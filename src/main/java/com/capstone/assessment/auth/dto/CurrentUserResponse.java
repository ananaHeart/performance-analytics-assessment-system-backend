package com.capstone.assessment.auth.dto;

public record CurrentUserResponse(
        Long userId,
        String firstName,
        String lastName,
        String email,
        String role,
        String status
) {
}
