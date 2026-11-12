package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IncidentTriageRateLimitFilter extends OncePerRequestFilter {
    private final int maximumRequests;
    private final long windowSeconds;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Autowired
    public IncidentTriageRateLimitFilter(
            @Value("${app.ai.rate-limit.max-requests:10}") int maximumRequests,
            @Value("${app.ai.rate-limit.window-seconds:60}") long windowSeconds) {
        this(maximumRequests, windowSeconds, Clock.systemUTC());
    }

    IncidentTriageRateLimitFilter(int maximumRequests, long windowSeconds, Clock clock) {
        this.maximumRequests = maximumRequests;
        this.windowSeconds = windowSeconds;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/api/public-works/assistant/triage".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String key = authentication == null ? request.getRemoteAddr() : authentication.getName();
        Instant now = clock.instant();
        Deque<Instant> userAttempts = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (userAttempts) {
            Instant cutoff = now.minusSeconds(windowSeconds);
            while (!userAttempts.isEmpty() && userAttempts.peekFirst().isBefore(cutoff)) userAttempts.removeFirst();
            if (userAttempts.size() >= maximumRequests) {
                response.setStatus(429);
                response.setContentType("application/problem+json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Retry-After", Long.toString(windowSeconds));
                response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,"
                        + "\"detail\":\"Se alcanzo el limite del asistente\",\"code\":\"RATE_LIMITED\"}");
                return;
            }
            userAttempts.addLast(now);
        }
        chain.doFilter(request, response);
    }
}
