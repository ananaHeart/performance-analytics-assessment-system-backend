package com.capstone.assessment.v2.auth.model;

import java.time.Instant;

public record V2EmailVerificationOtp(
        Long otpId,
        Long userId,
        String otpHash,
        int attemptCount,
        int maxAttempts,
        Instant expiresAt,
        Instant resendAvailableAt,
        Instant usedAt
) {
}
