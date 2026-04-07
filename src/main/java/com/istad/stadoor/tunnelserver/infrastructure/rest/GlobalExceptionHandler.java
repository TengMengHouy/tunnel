package com.istad.stadoor.tunnelserver.infrastructure.rest;

import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CommandExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleCmd(CommandExecutionException ex) {
        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
        HttpStatus s = c instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST
                : c instanceof IllegalStateException ? HttpStatus.CONFLICT
                  : HttpStatus.INTERNAL_SERVER_ERROR;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", c.getMessage() != null ? c.getMessage() : "Command execution failed");
        body.put("status", s.value());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(s).body(body);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSec(SecurityException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage() != null ? ex.getMessage() : "Unauthorized");
        body.put("status", 401);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRt(RuntimeException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage() != null ? ex.getMessage() : "Unexpected error");
        body.put("status", 500);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage() != null ? ex.getMessage() : "Bad request");
        body.put("status", 400);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "Validation failed");
        body.put("status", 400);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }
}