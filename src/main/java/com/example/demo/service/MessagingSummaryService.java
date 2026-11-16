package com.example.demo.service;

import com.example.demo.dto.response.MessagingSummaryResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagingSummaryService {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;

    public MessagingSummaryService(JdbcTemplate jdbc, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public MessagingSummaryResponse summary() {
        var inbox = jdbc.queryForMap("""
                SELECT COUNT(*) total,
                       SUM(CASE WHEN status='PROCESSED' THEN 1 ELSE 0 END) processed,
                       SUM(CASE WHEN status='REJECTED' THEN 1 ELSE 0 END) rejected,
                       SUM(CASE WHEN status='ERROR' THEN 1 ELSE 0 END) errors,
                       SUM(CASE WHEN status='RECEIVED' THEN 1 ELSE 0 END) pending
                FROM event_inbox
                """);
        var outbox = jdbc.queryForMap("""
                SELECT SUM(CASE WHEN status='PUBLISHED' THEN 1 ELSE 0 END) published,
                       SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END) pending
                FROM event_outbox
                """);
        return new MessagingSummaryResponse(
                number(inbox, "total"), number(inbox, "processed"), number(inbox, "rejected"),
                number(inbox, "errors"), number(inbox, "pending"), number(outbox, "published"),
                number(outbox, "pending"), counter("duplicated"), counter("nacked"),
                counter("publication_failed"));
    }

    private long counter(String result) {
        return Math.round(metrics.counter("obras.events", "result", result).count());
    }

    private static long number(java.util.Map<String, Object> values, String key) {
        var value = values.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
