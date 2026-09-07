package com.example.demo.exception;

/**
 * Codigos de error estables para el contrato de API.
 * El Frontend puede usar 'code' para manejar casos especificos sin parsear el mensaje.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    NOT_FOUND,
    INVALID_STATE_TRANSITION,
    BUSINESS_RULE_VIOLATION,
    INTERNAL_ERROR
}
