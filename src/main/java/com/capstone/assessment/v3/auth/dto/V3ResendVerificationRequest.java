package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record V3ResendVerificationRequest(
        @NotBlank String challengeUuid,
        // Optional: switch where the new code goes, e.g. "email" when the SMS can't get through.
        @Pattern(regexp = "email|sms", message = "Verification method must be email or sms.")
        String verificationMethod
) {
}
