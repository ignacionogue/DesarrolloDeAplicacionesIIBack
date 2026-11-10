package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class JdbcRoadAccidentStore implements RoadAccidentStore {
    private final JdbcTemplate jdbc; private final JsonSql json;
    public JdbcRoadAccidentStore(JdbcTemplate jdbc,JsonSql json) {this.jdbc=jdbc;this.json=json;}
    public void observe(EventEnvelope event) {
        var d=event.data(); String id=d.path("accidentId").asString();
        if(json.postgres()) jdbc.queryForList("SELECT pg_advisory_xact_lock(31007, ?)",id.hashCode());
        var rows=jdbc.queryForList("SELECT accident_reference FROM road_accident_projection WHERE accident_reference=? FOR UPDATE",id);
        var at=OffsetDateTime.parse(d.path("accidentDateTime").asString());
        if(rows.isEmpty()) jdbc.update("INSERT INTO road_accident_projection(accident_reference,payload,updated_at,correlation_id) VALUES (?,CAST(? AS JSONB),?,?)",
                id,json.parameter(d.toString()),at,event.correlationId()==null?UUID.randomUUID():event.correlationId());
        else jdbc.update("UPDATE road_accident_projection SET payload=CAST(? AS JSONB),updated_at=? WHERE accident_reference=? AND updated_at<?",json.parameter(d.toString()),at,id,at);
    }
}
