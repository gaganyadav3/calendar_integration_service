package com.omvrti.calendar_service.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CalendarException.class)
    public ResponseEntity<Map<String, Object>> handleCalendarException(CalendarException ex) {
        log.error("CalendarException occurred", ex);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", ex.getClass().getSimpleName());
        response.put("errorCode", ex.getErrorCode());
        response.put("message", ex.getMessage());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<Map<String, Object>> handleOAuthException(OAuthException ex) {
        log.error("OAuthException occurred for provider: {}", ex.getProviderName(), ex);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", ex.getClass().getSimpleName());
        response.put("errorCode", ex.getErrorCode());
        response.put("provider", ex.getProviderName());
        response.put("message", ex.getMessage());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException occurred", ex);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", ex.getClass().getSimpleName());
        response.put("message", ex.getMessage());
        response.put("cause", ex.getCause() != null ? ex.getCause().getMessage() : null);
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        log.error("Unexpected exception occurred", ex);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", ex.getClass().getSimpleName());
        response.put("message", ex.getMessage());
        response.put("cause", ex.getCause() != null ? ex.getCause().getMessage() : null);
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(ProviderAuthException.class)
    public ResponseEntity<Map<String, Object>> handleProviderAuthException(ProviderAuthException ex) {
        log.error("ProviderAuthException for provider: {}", ex.getProvider(), ex);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", ex.getClass().getSimpleName());
        response.put("provider", ex.getProvider());
        response.put("message", ex.getMessage());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(SyncException.class)
    public ResponseEntity<Map<String, Object>> handleSyncException(SyncException ex) {
        log.error("SyncException occurred", ex);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", ex.getClass().getSimpleName());
        response.put("errorCode", ex.getErrorCode());
        response.put("message", ex.getMessage());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
