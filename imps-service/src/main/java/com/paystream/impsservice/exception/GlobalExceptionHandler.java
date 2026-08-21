package com.paystream.impsservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidAccountException.class)
    public ResponseEntity<ApiError> handleInvalidAccount(InvalidAccountException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError(Instant.now(), 422, "INVALID_ACCOUNT", ex.getMessage()));
    }

    @ExceptionHandler(ImpsTransactionNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ImpsTransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(Instant.now(), 404, "NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MmidNotFoundException.class)
    public ResponseEntity<ApiError> handleMmidNotFound(MmidNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(Instant.now(), 404, "MMID_NOT_FOUND", ex.getMessage()));
    }


    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(ServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(Instant.now(), 503, "SERVICE_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(PaymentRailNotEnabledException.class)
    public ResponseEntity<ApiError> handleRailNotEnabled(PaymentRailNotEnabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(Instant.now(), 403, "PAYMENT_RAIL_NOT_ENABLED", ex.getMessage()));
    }

    @ExceptionHandler(PerTransactionLimitExceededException.class)
    public ResponseEntity<ApiError> handlePerTransactionLimitExceeded(PerTransactionLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError(Instant.now(), 422, "PER_TRANSACTION_LIMIT_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(DailyLimitExceededException.class)
    public ResponseEntity<ApiError> handleDailyLimitExceeded(DailyLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError(Instant.now(), 422, "DAILY_LIMIT_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(Instant.now(), 400, "VALIDATION_FAILED", message));
    }
}
