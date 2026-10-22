package com.example.demo;

import com.example.demo.application.events.*;
import com.example.demo.infrastructure.integration.*;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.core.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class EventIntegrationFoundationTests {
    @Autowired EventCodec codec;
    @Autowired M2SchemaValidator schemas;
    @Autowired ObjectMapper json;
    @Autowired JdbcEventInbox inbox;
    @Autowired JdbcEventOutbox outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test void messagingDisabledDoesNotCreateBrokerConnectionsOrDeclareTopology() {
        assertTrue(context.getBeansOfType(org.springframework.amqp.rabbit.connection.ConnectionFactory.class).isEmpty());
        assertTrue(context.getBeansOfType(org.springframework.amqp.rabbit.core.RabbitAdmin.class).isEmpty());
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->outbox.append(event("mustBeTransactional"),"test"));
    }

    EventEnvelope event(String type) { return EventEnvelope.outgoing(type,json.createObjectNode(),null,null); }
    EventHandler handler(String type, Runnable action) {
        return new EventHandler() {
            public String eventType() { return type; }
            public String sourceModule() { return "obras"; }
            public boolean relevant(EventEnvelope event) { return true; }
            public void validate(EventEnvelope event) { }
            public void handle(EventEnvelope event) { action.run(); }
        };
    }
    @Test void outgoingEnvelopeHasExactFieldsAndOmitsAbsentCausation() {
        var event=event("testEvent"); var tree=json.readTree(codec.encode(event));
        assertEquals(Set.of("eventId","eventType","eventVersion","occurredAt","sourceModule","correlationId","data"),new HashSet<>(tree.propertyNames()));
        assertEquals(4,event.eventId().version()); assertNotNull(event.correlationId());
        assertEquals(event,codec.decode(codec.encode(event).getBytes(StandardCharsets.UTF_8)));
        tree.withObject("data").put("sensitive","secret");
        assertFalse(event.toString().contains("secret"));
    }
    @Test void rejectsUnknownFieldsWrongVersionUuidAndMissingOffsetWithoutLeakingInput() {
        for (String field : List.of("traceId","receivedAt","occuredAt")) {
            var tree=(tools.jackson.databind.node.ObjectNode)json.readTree(codec.encode(event("testEvent")));
            tree.put(field,"sensitive");
            var failure=assertThrows(InvalidEventException.class,()->codec.decode(tree.toString().getBytes(StandardCharsets.UTF_8)));
            assertEquals("INVALID_EVENT",failure.getMessage()); assertNull(failure.getCause());
        }
        for (var change : Map.of("occurredAt","2026-10-21T12:00:00","eventVersion","2.0","eventId","1-1-1-1-1").entrySet()) {
            var tree=(tools.jackson.databind.node.ObjectNode)json.readTree(codec.encode(event("testEvent")));
            tree.put(change.getKey(),change.getValue());
            assertThrows(InvalidEventException.class,()->codec.decode(tree.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }
    @Test void actualM2FixturesAndActorUuidAreValidated() throws Exception {
        var root=java.nio.file.Path.of("src/test/resources/contracts/m2-eventos-v1.78/examples");
        try(var paths=java.nio.file.Files.walk(root)) {
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList())
                schemas.validate(codec.decode(java.nio.file.Files.readAllBytes(file)));
        }
        var fixture=root.resolve("valid/updateTicketStatus_STARTED.json");
        var tree=(tools.jackson.databind.node.ObjectNode)json.readTree(java.nio.file.Files.readString(fixture));
        ((tools.jackson.databind.node.ObjectNode)tree.path("data").path("updatedBy")).put("id","USR-M3-214");
        assertThrows(InvalidEventException.class,()->schemas.validate(codec.decode(tree.toString().getBytes(StandardCharsets.UTF_8))));
    }
    @Test void registryRejectsDuplicateHandlersAndProducerSpoofing() {
        var handler=handler("caseEvent",()->{});
        assertThrows(IllegalStateException.class,()->new EventHandlerRegistry(List.of(handler,handler)));
        var event=event("caseEvent");
        assertThrows(InvalidEventException.class,()->new EventHandlerRegistry(List.of(handler)).find(new EventEnvelope(
                event.eventId(),event.eventType(),"1.0",event.occurredAt(),"transito",event.correlationId(),null,event.data())));
    }
    @Test void inboxSurvivesInterruptedReceiptAndAppliesDuplicateOnceEvenConcurrently() throws Exception {
        var event=event("concurrentEvent"); var counter=new java.util.concurrent.atomic.AtomicInteger();
        var registry=new EventHandlerRegistry(List.of(handler(event.eventType(),counter::incrementAndGet)));
        inbox.receive(event); // Simulates process termination before handler.
        assertTrue(inbox.pending().contains(event.eventId()));
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{ inbox.receive(event); return inbox.process(event.eventId(),registry); });
            var second=pool.submit(()->{ inbox.receive(event); return inbox.process(event.eventId(),registry); });
            assertEquals(Set.of("processed","duplicated"),Set.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)));
            assertEquals(1,counter.get());
        } finally { pool.shutdownNow(); }
    }
    @Test void technicalFailureRollsBackBusinessAndRetriesPersistedEnvelope() {
        var event=event("technicalEvent"); inbox.receive(event);
        String name="rollback-"+event.eventId();
        var failing=new EventHandlerRegistry(List.of(handler(event.eventType(),()->{
            jdbc.update("INSERT INTO material(nombre) VALUES (?)",name); throw new IllegalStateException("private");
        })));
        assertThrows(IllegalStateException.class,()->inbox.process(event.eventId(),failing));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM material WHERE nombre=?",Integer.class,name));
        assertEquals("TECHNICAL_ERROR",jdbc.queryForObject("SELECT error FROM event_inbox WHERE event_id=?",String.class,event.eventId()));
        assertEquals("processed",inbox.process(event.eventId(),new EventHandlerRegistry(List.of(handler(event.eventType(),()->{})))));
    }
    @Test void rejectedBusinessRollsBackEffectsButIsTerminal() {
        var event=event("rejectedEvent"); inbox.receive(event);
        String name="rejected-"+event.eventId();
        var registry=new EventHandlerRegistry(List.of(handler(event.eventType(),()->{
            jdbc.update("INSERT INTO material(nombre) VALUES (?)",name); throw new RejectedEventException();
        })));
        assertEquals("rejected",inbox.process(event.eventId(),registry));
        assertEquals("duplicated",inbox.process(event.eventId(),registry));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM material WHERE nombre=?",Integer.class,name));
    }
    @Test void outboxSharesBusinessRollbackAndRetainsEventIdOnRetry() {
        var rolledBack=event("rollbackOutbox"); var tx=new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{ outbox.append(rolledBack,"rollback"); throw new IllegalStateException(); }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE event_id=?",Integer.class,rolledBack.eventId()));
        var first=event("firstEvent"); var second=event("secondEvent"); String key=UUID.randomUUID().toString();
        tx.executeWithoutResult(status->{outbox.append(first,key);outbox.append(second,key);});
        var sent=new ArrayList<UUID>();
        assertTrue(outbox.publishOne(e->{sent.add(e.eventId());throw new IllegalStateException();}));
        assertFalse(outbox.publishOne(e->fail("Later event must wait for its predecessor")));
        jdbc.update("UPDATE event_outbox SET next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=?",first.eventId());
        assertTrue(outbox.publishOne(e->sent.add(e.eventId())));
        assertTrue(outbox.publishOne(e->sent.add(e.eventId())));
        assertEquals(List.of(first.eventId(),first.eventId(),second.eventId()),sent);
    }
    @Test void validIrrelevantEventNeverPersistsTicketAndListenerNeverRequeues() throws Exception {
        var event=event("irrelevantEvent");
        var ignore=mock(EventHandler.class); when(ignore.eventType()).thenReturn(event.eventType()); when(ignore.sourceModule()).thenReturn("obras");
        var telemetry=mock(EventTelemetry.class);
        var dispatcher=new EventDispatcher(new EventHandlerRegistry(List.of(ignore)),inbox,codec,telemetry,schemas);
        assertEquals("ignored",dispatcher.dispatch(codec.encode(event).getBytes(StandardCharsets.UTF_8)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM event_inbox WHERE event_id=?",Integer.class,event.eventId()));
        var failing=mock(EventDispatcher.class); when(failing.dispatch(any())).thenThrow(new IllegalStateException());
        var channel=mock(Channel.class); var props=new MessageProperties(); props.setDeliveryTag(42);
        new RabbitEventListener(failing,telemetry).onMessage(new Message(new byte[0],props),channel);
        verify(channel).basicNack(42,false,false); verify(channel,never()).basicAck(anyLong(),anyBoolean());
    }
}
