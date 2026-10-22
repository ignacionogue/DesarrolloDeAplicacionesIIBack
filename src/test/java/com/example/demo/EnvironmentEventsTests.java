package com.example.demo;

import com.example.demo.application.events.*;
import com.example.demo.dto.request.*;
import com.example.demo.service.OrdenTrabajoService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="app.messaging.m6-uuid-contract-confirmed=true")
class EnvironmentEventsTests {
    @Autowired EventDispatcher dispatcher;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrdenTrabajoService orders;
    @Autowired com.example.demo.infrastructure.integration.M3SchemaValidator schemas;
    private final List<UUID> sources=new ArrayList<>();
    @AfterEach void cleanupOutbox() { for(UUID id:sources) jdbc.update("DELETE FROM event_outbox WHERE ordering_key=?","environment:"+id); }
    ObjectNode event(String type) {
        UUID id=UUID.randomUUID(); sources.add(id);
        var e=json.createObjectNode(); e.put("eventId",UUID.randomUUID().toString()).put("eventType",type).put("eventVersion","1.0")
            .put("occurredAt","2026-10-21T10:00:00Z").put("sourceModule","ambiente").put("correlationId",UUID.randomUUID().toString());
        var d=e.putObject("data"); d.putObject("location").put("street","Calle de prueba 100");
        if(type.equals("infrastructureRepairRequested")) {
            d.put("requestId",id.toString()).put("damageType","BLOCKED_DRAIN").put("severity","HIGH")
                .put("detectedIn",UUID.randomUUID().toString()).put("publicSafetyRisk",true).put("requestedAt","2026-10-21T10:00:00Z");
        } else if(type.equals("containerDamaged")) {
            d.put("containerId",id.toString()).put("damageType","STRUCTURAL").put("severity","HIGH")
                .put("requiresPublicWorks",true).put("detectedAt","2026-10-21T10:00:00Z");
        } else {
            d.put("treeId",id.toString()).put("riskLevel","HIGH").put("requiresPublicWorks",true).put("surveyedAt","2026-10-21T10:00:00Z");
        }
        return e;
    }
    String dispatch(ObjectNode e) { return dispatcher.dispatch(e.toString().getBytes(StandardCharsets.UTF_8)); }
    OrdenTrabajoRequest request(UUID source) {
        var r=new OrdenTrabajoRequest(); r.setOrigin("AMBIENTE"); r.setSourceRequestId(source.toString());
        r.setDescription("Trabajo evaluado"); r.setPriority("ALTA"); r.setEstimatedDurationHours(4); return r;
    }
    void finish(Long id) {
        var schedule=new ScheduleOTRequest(); schedule.setScheduledDate(java.time.LocalDate.of(2026,10,25));
        orders.programar(id,schedule); orders.iniciar(id); orders.completar(id,new CompleteOTRequest());
    }
    @Test void requestKeepsSourceIdAndPublishesOnlyScheduledAndCompleted() {
        var event=event("infrastructureRepairRequested"); UUID source=sources.get(0);
        assertEquals("processed",dispatch(event)); assertEquals("duplicated",dispatch(event));
        var order=orders.crear(request(source)); finish(order.id());
        assertEquals(source.toString(),order.sourceRequestId());
        assertThrows(com.example.demo.exception.InvalidStateTransitionException.class,()->orders.crear(request(source)));
        var envelopes=jdbc.query("SELECT envelope FROM event_outbox WHERE ordering_key=? ORDER BY sequence_number",(rs,n)-> {
            Object raw=rs.getObject(1); return raw instanceof byte[] b?json.readTree(b):json.readTree(raw.toString());
        },"environment:"+source);
        assertEquals(List.of("workOrderScheduled","workOrderCompleted"),envelopes.stream().map(e->e.path("eventType").asString()).toList());
        for(var e:envelopes) {
            assertEquals(source.toString(),e.path("data").path("sourceRequestId").asString());
            assertNotNull(UUID.fromString(e.path("data").path("workOrderId").asString()));
            assertEquals(event.path("correlationId"),e.path("correlationId")); assertFalse(e.has("causationId"));
        }
    }
    @Test void ignoresUnneededContainerAndMonitorsTreeWithoutSuggestedWork() {
        var container=event("containerDamaged"); ((ObjectNode)container.path("data")).put("requiresPublicWorks",false);
        assertEquals("ignored",dispatch(container));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM environment_case WHERE source_id=?",Integer.class,sources.get(0)));
        var tree=event("treeRiskDetected"); assertEquals("processed",dispatch(tree));
        assertFalse(jdbc.queryForObject("SELECT actionable FROM environment_case WHERE source_id=?",Boolean.class,sources.get(1)));
        assertThrows(com.example.demo.exception.InvalidRequestException.class,()->orders.crear(request(sources.get(1))));
    }
    @Test void containerAndTreeNeverReplyToM6AndOlderTreeDoesNotEraseIntervention() {
        var container=event("containerDamaged"); dispatch(container); finish(orders.crear(request(sources.get(0))).id());
        var tree=event("treeRiskDetected"); ((ObjectNode)tree.path("data")).put("suggestedIntervention","SAFETY_PRUNING"); dispatch(tree);
        tree.put("eventId",UUID.randomUUID().toString()); ((ObjectNode)tree.path("data")).remove("suggestedIntervention");
        ((ObjectNode)tree.path("data")).put("surveyedAt","2026-10-20T10:00:00Z"); dispatch(tree);
        finish(orders.crear(request(sources.get(1))).id());
        for(UUID source:sources) assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE ordering_key=?",Integer.class,"environment:"+source));
    }
    @Test void missingRequiredRepairDataDoesNotCreateCase() {
        for(String field:List.of("requestId","damageType","severity","location","detectedIn","publicSafetyRisk","requestedAt")) {
            var e=event("infrastructureRepairRequested"); ((ObjectNode)e.path("data")).remove(field);
            assertThrows(InvalidEventException.class,()->dispatch(e));
        }
    }
    @Test void authoritativeOutgoingSchemasRejectExtraFieldsAndInvalidAttachmentShape() {
        var data=json.createObjectNode().put("sourceRequestId",UUID.randomUUID().toString()).put("workOrderId",UUID.randomUUID().toString());
        schemas.validate(EventEnvelope.outgoing("workOrderCompleted",data,null,null));
        data.put("evidence","not-part-of-contract");
        assertThrows(InvalidEventException.class,()->schemas.validate(EventEnvelope.outgoing("workOrderCompleted",data,null,null)));
        data.remove("evidence"); data.putArray("attachments").addObject().put("attachmentId","obsolete");
        assertThrows(InvalidEventException.class,()->schemas.validate(EventEnvelope.outgoing("workOrderCompleted",data,null,null)));
        data.remove("attachments"); data.put("workOrderId","OT-2026-0098");
        assertThrows(InvalidEventException.class,()->schemas.validate(EventEnvelope.outgoing("workOrderScheduled",data,null,null)));
    }
}
