package com.capstone.assessment.auth.dto;

public record LoginResponse(
        Long userId,
        String firstName,
        String lastName,
        String email,
        String role,
        String status,
        String token
) {
}
