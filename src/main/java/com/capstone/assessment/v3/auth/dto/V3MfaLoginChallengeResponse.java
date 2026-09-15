package com.capstone.assessment.v3.auth.dto;

import java.time.Instant;
import java.util.List;

public record V3MfaLoginChallengeResponse(
        String challengeUuid,
        Instant expiresAt,
        int maximumAttempts,
        List<String> verificationMethods,
        String emailMasked
) {
}
