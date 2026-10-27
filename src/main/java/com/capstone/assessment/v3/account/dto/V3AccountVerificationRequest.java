package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.Pattern;

/** VER-1: verify the channel not chosen at registration (start: channel; confirm: challengeUuid and code). */
public record V3AccountVerificationRequest(
        @Pattern(regexp = "(?i)^(email|sms)$", message = "Choose email or sms.") String channel,
        String challengeUuid,
        String code
) {
}
