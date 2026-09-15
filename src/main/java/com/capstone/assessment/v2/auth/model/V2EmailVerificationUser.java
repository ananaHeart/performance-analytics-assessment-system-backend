package com.capstone.assessment.v2.auth.model;

public record V2EmailVerificationUser(
        Long userId,
        String schoolId,
        String firstName,
        String lastName,
        String email,
        String status,
        boolean emailVerified
) {
}
