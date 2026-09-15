package com.example.demo.exception;

/** Se lanza ante violaciones de reglas de negocio que no encajan en validacion de campos ni transicion de estado. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(message, ErrorCode.BUSINESS_RULE_VIOLATION);
    }
}
