package com.example.demo;

import com.example.demo.application.events.*;
import com.example.demo.dto.request.*;
import com.example.demo.service.OrdenTrabajoService;
import com.example.demo.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class TrafficEventsTests {
    @Autowired ClosureIntegrationService closures;
    @Autowired OrdenTrabajoService orders;
    @Autowired EventDispatcher dispatcher;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    final List<UUID> correlations=new ArrayList<>();
    @AfterEach void cleanupOutbox() {for(UUID id:correlations) jdbc.update("DELETE FROM event_outbox WHERE ordering_key=?","closure:"+id);}
    Long order() {
        var request=new OrdenTrabajoRequest(); request.setOrigin("MANUAL"); request.setDescription("Prueba de corte"); request.setPriority("MEDIA");
        Long id=orders.crear(request).id(); var schedule=new ScheduleOTRequest(); schedule.setScheduledDate(LocalDate.now()); orders.programar(id,schedule); return id;
    }
    ClosureIntegrationStore.Closure request(Long order, UUID previous) {
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        var result=closures.request(new IntegratedClosureRequest(order,"Reparacion",
            List.of(new IntegratedClosureRequest.Section("Calle A","Calle B","Calle C",IntegratedClosureRequest.Direction.BOTH)),
            now.minusHours(1),now.plusHours(6),"TOTAL",previous));
        correlations.add(result.correlationId()); return result;
    }
    ObjectNode event(String type,ClosureIntegrationStore.Closure closure,OffsetDateTime from,OffsetDateTime to) {
        var event=json.createObjectNode(); event.put("eventId",UUID.randomUUID().toString()).put("eventType",type).put("eventVersion","1.0")
            .put("occurredAt",OffsetDateTime.now(ZoneOffset.UTC).toString()).put("sourceModule","transito").put("correlationId",closure.correlationId().toString());
        if(type.equals("streetClosureApproved") || type.equals("streetClosureRejected"))
            event.put("causationId",closure.requestEventId().toString());
        var data=event.putObject("data"); data.put("closureRequestId",closure.requestId().toString());
        if(type.equals("streetClosureApproved")) {
            data.put("streetClosureId",UUID.randomUUID().toString()).put("requestingModule","obras")
                .put("startDate",from.toString()).put("endDate",to.toString());
            data.set("affectedSections",closure.request().path("affectedSections"));
            data.putArray("conditions").add("Acceso a emergencias"); data.putArray("detours").addObject().put("street","Calle A").put("alternateRoute","Calle D");
        } else if(type.equals("streetClosureRejected")) data.put("requestingModule","obras").put("rejectionReason","Via prioritaria");
        else data.put("streetClosureId",UUID.randomUUID().toString()).put("completionDateTime",OffsetDateTime.now(ZoneOffset.UTC).toString()).put("notes","Circulacion restablecida");
        return event;
    }
    String dispatch(ObjectNode event) {return dispatcher.dispatch(event.toString().getBytes(StandardCharsets.UTF_8));}
    @Test void normalCycleStoresConditionsAndEndsWithoutCausation() {
        Long id=order(); var closure=request(id,null); var now=OffsetDateTime.now(ZoneOffset.UTC);
        assertThrows(InvalidStateTransitionException.class,()->orders.iniciar(id));
        var approved=event("streetClosureApproved",closure,now.minusMinutes(1),now.plusHours(1));
        assertEquals("processed",dispatch(approved)); assertEquals("duplicated",dispatch(approved));
        var current=closures.get(closure.requestId()); assertEquals("ACTIVE",current.state());
        assertEquals(1,current.authorization().path("conditions").size()); assertEquals(1,current.authorization().path("detours").size());
        assertEquals("EN_EJECUCION",orders.iniciar(id).status());
        var ended=event("streetClosureEnded",closure,null,null); ended.putNull("causationId");
        assertEquals("rejected",dispatch(ended));
        assertEquals("ACTIVE",closures.get(closure.requestId()).state());
        ended.put("eventId",UUID.randomUUID().toString());
        ((ObjectNode)ended.path("data")).set("streetClosureId",current.authorization().path("streetClosureId"));
        dispatch(ended);
        assertEquals("ENDED",closures.get(closure.requestId()).state());
        approved.put("eventId",UUID.randomUUID().toString()); dispatch(approved);
        assertEquals("ENDED",closures.get(closure.requestId()).state());
        assertNotNull(closures.get(closure.requestId()).completion().path("completionDateTime"));
    }
    @Test void authorizedEndOverridesRequestedEndAndFutureWindowDoesNotUnlock() {
        var now=OffsetDateTime.now(ZoneOffset.UTC); Long id=order(); var c=request(id,null);
        dispatch(event("streetClosureApproved",c,now.minusHours(2),now.minusHours(1)));
        closures.refreshWindows(); assertEquals("EXPIRED",closures.get(c.requestId()).state());
        assertThrows(InvalidStateTransitionException.class,()->orders.iniciar(id));
        Long future=order(); var f=request(future,null); dispatch(event("streetClosureApproved",f,now.plusHours(1),now.plusHours(2)));
        assertEquals("APPROVED",closures.get(f.requestId()).state());
        assertThrows(InvalidStateTransitionException.class,()->orders.iniciar(future));
    }
    @Test void rejectionBlocksAndExtensionUsesNewIdsPreservingThread() {
        var now=OffsetDateTime.now(ZoneOffset.UTC); Long rejectedOrder=order(); var rejected=request(rejectedOrder,null);
        dispatch(event("streetClosureRejected",rejected,null,null));
        assertEquals("REJECTED",closures.get(rejected.requestId()).state());
        assertThrows(InvalidStateTransitionException.class,()->orders.iniciar(rejectedOrder));
        Long id=order(); var original=request(id,null);
        dispatch(event("streetClosureApproved",original,now.minusHours(2),now.minusHours(1)));
        var extension=request(id,original.requestId());
        assertNotEquals(original.requestId(),extension.requestId()); assertNotEquals(original.requestEventId(),extension.requestEventId());
        assertEquals(original.correlationId(),extension.correlationId());
        assertThrows(InvalidStateTransitionException.class,()->orders.iniciar(id));
        dispatch(event("streetClosureApproved",extension,now.minusMinutes(1),now.plusHours(1)));
        assertEquals("EN_EJECUCION",orders.iniciar(id).status());
    }
    @Test void accidentMinimizesNationalIdentifiersBeforeInboxAndDoesNotLogThem(CapturedOutput output) {
        var e=json.createObjectNode(); UUID id=UUID.randomUUID();
        e.put("eventId",id.toString()).put("eventType","roadAccidentRegistered").put("eventVersion","1.0")
            .put("occurredAt",OffsetDateTime.now(ZoneOffset.UTC).toString()).put("sourceModule","transito")
            .put("correlationId",UUID.randomUUID().toString());
        var d=e.putObject("data"); d.put("accidentId",UUID.randomUUID().toString()).put("accidentDateTime",OffsetDateTime.now(ZoneOffset.UTC).toString())
            .put("involvesInfrastructureDamage",true).put("damageDescription","Semaforo danado").put("inspectorId",UUID.randomUUID().toString());
        d.putObject("location").put("street","Calle A"); d.putArray("vehicles").addObject().put("ownerNationalId","38765432");
        d.putArray("persons").addObject().put("nationalId","38765432");
        assertEquals("processed",dispatch(e));
        Object raw=jdbc.queryForObject("SELECT payload FROM event_inbox WHERE event_id=?",Object.class,id);
        String payload=raw instanceof byte[] b?new String(b,StandardCharsets.UTF_8):raw.toString();
        assertFalse(payload.contains("38765432"));assertFalse(payload.contains("inspectorId"));assertFalse(output.getAll().contains("38765432"));
        e.put("eventId",UUID.randomUUID().toString()); d.put("involvesInfrastructureDamage",false); d.remove("damageDescription"); assertEquals("ignored",dispatch(e));
        d.put("involvesInfrastructureDamage",true); assertThrows(InvalidEventException.class,()->dispatch(e));
    }
}
