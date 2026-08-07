package com.personalfinance.quest.exception;

import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    /**
     * The calendar's optional `month` parameter is parsed by hand rather than
     * bean-validated, so a malformed value arrives here instead of as a
     * MethodArgumentNotValidException. Without this mapping it would surface as
     * a 500 for what is plainly a bad request.
     */
    @ExceptionHandler(DateTimeParseException.class)
    ResponseEntity<Map<String, String>> badDate(DateTimeParseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid date value: " + e.getParsedString()));
    }
}
