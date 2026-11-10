package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class JdbcEventOutbox implements EventOutbox {
    private final JdbcTemplate jdbc;
    private final EventCodec codec;
    private final TransactionTemplate transaction;
    private final EventMetrics metrics;
    private final JsonSql jsonSql;
    public JdbcEventOutbox(JdbcTemplate jdbc, EventCodec codec, PlatformTransactionManager manager, EventMetrics metrics, MeterRegistry registry, JsonSql jsonSql) {
        this.jdbc=jdbc; this.codec=codec; this.transaction=new TransactionTemplate(manager); this.metrics=metrics;this.jsonSql=jsonSql;
        registry.gauge("obras.outbox.backlog", this, outbox -> outbox.pendingCount());
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void append(EventEnvelope event, String orderingKey) {
        if (!event.sourceModule().equals("obras") || event.correlationId()==null) throw new InvalidEventException();
        if (orderingKey==null || orderingKey.isBlank() || orderingKey.length()>180) throw new IllegalArgumentException("INVALID_ORDERING_KEY");
        // Serialize sequence allocation until commit, not only delivery of already-visible rows.
        if (jsonSql.postgres()) jdbc.queryForList("SELECT pg_advisory_xact_lock(?,?)", 31003, orderingKey.hashCode());
        jdbc.update("INSERT INTO event_outbox(id,event_id,ordering_key,envelope,payload) VALUES (?,?,?,CAST(? AS JSONB),CAST(? AS JSONB))",
                UUID.randomUUID(),event.eventId(),orderingKey,jsonSql.parameter(codec.encode(event)),jsonSql.parameter(event.data().toString()));
    }
    public long pendingCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE status='PENDING'",Long.class);
    }
    public boolean publishOne(EventTransport transport) {
        return Boolean.TRUE.equals(transaction.execute(tx -> {
            // The predecessor test prevents another worker skipping STARTED and publishing PROGRESS.
            var rows=jdbc.queryForList("""
                SELECT o.id,o.envelope FROM event_outbox o
                WHERE o.status='PENDING' AND o.next_attempt_at<=CURRENT_TIMESTAMP
                AND NOT EXISTS (SELECT 1 FROM event_outbox earlier WHERE earlier.ordering_key=o.ordering_key
                    AND earlier.sequence_number<o.sequence_number AND earlier.status='PENDING')
                ORDER BY o.sequence_number LIMIT 1 FOR UPDATE SKIP LOCKED
                """);
            if(rows.isEmpty()) return false;
            var row=rows.get(0); Object raw=row.get("envelope");
            var event=codec.decode(raw instanceof byte[] b ? b : raw.toString().getBytes(StandardCharsets.UTF_8));
            try {
                transport.publish(event);
                jdbc.update("UPDATE event_outbox SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,attempts=attempts+1,last_error=NULL WHERE id=?",row.get("id"));
                metrics.result("published",event);
            } catch (RuntimeException failure) {
                jdbc.update("UPDATE event_outbox SET attempts=attempts+1,last_error='PUBLICATION_FAILED',next_attempt_at=? WHERE id=?",
                        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(30),row.get("id"));
                metrics.result("publication_failed",event);
            }
            return true;
        }));
    }
}
