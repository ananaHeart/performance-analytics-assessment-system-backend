package com.capstone.assessment.v2.auth.dto;

import java.time.Instant;

public record V2LoginResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        V2CurrentUserResponse user
) {
}
