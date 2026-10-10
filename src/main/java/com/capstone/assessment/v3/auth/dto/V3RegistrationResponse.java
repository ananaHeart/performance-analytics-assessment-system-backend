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
        Instant registrationExpiresAt,
        // Null while SMS verification is off.
        ContactVerification contactVerification
) {
    /**
     * The SMS code for the contact number. While {@code required}, the teacher enters it together
     * with the email code; when the SMS could not be sent it is not required.
     */
    public record ContactVerification(
            boolean required,
            String contactMasked,
            String deliveryStatus,
            Instant codeExpiresAt,
            Instant resendAvailableAt
    ) {
    }
}
