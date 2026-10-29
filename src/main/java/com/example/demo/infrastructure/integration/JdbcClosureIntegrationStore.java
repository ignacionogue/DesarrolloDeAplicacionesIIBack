package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class JdbcClosureIntegrationStore implements ClosureIntegrationStore {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final JsonSql sqlJson;
    public JdbcClosureIntegrationStore(JdbcTemplate jdbc,ObjectMapper json,JsonSql sqlJson) {this.jdbc=jdbc;this.json=json;this.sqlJson=sqlJson;}
    public void initialize(Long id,EventEnvelope event) {
        var d=event.data();
        jdbc.update("""
            UPDATE street_closure SET source_ref=?,correlation_id=?,request_event_id=?,requested_from_at=?,requested_to_at=?,
            requested_at=?,previous_closure_request_id=?,integration_status='PENDING',integration_request=CAST(? AS JSONB) WHERE id=?
            """,UUID.fromString(d.path("sourceRef").asString()),event.correlationId(),event.eventId(),
            OffsetDateTime.parse(d.path("requestedFrom").asString()),OffsetDateTime.parse(d.path("requestedTo").asString()),
            OffsetDateTime.parse(d.path("requestedAt").asString()),d.hasNonNull("previousClosureRequestId")?d.path("previousClosureRequestId").asString():null,
            sqlJson.parameter(d.toString()),id);
    }
    private JsonNode read(Object raw) {return raw==null?null:raw instanceof byte[] b?json.readTree(b):json.readTree(raw.toString());}
    private RowMapper<Closure> mapper() { return (rs,n)->new Closure(rs.getLong("id"),rs.getLong("work_order_id"),UUID.fromString(rs.getString("closure_request_id")),
            rs.getObject("correlation_id",UUID.class),rs.getObject("request_event_id",UUID.class),rs.getString("integration_status"),
            read(rs.getObject("integration_request")),read(rs.getObject("authorization_data")),read(rs.getObject("completion")),
            rs.getObject("authorized_start",OffsetDateTime.class),rs.getObject("authorized_end",OffsetDateTime.class),rs.getObject("last_event_at",OffsetDateTime.class)); }
    public Optional<Closure> lock(UUID id) {return jdbc.query("SELECT * FROM street_closure WHERE closure_request_id=? AND integration_status IS NOT NULL FOR UPDATE",mapper(),id.toString()).stream().findFirst();}
    public List<Closure> forOrder(Long id) {return jdbc.query("SELECT * FROM street_closure WHERE work_order_id=? AND integration_status IS NOT NULL ORDER BY id FOR UPDATE",mapper(),id);}
    public void apply(Closure c,EventEnvelope event) {
        var d=event.data();
        switch(event.eventType()) {
            case "streetClosureApproved" -> jdbc.update("""
                UPDATE street_closure SET integration_status='APPROVED',authorization_data=CAST(? AS JSONB),street_closure_id=?,
                authorized_start=?,authorized_end=?,last_event_at=? WHERE id=?
                """,sqlJson.parameter(d.toString()),UUID.fromString(d.path("streetClosureId").asString()),
                OffsetDateTime.parse(d.path("startDate").asString()),OffsetDateTime.parse(d.path("endDate").asString()),event.occurredAt(),c.id());
            case "streetClosureRejected" -> jdbc.update("UPDATE street_closure SET integration_status='REJECTED',authorization_data=CAST(? AS JSONB),last_event_at=? WHERE id=?",
                    sqlJson.parameter(d.toString()),event.occurredAt(),c.id());
            case "streetClosureEnded" -> jdbc.update("UPDATE street_closure SET integration_status='ENDED',completion=CAST(? AS JSONB),street_closure_id=?,last_event_at=? WHERE id=?",
                    sqlJson.parameter(d.toString()),UUID.fromString(d.path("streetClosureId").asString()),event.occurredAt(),c.id());
            default -> throw new InvalidEventException();
        }
    }
    public void refreshWindows(OffsetDateTime now) {
        jdbc.update("UPDATE street_closure SET integration_status='EXPIRED' WHERE integration_status IN ('APPROVED','ACTIVE') AND authorized_end<=?",now);
        jdbc.update("UPDATE street_closure SET integration_status='ACTIVE' WHERE integration_status='APPROVED' AND authorized_start<=? AND authorized_end>?",now,now);
    }
}
