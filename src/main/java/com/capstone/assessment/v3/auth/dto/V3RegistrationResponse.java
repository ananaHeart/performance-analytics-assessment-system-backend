package com.capstone.assessment.v3.auth.dto;

import java.time.Instant;

public record V3RegistrationResponse(
        String accountStatus,
        String verificationMethod,
        String emailMasked,
        String challengeUuid,
        String deliveryStatus,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        Instant registrationExpiresAt
) {
}
