package com.capstone.assessment.v3.auth.dto;

import java.time.Instant;

public record V3MfaStatusResponse(
        boolean available,
        boolean enabled,
        String factorUuid,
        String factorName,
        Instant verifiedAt,
        Instant lastUsedAt,
        int unusedRecoveryCodeCount
) {
}
