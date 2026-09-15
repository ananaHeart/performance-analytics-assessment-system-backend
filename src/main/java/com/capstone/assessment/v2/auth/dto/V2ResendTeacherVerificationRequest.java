package com.capstone.assessment.v2.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record V2ResendTeacherVerificationRequest(
        @NotBlank @Email @Size(max = 120) String email
) {
}
