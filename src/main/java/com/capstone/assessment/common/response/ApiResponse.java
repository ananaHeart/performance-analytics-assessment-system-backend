package com.capstone.assessment.common.response;

import java.time.Instant;
import java.util.Map;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Map<String, Object> errors,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Request completed successfully.", data);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, null, Instant.now());
    }

    public static ApiResponse<Void> error(String message, Map<String, Object> errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now());
    }
}
