package com.example.demo;

import com.example.demo.application.events.*;
import com.example.demo.infrastructure.integration.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TicketUpdatedHandlerTests {
    @Autowired EventDispatcher dispatcher;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    ObjectNode fixture(String name,UUID ticket,String time) throws Exception {
        var node=(ObjectNode)json.readTree(Files.readString(Path.of("src/test/resources/contracts/m2-eventos-v1.78/examples/valid/"+name+".json")));
        node.put("eventId",UUID.randomUUID().toString());
        var data=(ObjectNode)node.path("data"); data.put("ticketId",ticket.toString()); data.put("updatedAt",time);
        return node;
    }
    String dispatch(ObjectNode node) { return dispatcher.dispatch(node.toString().getBytes(StandardCharsets.UTF_8)); }
    String state(UUID ticket) {
        Object value=jdbc.queryForObject("SELECT snapshot FROM ticket_projection WHERE ticket_id=?",Object.class,ticket);
        var node=value instanceof byte[] b?json.readTree(b):json.readTree(value.toString());
        return node.path("currentStatus").asString();
    }
    @Test void ignoresOtherAreasTicketCreatedAndReclassificationWithoutStoringTicket() throws Exception {
        UUID ticket=UUID.randomUUID();
        var other=fixture("ticketUpdated_ROUTED",ticket,"2026-10-20T10:00:00Z");
        ((ObjectNode)other.path("data")).put("responsibleAreaId","M2");
        assertEquals("ignored",dispatch(other));
        for (String name:java.util.List.of("ticketUpdated_INFORMATION_PROVIDED_reclasificacion","ticketUpdated_INFORMATION_REQUIRED_reclasificacion"))
            assertEquals("ignored",dispatch(fixture(name,ticket,"2026-10-20T11:00:00Z")));
        var created=(ObjectNode)json.readTree(Files.readString(Path.of("src/test/resources/contracts/m2-eventos-v1.78/examples/valid/ticketCreated.json")));
        assertEquals("ignored",dispatch(created));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ticket_projection WHERE ticket_id=?",Integer.class,ticket));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM event_inbox WHERE event_id=?",Integer.class,UUID.fromString(other.path("eventId").asString())));
    }
    @Test void duplicateAndReassignmentKeepSameProjection() throws Exception {
        UUID ticket=UUID.randomUUID();
        var routed=fixture("ticketUpdated_ROUTED",ticket,"2026-10-20T10:00:00Z");
        assertEquals("processed",dispatch(routed)); assertEquals("duplicated",dispatch(routed));
        assertEquals("processed",dispatch(fixture("ticketUpdated_ROUTED_reclasificacion",ticket,"2026-10-20T11:00:00Z")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ticket_projection WHERE ticket_id=?",Integer.class,ticket));
        Object raw=jdbc.queryForObject("SELECT snapshot FROM ticket_projection WHERE ticket_id=?",Object.class,ticket);
        String text=raw instanceof byte[] b?new String(b,StandardCharsets.UTF_8):raw.toString();
        assertFalse(text.contains("citizenId"));
    }
    @Test void cancelledThenLateRoutingDoesNotReopenButHydratesSnapshot() throws Exception {
        UUID ticket=UUID.randomUUID();
        dispatch(fixture("ticketUpdated_CANCELLED",ticket,"2026-10-20T12:00:00Z"));
        dispatch(fixture("ticketUpdated_ROUTED",ticket,"2026-10-20T10:00:00Z"));
        assertEquals("CANCELLED",state(ticket));
        assertNotNull(jdbc.queryForObject("SELECT routing FROM ticket_projection WHERE ticket_id=?",Object.class,ticket));
        assertFalse(jdbc.queryForObject("SELECT evaluation_required FROM ticket_projection WHERE ticket_id=?",Boolean.class,ticket));
    }
    @Test void informationReopenedPriorityAndEscalationPreserveOneCase() throws Exception {
        UUID ticket=UUID.randomUUID();
        dispatch(fixture("ticketUpdated_ROUTED",ticket,"2026-10-20T10:00:00Z"));
        dispatch(fixture("ticketUpdated_INFORMATION_PROVIDED",ticket,"2026-10-20T11:00:00Z"));
        assertNotNull(jdbc.queryForObject("SELECT information FROM ticket_projection WHERE ticket_id=?",Object.class,ticket));
        dispatch(fixture("ticketUpdated_REOPENED",ticket,"2026-10-20T12:00:00Z"));
        assertTrue(jdbc.queryForObject("SELECT evaluation_required FROM ticket_projection WHERE ticket_id=?",Boolean.class,ticket));
        dispatch(fixture("ticketUpdated_PRIORITY_CHANGED",ticket,"2026-10-20T13:00:00Z"));
        assertTrue(jdbc.queryForObject("SELECT planning_review_required FROM ticket_projection WHERE ticket_id=?",Boolean.class,ticket));
        dispatch(fixture("ticketUpdated_ESCALATION_CHANGED",ticket,"2026-10-20T14:00:00Z"));
        assertNotNull(jdbc.queryForObject("SELECT escalation FROM ticket_projection WHERE ticket_id=?",Object.class,ticket));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ticket_projection WHERE ticket_id=?",Integer.class,ticket));
    }
}
