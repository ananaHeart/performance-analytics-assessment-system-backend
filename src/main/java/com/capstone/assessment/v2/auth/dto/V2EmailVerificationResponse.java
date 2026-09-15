package com.capstone.assessment.v2.auth.dto;

import java.time.Instant;

public record V2EmailVerificationResponse(
        String email,
        boolean emailVerified,
        String accountStatus,
        Instant expiresAt,
        Instant resendAvailableAt
) {
}
