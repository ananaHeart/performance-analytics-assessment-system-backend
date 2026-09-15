package com.capstone.assessment.v3.auth.model;

import java.time.Instant;

public record V3MfaFactor(
        long factorId,
        String factorUuid,
        long userId,
        String factorName,
        byte[] secretCiphertext,
        int secretKeyVersion,
        String algorithm,
        int digits,
        int periodSeconds,
        String status,
        Instant enrolledAt,
        Instant verifiedAt,
        Instant lastUsedAt
) {
}
