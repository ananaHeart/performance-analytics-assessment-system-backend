package com.capstone.assessment.v2.auth.exception;

import com.capstone.assessment.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.LinkedHashMap;
import java.util.Map;

@Profile("v2")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.capstone.assessment.v2")
public class V2AuthExceptionHandler {

    @ExceptionHandler(V2AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(V2AuthException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.error(
                        exception.getMessage(),
                        Map.of("code", exception.getCode())
                ));
    }

    @ExceptionHandler(V2FieldValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleFieldValidationException(V2FieldValidationException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.error(exception.getMessage(), exception.getErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", "VALIDATION_FAILED");
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed.", errors));
    }
}
