package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Persistent Idempotency-Key replay for authenticated POST commands. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class IdempotencyKeyFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public IdempotencyKeyFilter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/public-works/")
                || request.getHeader("Idempotency-Key") == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("Idempotency-Key").trim();
        if (key.isEmpty() || key.length() > 128) {
            writeProblem(response, 400, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key debe tener entre 1 y 128 caracteres");
            return;
        }
        String actor = SecurityContextHolder.getContext().getAuthentication() == null
                ? "anonymous" : SecurityContextHolder.getContext().getAuthentication().getName();
        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        String hash = sha256(cachedRequest.body());
        String scope = actor + ":" + request.getMethod() + ":" + request.getRequestURI() + ":" + key;

        StoredResponse stored = find(scope);
        if (stored != null) {
            replayOrReject(response, hash, stored);
            return;
        }
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO rest_idempotency(request_scope,request_hash) VALUES (?,?)", scope, hash);
                try {
                    chain.doFilter(cachedRequest, cachedResponse);
                } catch (IOException | ServletException failure) {
                    throw new FilterFailure(failure);
                }
                byte[] body = cachedResponse.getContentAsByteArray();
                if (cachedResponse.getStatus() < 500) {
                    jdbc.update("UPDATE rest_idempotency SET response_status=?,response_content_type=?,response_etag=?,response_body=? WHERE request_scope=?",
                            cachedResponse.getStatus(), cachedResponse.getContentType(), cachedResponse.getHeader("ETag"),
                            new String(body, StandardCharsets.UTF_8), scope);
                } else {
                    status.setRollbackOnly();
                }
            });
        } catch (DuplicateKeyException concurrent) {
            stored = find(scope);
            if (stored == null || stored.status() == null) {
                writeProblem(response, 409, "IDEMPOTENCY_IN_PROGRESS", "Ya se esta procesando una solicitud con esa clave");
            } else {
                replayOrReject(response, hash, stored);
            }
            return;
        } catch (FilterFailure failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            if (failure.getCause() instanceof ServletException servlet) throw servlet;
            throw failure;
        } catch (RuntimeException failure) {
            throw failure;
        }
        cachedResponse.copyBodyToResponse();
    }

    private StoredResponse find(String scope) {
        return jdbc.query("SELECT request_hash,response_status,response_content_type,response_etag,response_body FROM rest_idempotency WHERE request_scope=?",
                rs -> rs.next() ? new StoredResponse(rs.getString(1), (Integer) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5)) : null,
                scope);
    }

    private static void replayOrReject(HttpServletResponse response, String requestHash, StoredResponse stored) throws IOException {
        if (!stored.requestHash().equals(requestHash)) {
            writeProblem(response, 409, "IDEMPOTENCY_KEY_REUSED", "La clave ya fue usada con otro cuerpo");
            return;
        }
        if (stored.status() == null) {
            writeProblem(response, 409, "IDEMPOTENCY_IN_PROGRESS", "Ya se esta procesando una solicitud con esa clave");
            return;
        }
        response.setStatus(stored.status());
        response.setContentType(stored.contentType() == null ? MediaType.APPLICATION_JSON_VALUE : stored.contentType());
        if (stored.etag() != null) response.setHeader("ETag", stored.etag());
        response.setHeader("Idempotency-Replayed", "true");
        if (stored.body() != null) response.getWriter().write(stored.body());
    }

    private static void writeProblem(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"https://obras.municipalidad.example/problems/idempotency\"," +
                "\"title\":\"Idempotency error\",\"status\":" + status + ",\"detail\":\"" + detail +
                "\",\"message\":\"" + detail + "\",\"code\":\"" + code + "\",\"details\":[]}");
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record StoredResponse(String requestHash, Integer status, String contentType, String etag, String body) { }

    private static final class FilterFailure extends RuntimeException {
        private FilterFailure(Exception cause) { super(cause); }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            body = request.getInputStream().readAllBytes();
        }
        private byte[] body() { return body.clone(); }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) { /* synchronous request */ }
                @Override public int read() { return input.read(); }
            };
        }
    }
}
