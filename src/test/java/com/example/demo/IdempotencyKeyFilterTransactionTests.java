package com.example.demo;

import com.example.demo.security.IdempotencyKeyFilter;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyKeyFilterTransactionTests {
    @Test void partialFailureRollsBackBusinessEffectAndIdempotencyReservation() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:idempotency-rollback;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE rest_idempotency(request_scope VARCHAR(500) PRIMARY KEY, request_hash VARCHAR(64), " +
                "response_status INTEGER, response_content_type VARCHAR(100), response_etag VARCHAR(100), response_body CLOB)");
        jdbc.execute("CREATE TABLE business_effect(id INTEGER PRIMARY KEY)");
        IdempotencyKeyFilter filter = new IdempotencyKeyFilter(jdbc, new DataSourceTransactionManager(source));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public-works/projects");
        request.addHeader("Idempotency-Key", "rollback-key");
        request.setContentType("application/json");
        request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(request,
                new MockHttpServletResponse(), (req, response) -> {
                    jdbc.update("INSERT INTO business_effect(id) VALUES (1)");
                    throw new jakarta.servlet.ServletException("controlled partial failure");
                }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM business_effect", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rest_idempotency", Integer.class));
    }
}
