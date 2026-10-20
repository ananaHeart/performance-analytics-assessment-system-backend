package com.capstone.assessment.v3.auth.dto;

import java.time.Instant;

public record V3RegistrationResponse(
        String accountStatus,
        // "email" or "sms": where the code went; only that one code is needed.
        String verificationMethod,
        // Set for email; null for sms.
        String emailMasked,
        // Set for sms; null for email.
        String contactMasked,
        String challengeUuid,
        String deliveryStatus,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        Instant registrationExpiresAt
) {
}
