package com.example.demo;

import com.example.demo.application.events.*;
import com.example.demo.dto.request.*;
import com.example.demo.model.AppUser;
import com.example.demo.repository.AppUserRepository;
import com.example.demo.service.OrdenTrabajoService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TicketOutgoingTests {
    @Autowired TicketStatusPublisher publisher;
    @Autowired EventDispatcher dispatcher;
    @Autowired OrdenTrabajoService orders;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AppUserRepository users;

    @AfterEach void clearActor() { SecurityContextHolder.clearContext(); }
    void receive(String fixture, UUID ticket, String time) throws Exception {
        var event=(ObjectNode)json.readTree(Files.readString(Path.of("src/test/resources/contracts/m2-eventos-v1.78/examples/valid/"+fixture+".json")));
        event.put("eventId",UUID.randomUUID().toString());
        ((ObjectNode)event.path("data")).put("ticketId",ticket.toString()).put("updatedAt",time);
        dispatcher.dispatch(event.toString().getBytes(StandardCharsets.UTF_8));
    }
    OrdenTrabajoRequest request(UUID ticket) {
        var r=new OrdenTrabajoRequest(); r.setOrigin("ATENCION_CIUDADANA"); r.setSourceRequestId(ticket.toString());
        r.setDescription("Intervencion evaluada por personal de Obras"); r.setPriority("ALTA"); return r;
    }
    List<JsonNode> events(UUID ticket) {
        return jdbc.query("SELECT envelope FROM event_outbox WHERE ordering_key=? ORDER BY sequence_number",(rs,n)-> {
            Object raw=rs.getObject(1); return raw instanceof byte[] bytes?json.readTree(bytes):json.readTree(raw.toString());
        },"ticket:"+ticket);
    }
    @Test void linksOneOrderAndPublishesStartedBeforeProgressWithPersistentActor() throws Exception {
        UUID ticket=UUID.randomUUID(); receive("ticketUpdated_ROUTED",ticket,"2026-10-21T10:00:00Z");
        var user=users.save(new AppUser("event."+UUID.randomUUID(),"not-a-login-hash","PERSONAL_OBRAS"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(),null,List.of()));
        Long id=orders.crear(request(ticket)).id();
        assertThrows(com.example.demo.exception.InvalidStateTransitionException.class,()->orders.crear(request(ticket)));
        var schedule=new ScheduleOTRequest(); schedule.setScheduledDate(java.time.LocalDate.of(2026,10,25));
        orders.programar(id,schedule); orders.iniciar(id); orders.completar(id,new CompleteOTRequest());
        var events=events(ticket);
        assertEquals(List.of("STARTED","PROGRESS","PROGRESS","RESOLVED"),events.stream().map(e->e.path("data").path("updateType").asString()).toList());
        for(var event:events) {
            assertEquals(user.getIntegrationId().toString(),event.path("data").path("updatedBy").path("id").asString());
            assertFalse(event.has("causationId"));
        }
        assertEquals("ROUTED",jdbc.queryForObject("SELECT snapshot FROM ticket_projection WHERE ticket_id=?",(rs,n)-> {
            Object raw=rs.getObject(1); return (raw instanceof byte[] b?json.readTree(b):json.readTree(raw.toString())).path("currentStatus").asString();
        },ticket)); // Only M2 may change its authoritative status.
    }
    @Test void cancellationBlocksStartingButDoesNotCancelAnOngoingIntervention() throws Exception {
        UUID ticket=UUID.randomUUID(); receive("ticketUpdated_ROUTED",ticket,"2026-10-21T10:00:00Z");
        Long id=orders.crear(request(ticket)).id();
        var schedule=new ScheduleOTRequest(); schedule.setScheduledDate(java.time.LocalDate.of(2026,10,25));
        orders.programar(id,schedule); orders.iniciar(id);
        receive("ticketUpdated_CANCELLED",ticket,"2026-10-21T12:00:00Z");
        assertEquals("EN_EJECUCION",orders.obtenerPorId(id).status());
        orders.pausar(id);
        assertThrows(com.example.demo.exception.InvalidStateTransitionException.class,()->orders.iniciar(id));
        int before=events(ticket).size();
        assertEquals("COMPLETADA",orders.completar(id,new CompleteOTRequest()).status());
        assertEquals(before,events(ticket).size());
    }
    @Test void validatesAllSixVariantsAndRejectsMissingReasonsAndImpersonation() throws Exception {
        var tx=new TransactionTemplate(transactions); UUID ticket=UUID.randomUUID();
        var samples=Map.of("STARTED","STARTED_sistema","PROGRESS","PROGRESS_avance","INFORMATION_REQUIRED","INFORMATION_REQUIRED",
                "RETURNED","RETURNED","RESOLVED","RESOLVED","REJECTED","REJECTED");
        for(var entry:samples.entrySet()) {
            var data=(ObjectNode)json.readTree(Files.readString(Path.of("src/test/resources/contracts/m2-eventos-v1.78/examples/valid/updateTicketStatus_"+entry.getValue()+".json"))).path("data");
            data.remove(List.of("ticketId","updateType","updatedBy","updateOccurredAt"));
            tx.executeWithoutResult(status -> { publisher.publish(ticket,entry.getKey(),data,null,null); status.setRollbackOnly(); });
        }
        for(String type:List.of("RETURNED","REJECTED","RESOLVED","INFORMATION_REQUIRED","PROGRESS"))
            assertThrows(InvalidEventException.class,()->tx.executeWithoutResult(status->publisher.publish(ticket,type,json.createObjectNode(),null,null)));
        assertThrows(InvalidEventException.class,()->tx.executeWithoutResult(status->publisher.publish(ticket,"STARTED",json.createObjectNode().put("updatedBy","USR-M3-214"),null,null)));
        assertTrue(events(ticket).isEmpty());
    }

    @Test void concurrentStartsHaveOnlyOneBusinessEffect() throws Exception {
        UUID ticket=UUID.randomUUID(); receive("ticketUpdated_ROUTED",ticket,"2026-10-21T10:00:00Z");
        Long id=orders.crear(request(ticket)).id();
        var schedule=new ScheduleOTRequest(); schedule.setScheduledDate(java.time.LocalDate.of(2026,10,25));
        orders.programar(id,schedule);
        var gate=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> start=()-> {
                gate.await();
                try { orders.iniciar(id); return true; }
                catch(com.example.demo.exception.InvalidStateTransitionException expected) { return false; }
            };
            var first=pool.submit(start); var second=pool.submit(start); gate.countDown();
            assertNotEquals(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(3,events(ticket).size()); // Scheduled STARTED/PROGRESS plus one START PROGRESS.
        } finally { pool.shutdownNow(); }
    }
}
