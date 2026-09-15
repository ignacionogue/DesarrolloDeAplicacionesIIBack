package com.example.demo.exception;

import java.util.List;

/**
 * Forma estandar de error para toda la API.
 * {"message": "...", "code": "...", "details": [...]}
 */
public record ErrorResponse(String message, String code, List<String> details) {

    public static ErrorResponse of(String message, ErrorCode code) {
        return new ErrorResponse(message, code.name(), List.of());
    }

    public static ErrorResponse of(String message, ErrorCode code, List<String> details) {
        return new ErrorResponse(message, code.name(), details);
    }
}
