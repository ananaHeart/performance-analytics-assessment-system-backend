package com.capstone.assessment.v2.auth.dto;

public record V2CurrentUserResponse(
        Long userId,
        String schoolId,
        String firstName,
        String middleName,
        String lastName,
        String suffix,
        String email,
        String role,
        String status
) {
}
