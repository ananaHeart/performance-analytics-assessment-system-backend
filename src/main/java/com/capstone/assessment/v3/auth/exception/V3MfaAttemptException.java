package com.capstone.assessment.v3.auth.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Authentication rejection whose attempt, lock, or expiry state must be committed.
 */
public class V3MfaAttemptException extends V3AuthException {

    public V3MfaAttemptException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public V3MfaAttemptException(
            String code,
            String message,
            HttpStatus status,
            Map<String, ?> details
    ) {
        super(code, message, status, details);
    }
}
