package com.capstone.assessment.v2.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record V2LoginRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        @Size(max = 120, message = "Email must not exceed 120 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 128, message = "Password must not exceed 128 characters.")
        String password,

        @Size(max = 100, message = "Device identifier must not exceed 100 characters.")
        String deviceIdentifier
) {
}
