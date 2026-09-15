package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record V3MfaSensitiveActionRequest(
        @NotBlank @Size(max = 128) String password,
        @NotBlank @Size(max = 32) String code,
        @Pattern(regexp = "authenticator|recovery_code") String verificationMethod
) {
}
