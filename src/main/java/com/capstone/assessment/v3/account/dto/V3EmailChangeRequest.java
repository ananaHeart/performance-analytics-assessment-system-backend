package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SET-1: start (no code), resend (challengeUuid) or confirm (challengeUuid and code). The code
 * is tied to this exact new email, so the same email is sent each time.
 */
public record V3EmailChangeRequest(
        @NotBlank @Size(max = 120) String newEmail,
        String challengeUuid,
        String code
) {
}
