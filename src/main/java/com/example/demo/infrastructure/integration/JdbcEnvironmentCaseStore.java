package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class JdbcEnvironmentCaseStore implements EnvironmentCaseStore {
    private final JdbcTemplate jdbc;
    private final JsonSql json;
    public JdbcEnvironmentCaseStore(JdbcTemplate jdbc, JsonSql json) { this.jdbc=jdbc; this.json=json; }
    @Override public void receive(EventEnvelope event, String idField, String timestampField, boolean actionable) {
        var data=event.data(); UUID id=UUID.fromString(data.path(idField).asString());
        if(json.postgres()) jdbc.queryForList("SELECT pg_advisory_xact_lock(31006, ?)",id.hashCode());
        var existing=lock(id);
        var updated=OffsetDateTime.parse(data.path(timestampField).asString());
        if(existing.isEmpty()) {
            jdbc.update("INSERT INTO environment_case(source_id,event_type,payload,updated_at,correlation_id,actionable) VALUES (?,?,CAST(? AS JSONB),?,?,?)",
                    id,event.eventType(),json.parameter(data.toString()),updated,event.correlationId()==null?UUID.randomUUID():event.correlationId(),actionable);
        } else {
            if(!existing.get().eventType().equals(event.eventType())) throw new RejectedEventException();
            jdbc.update("UPDATE environment_case SET payload=CAST(? AS JSONB),updated_at=?,actionable=? WHERE source_id=? AND updated_at<?",
                    json.parameter(data.toString()),updated,actionable,id,updated);
        }
    }
    @Override public Optional<Case> lock(UUID id) {
        return jdbc.query("SELECT * FROM environment_case WHERE source_id=? FOR UPDATE",(rs,n)->new Case(id,rs.getString("event_type"),
                rs.getObject("correlation_id",UUID.class),rs.getObject("order_id",Long.class),rs.getBoolean("actionable")),id).stream().findFirst();
    }
    @Override public void link(UUID sourceId, Long orderId) {
        if(jdbc.update("UPDATE environment_case SET order_id=? WHERE source_id=? AND (order_id IS NULL OR order_id=?)",orderId,sourceId,orderId)!=1)
            throw new IllegalStateException("ENVIRONMENT_ORDER_CONFLICT");
    }
}
