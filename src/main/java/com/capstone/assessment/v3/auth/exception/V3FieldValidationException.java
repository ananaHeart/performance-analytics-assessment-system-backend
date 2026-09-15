package com.capstone.assessment.v3.auth.exception;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public class V3FieldValidationException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, Object> errors;

    public V3FieldValidationException(String message, HttpStatus status, Map<String, ?> errors) {
        super(message);
        this.status = status;
        this.errors = new LinkedHashMap<>(errors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getErrors() {
        return Map.copyOf(errors);
    }
}
