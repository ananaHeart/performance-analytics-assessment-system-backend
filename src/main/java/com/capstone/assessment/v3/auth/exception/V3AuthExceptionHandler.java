package com.capstone.assessment.v3.auth.exception;

import com.capstone.assessment.common.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Profile("v3")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.capstone.assessment.v3")
public class V3AuthExceptionHandler {

    @ExceptionHandler(V3AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(V3AuthException exception) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", exception.getCode());
        errors.putAll(exception.getDetails());
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.error(exception.getMessage(), errors));
    }

    @ExceptionHandler(V3FieldValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleFieldValidation(V3FieldValidationException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.error(exception.getMessage(), exception.getErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", "VALIDATION_FAILED");
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed.", errors));
    }
}
