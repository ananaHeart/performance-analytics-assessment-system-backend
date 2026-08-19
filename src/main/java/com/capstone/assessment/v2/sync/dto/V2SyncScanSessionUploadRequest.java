package com.capstone.assessment.v2.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record V2SyncScanSessionUploadRequest(
        @NotBlank String scanUuid,
        @NotBlank String templateVersion,
        @NotBlank String scannerVersion,
        String imageHash,
        @NotBlank String scanStatus,
        @NotNull OffsetDateTime scannedAt,
        @NotNull OffsetDateTime verifiedAt,
        @Valid List<V2SyncDetectionUploadRequest> detections
) {
}
