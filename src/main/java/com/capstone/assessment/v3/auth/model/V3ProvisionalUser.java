package com.capstone.assessment.v3.auth.model;

import java.time.Instant;

public record V3ProvisionalUser(
        long userId,
        long addressId,
        String email,
        Instant createdAt
) {
}
