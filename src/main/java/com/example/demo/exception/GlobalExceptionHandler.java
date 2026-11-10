package com.example.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Traduce cualquier excepcion conocida (o no) al formato de error estable
 * {message, code, details} que espera el Frontend. Nunca devuelve stacktraces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleMalformedRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "El cuerpo o los parametros de la solicitud no son validos",
                ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidStateTransition(InvalidStateTransitionException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(PreconditionFailedException.class)
    public ResponseEntity<ProblemDetail> handlePreconditionFailed(PreconditionFailedException ex) {
        return problem(HttpStatus.PRECONDITION_FAILED, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex.getErrorCode());
    }

    /** Errores de Bean Validation (@Valid en los DTOs de request). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblems.of(HttpStatus.BAD_REQUEST, "Los datos enviados no son validos",
                        ErrorCode.VALIDATION_ERROR, details));
    }

    /** Cualquier otra excepcion no prevista: no se expone el detalle interno al Front. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error interno", ErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String message, ErrorCode code) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblems.of(status, message, code));
    }
}
