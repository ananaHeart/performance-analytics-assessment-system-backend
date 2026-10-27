package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** SET-1: like the email change; the code is tied to this exact new number. */
public record V3ContactChangeRequest(
        @NotBlank @Size(max = 20) String newContactNumber,
        String challengeUuid,
        String code
) {
}
