package com.capstone.assessment.v3.auth.model;

public record V3AuthenticatedUser(
        long userId,
        String schoolId,
        String email,
        String role,
        String status,
        String sessionUuid
) {
}
