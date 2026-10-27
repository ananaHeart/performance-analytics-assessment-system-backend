package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TCH-7: request (identifier), resend (challengeUuid) or confirm (challengeUuid, code,
 * newPassword). The identifier is an email or a 09XXXXXXXXX / +639XXXXXXXXX number.
 */
public record V3PasswordResetRequest(
        @Size(max = 120) String identifier,
        String challengeUuid,
        String code,
        @Size(max = 128) String newPassword
) {
}
