package com.capstone.assessment.v3.auth.dto;

import java.time.Instant;

public record V3LoginResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        V3CurrentUserResponse user,
        boolean mfaRequired,
        V3MfaLoginChallengeResponse mfaChallenge
) {

    public V3LoginResponse(
            String tokenType,
            String accessToken,
            Instant expiresAt,
            V3CurrentUserResponse user
    ) {
        this(tokenType, accessToken, expiresAt, user, false, null);
    }

    public static V3LoginResponse mfaRequired(V3MfaLoginChallengeResponse challenge) {
        return new V3LoginResponse(null, null, null, null, true, challenge);
    }
}
