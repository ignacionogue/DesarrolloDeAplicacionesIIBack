package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/** Small, bounded login brute-force guard. Successful authentication resets the source bucket. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maximumAttempts;
    private final long windowMillis;
    private final Clock clock;

    @Autowired
    public LoginRateLimitFilter(
            @Value("${app.auth.rate-limit.max-attempts:10}") int maximumAttempts,
            @Value("${app.auth.rate-limit.window-seconds:60}") long windowSeconds) {
        this(maximumAttempts, windowSeconds, Clock.systemUTC());
    }

    LoginRateLimitFilter(int maximumAttempts, long windowSeconds, Clock clock) {
        if (maximumAttempts < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("La configuracion de rate limiting debe ser positiva");
        }
        this.maximumAttempts = maximumAttempts;
        this.windowMillis = Math.multiplyExact(windowSeconds, 1000L);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/auth/login".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String source = request.getRemoteAddr();
        long now = clock.millis();
        Attempts current = attempts.compute(source, (ignored, previous) ->
                previous == null || previous.expired(now, windowMillis) ? new Attempts(now, 0) : previous);
        if (current.failures() >= maximumAttempts) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(Math.max(1,
                    (current.startedAt() + windowMillis - now + 999) / 1000)));
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"type\":\"https://obras.municipalidad.example/problems/rate-limit\"," +
                    "\"title\":\"Too Many Requests\",\"status\":429," +
                    "\"detail\":\"Demasiados intentos de inicio de sesion\"," +
                    "\"message\":\"Demasiados intentos de inicio de sesion\"," +
                    "\"code\":\"RATE_LIMITED\",\"details\":[]}");
            return;
        }

        chain.doFilter(request, response);
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            attempts.computeIfPresent(source, (ignored, value) -> value.increment());
        } else if (response.getStatus() < 400) {
            attempts.remove(source);
        }
    }

    private record Attempts(long startedAt, int failures) {
        boolean expired(long now, long windowMillis) { return now - startedAt >= windowMillis; }
        Attempts increment() { return new Attempts(startedAt, failures + 1); }
    }
}
