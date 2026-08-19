package com.capstone.assessment.v2.auth.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class V2FieldValidationException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, Object> errors;

    public V2FieldValidationException(String message, HttpStatus status, Map<String, Object> errors) {
        super(message);
        this.status = status;
        this.errors = errors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getErrors() {
        return errors;
    }
}
