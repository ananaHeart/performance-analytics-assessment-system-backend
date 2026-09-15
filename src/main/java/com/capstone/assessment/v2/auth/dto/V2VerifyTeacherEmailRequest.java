package com.capstone.assessment.v2.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record V2VerifyTeacherEmailRequest(
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Verification code must contain exactly 6 digits.") String otp
) {
}
