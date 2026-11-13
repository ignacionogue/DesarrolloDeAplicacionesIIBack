package com.example.demo.service;

import com.example.demo.dto.response.IntegrationEventResponse;
import com.example.demo.dto.response.PageResponse;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class IntegrationEventQueryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public IntegrationEventQueryService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public PageResponse<IntegrationEventResponse> list(int page, int size, String direction) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String normalized = direction == null ? "ALL" : direction.toUpperCase(java.util.Locale.ROOT);
        if (!List.of("ALL", "INCOMING", "OUTGOING").contains(normalized)) normalized = "ALL";
        String filter = switch (normalized) {
            case "INCOMING" -> " WHERE direction='INCOMING' ";
            case "OUTGOING" -> " WHERE direction='OUTGOING' ";
            default -> " ";
        };
        String union = "SELECT CAST(event_id AS VARCHAR) id,'INCOMING' direction,event_type,source_module,status,occurred_at,CAST(envelope AS VARCHAR) envelope FROM event_inbox " +
                "UNION ALL SELECT CAST(event_id AS VARCHAR) id,'OUTGOING' direction,NULL event_type,NULL source_module,status,created_at occurred_at,CAST(envelope AS VARCHAR) envelope FROM event_outbox";
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + union + ") events" + filter, Long.class);
        var content = jdbc.query("SELECT * FROM (" + union + ") events" + filter + "ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    String eventType = rs.getString("event_type");
                    String sourceModule = rs.getString("source_module");
                    if (eventType == null || sourceModule == null) {
                        try {
                            var envelope = json.readTree(rs.getString("envelope"));
                            if (eventType == null) eventType = envelope.path("eventType").asText(null);
                            if (sourceModule == null) sourceModule = envelope.path("sourceModule").asText(null);
                        } catch (Exception ignored) {
                            // The operational list remains useful without optional envelope labels.
                        }
                    }
                    OffsetDateTime occurredAt = rs.getObject("occurred_at", OffsetDateTime.class);
                    return new IntegrationEventResponse(rs.getString("id"), rs.getString("direction"), eventType,
                            sourceModule, rs.getString("status"), occurredAt);
                }, safeSize, safePage * safeSize);
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        return new PageResponse<>(content, safePage, safeSize, total, pages);
    }
}
