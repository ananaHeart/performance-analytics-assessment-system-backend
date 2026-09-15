package com.capstone.assessment.v3.auth.exception;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public class V3AuthException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public V3AuthException(String code, String message, HttpStatus status) {
        this(code, message, status, Map.of());
    }

    public V3AuthException(String code, String message, HttpStatus status, Map<String, ?> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = new LinkedHashMap<>(details);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return Map.copyOf(details);
    }
}
