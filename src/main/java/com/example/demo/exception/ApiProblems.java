package com.example.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;

/** RFC 9457 Problem Details factory with the legacy fields kept for Front compatibility. */
public final class ApiProblems {
    private static final URI TYPE_BASE = URI.create("https://obras.municipalidad.example/problems/");

    private ApiProblems() {
    }

    public static ProblemDetail of(HttpStatus status, String message, ErrorCode code) {
        return of(status, message, code.name(), List.of());
    }

    public static ProblemDetail of(HttpStatus status, String message, ErrorCode code, List<String> details) {
        return of(status, message, code.name(), details);
    }

    public static ProblemDetail of(HttpStatus status, String message, String code, List<String> details) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setType(TYPE_BASE.resolve(code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("message", message);
        problem.setProperty("code", code);
        problem.setProperty("details", details);
        return problem;
    }
}
