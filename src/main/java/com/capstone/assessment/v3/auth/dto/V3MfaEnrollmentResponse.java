package com.capstone.assessment.v3.auth.dto;

import java.time.Instant;

public record V3MfaEnrollmentResponse(
        String factorUuid,
        String factorName,
        String status,
        String manualEntryKey,
        String otpauthUri,
        String qrCodeDataUrl,
        Instant enrolledAt
) {
}
