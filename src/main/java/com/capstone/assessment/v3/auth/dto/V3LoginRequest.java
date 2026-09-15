package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record V3LoginRequest(
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(max = 128) String password,
        @Size(max = 100) String deviceIdentifier
) {
}
