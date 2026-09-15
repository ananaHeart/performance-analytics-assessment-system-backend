package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record V3VerifyMfaLoginRequest(
        @NotBlank @Size(max = 36) String challengeUuid,
        @NotBlank @Size(max = 32) String code,
        @Pattern(regexp = "authenticator|recovery_code") String verificationMethod,
        @Size(max = 100) String deviceIdentifier
) {
}
