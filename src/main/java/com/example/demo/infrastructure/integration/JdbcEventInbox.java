package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Component
public class JdbcEventInbox implements EventInbox {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final EventCodec codec;
    private final JsonSql jsonSql;
    public JdbcEventInbox(JdbcTemplate jdbc, PlatformTransactionManager manager, EventCodec codec, JsonSql jsonSql) {
        this.jdbc=jdbc; this.codec=codec;this.jsonSql=jsonSql;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void receive(EventEnvelope event) {
        try {
            transaction.executeWithoutResult(tx -> jdbc.update("""
                    INSERT INTO event_inbox(event_id,event_type,event_version,source_module,correlation_id,causation_id,
                      occurred_at,payload,envelope) VALUES (?,?,?,?,?,?,?,CAST(? AS JSONB),CAST(? AS JSONB))
                    """,event.eventId(),event.eventType(),event.eventVersion(),event.sourceModule(),event.correlationId(),
                    event.causationId(),event.occurredAt(),jsonSql.parameter(event.data().toString()),jsonSql.parameter(codec.encode(event))));
        } catch (DuplicateKeyException duplicate) {
            // The failed INSERT is rolled back in its own transaction before processing.
        }
    }
    public String process(UUID id, EventHandlerRegistry registry) {
        try {
            return transaction.execute(tx -> {
                var rows = jdbc.queryForList("SELECT status,envelope FROM event_inbox WHERE event_id=? FOR UPDATE",id);
                if (rows.isEmpty()) throw new IllegalStateException("Missing Inbox entry");
                String status=rows.get(0).get("status").toString();
                if (status.equals("PROCESSED") || status.equals("REJECTED")) return "duplicated";
                var event=readJson(rows.get(0).get("envelope"));
                var handler=registry.find(event).orElseThrow(IllegalStateException::new);
                // Do not catch business rejection inside this transaction: partial effects must roll back too.
                handler.handle(event);
                jdbc.update("UPDATE event_inbox SET status='PROCESSED',error=NULL,processed_at=CURRENT_TIMESTAMP,attempts=attempts+1 WHERE event_id=?",id);
                return "processed";
            });
        } catch (RejectedEventException rejected) {
            transaction.executeWithoutResult(tx -> jdbc.update("UPDATE event_inbox SET status='REJECTED',error='BUSINESS_TRANSITION_REJECTED',processed_at=CURRENT_TIMESTAMP WHERE event_id=? AND status NOT IN ('PROCESSED','REJECTED')",id));
            return "rejected";
        } catch (RuntimeException technical) {
            transaction.executeWithoutResult(tx -> jdbc.update("UPDATE event_inbox SET status='ERROR',error='TECHNICAL_ERROR',attempts=attempts+1,next_attempt_at=? WHERE event_id=? AND status NOT IN ('PROCESSED','REJECTED')",
                    java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(1),id));
            throw technical;
        }
    }
    public java.util.List<UUID> pending() {
        return jdbc.query("SELECT event_id FROM event_inbox WHERE status IN ('RECEIVED','ERROR') AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY received_at LIMIT 20",
                (rs,n)->rs.getObject(1,UUID.class));
    }
    private EventEnvelope readJson(Object value) {
        byte[] bytes=value instanceof byte[] b ? b : value.toString().getBytes(StandardCharsets.UTF_8);
        return codec.decode(bytes);
    }
}
