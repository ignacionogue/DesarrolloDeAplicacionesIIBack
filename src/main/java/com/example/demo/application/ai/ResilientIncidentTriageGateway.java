package com.example.demo.application.ai;

import com.example.demo.dto.response.IncidentTriageSuggestion;
import com.example.demo.infrastructure.ai.DeterministicIncidentTriageGateway;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ResilientIncidentTriageGateway {
    private final IncidentTriageGateway delegate;
    private final IncidentTriageMetrics metrics;
    private final Duration timeout;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;
    private final ExecutorService executor;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openUntil = new AtomicReference<>();

    @Autowired
    public ResilientIncidentTriageGateway(DeterministicIncidentTriageGateway delegate,
                                          IncidentTriageMetrics metrics,
                                          @Value("${app.ai.timeout-ms:5000}") long timeoutMs,
                                          @Value("${app.ai.circuit-breaker.failure-threshold:5}") int failureThreshold,
                                          @Value("${app.ai.circuit-breaker.open-seconds:30}") long openSeconds) {
        this(delegate, metrics, Duration.ofMillis(timeoutMs), failureThreshold,
                Duration.ofSeconds(openSeconds), Clock.systemUTC(), newDaemonExecutor());
    }

    ResilientIncidentTriageGateway(IncidentTriageGateway delegate, IncidentTriageMetrics metrics,
                                   Duration timeout, int failureThreshold, Duration openDuration,
                                   Clock clock, ExecutorService executor) {
        this.delegate = delegate;
        this.metrics = metrics;
        this.timeout = timeout;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
        this.executor = executor;
    }

    public IncidentTriageSuggestion suggest(String sanitizedDescription, String promptVersion) {
        Instant started = clock.instant();
        Instant blockedUntil = openUntil.get();
        if (blockedUntil != null && clock.instant().isBefore(blockedUntil)) {
            metrics.record("circuit_open", Duration.between(started, clock.instant()));
            return IncidentTriageSuggestion.manual("El clasificador esta temporalmente pausado");
        }
        if (blockedUntil != null) openUntil.compareAndSet(blockedUntil, null);
        Future<IncidentTriageSuggestion> future = executor.submit(
                () -> delegate.suggest(sanitizedDescription, promptVersion));
        try {
            IncidentTriageSuggestion result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            consecutiveFailures.set(0);
            metrics.record("success", Duration.between(started, clock.instant()));
            return result;
        } catch (TimeoutException timeoutError) {
            future.cancel(true);
            onFailure();
            metrics.record("timeout", Duration.between(started, clock.instant()));
            return IncidentTriageSuggestion.manual("El clasificador excedio el tiempo limite");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            onFailure();
            metrics.record("interrupted", Duration.between(started, clock.instant()));
            return IncidentTriageSuggestion.manual("El clasificador fue interrumpido");
        } catch (ExecutionException failure) {
            onFailure();
            metrics.record("provider_error", Duration.between(started, clock.instant()));
            return IncidentTriageSuggestion.manual("El clasificador no esta disponible");
        }
    }

    private void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntil.set(clock.instant().plus(openDuration));
            consecutiveFailures.set(0);
        }
    }

    private static ExecutorService newDaemonExecutor() {
        return Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "incident-triage");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
