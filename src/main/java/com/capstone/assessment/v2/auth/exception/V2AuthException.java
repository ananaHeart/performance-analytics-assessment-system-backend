package com.capstone.assessment.v2.auth.exception;

import org.springframework.http.HttpStatus;

public class V2AuthException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public V2AuthException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
