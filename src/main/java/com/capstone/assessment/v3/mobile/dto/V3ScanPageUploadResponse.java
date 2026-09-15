package com.capstone.assessment.v3.mobile.dto;

import java.time.Instant;

public record V3ScanPageUploadResponse(
        String syncUuid,
        String resultUuid,
        String scanUuid,
        String scanPageUuid,
        long backendScanPageId,
        String uploadStatus,
        String pageStatus,
        String contentHash,
        Instant acknowledgedAt
) {
}
