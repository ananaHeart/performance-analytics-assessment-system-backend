package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record V3ResendVerificationRequest(@NotBlank String challengeUuid) {
}
