package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class JdbcTicketProjectionStore implements TicketProjectionStore {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    private final JsonSql jsonSql;
    public JdbcTicketProjectionStore(JdbcTemplate jdbc,ObjectMapper json,JsonSql jsonSql) {this.jdbc=jdbc;this.json=json;this.jsonSql=jsonSql;}
    @Override public Projection lockOrCreate(EventEnvelope event) {
        UUID id=UUID.fromString(event.data().path("ticketId").asString());
        // PostgreSQL advisory transaction lock also serializes concurrent first deliveries,
        // before a projection row exists. Hash collisions only serialize unrelated tickets.
        if (postgres()) jdbc.queryForList("SELECT pg_advisory_xact_lock(?)",id.getMostSignificantBits()^id.getLeastSignificantBits());
        var existing=jdbc.queryForList("SELECT * FROM ticket_projection WHERE ticket_id=? FOR UPDATE",id);
        if(existing.isEmpty()) {
            jdbc.update("INSERT INTO ticket_projection(ticket_id,snapshot,updated_at,correlation_id) VALUES (?,CAST(? AS JSONB),?,?)",
                    id,jsonSql.parameter(event.data().toString()),OffsetDateTime.parse(event.data().path("updatedAt").asString()),
                    event.correlationId()==null?UUID.randomUUID():event.correlationId());
        }
        return lock(id).orElseThrow();
    }
    @Override public java.util.Optional<Projection> lock(UUID id) {
        return jdbc.query("SELECT * FROM ticket_projection WHERE ticket_id=? FOR UPDATE",(rs,n)->new Projection(
                id,read(rs.getObject("snapshot")),rs.getObject("updated_at",OffsetDateTime.class),read(rs.getObject("routing")),
                rs.getObject("routing_at",OffsetDateTime.class),read(rs.getObject("information")),rs.getObject("information_at",OffsetDateTime.class),
                read(rs.getObject("escalation")),rs.getObject("escalation_at",OffsetDateTime.class),rs.getObject("correlation_id",UUID.class),
                rs.getObject("order_id",Long.class),rs.getBoolean("started"),rs.getBoolean("planning_review_required"),rs.getBoolean("evaluation_required")),id).stream().findFirst();
    }
    @Override public void save(Projection p) {
        jdbc.update("""
            UPDATE ticket_projection SET snapshot=CAST(? AS JSONB),updated_at=?,routing=CAST(? AS JSONB),routing_at=?,
              information=CAST(? AS JSONB),information_at=?,escalation=CAST(? AS JSONB),escalation_at=?,
              order_id=?,started=?,planning_review_required=?,evaluation_required=? WHERE ticket_id=?
            """,str(p.snapshot()),p.updatedAt(),str(p.routing()),p.routingAt(),str(p.information()),p.informationAt(),
                str(p.escalation()),p.escalationAt(),p.orderId(),p.started(),p.planningReviewRequired(),p.evaluationRequired(),p.ticketId());
    }
    private boolean postgres() { return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) c->c.getMetaData().getDatabaseProductName().equals("PostgreSQL"))); }
    private Object str(JsonNode node) { return jsonSql.parameter(node==null?null:node.toString()); }
    private JsonNode read(Object raw) {
        if(raw==null) return null;
        return raw instanceof byte[] b?json.readTree(b):json.readTree(raw.toString());
    }
}
