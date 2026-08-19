package com.capstone.assessment.v2.auth.model;

public record V2AuthenticatedUser(
        Long userId,
        String schoolId,
        String email,
        String role,
        String status,
        String sessionUuid
) {
}
