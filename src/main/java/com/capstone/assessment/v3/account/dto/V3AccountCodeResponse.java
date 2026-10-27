package com.capstone.assessment.v3.account.dto;

import java.time.Instant;

/** A 6-digit code was sent; a resend returns a new challengeUuid and the old code stops working. */
public record V3AccountCodeResponse(
        String challengeUuid,
        String channel,
        String destinationMasked,
        Instant otpExpiresAt,
        Instant resendAvailableAt
) {
}
