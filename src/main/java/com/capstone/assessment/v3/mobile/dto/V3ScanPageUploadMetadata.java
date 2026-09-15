package com.capstone.assessment.v3.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record V3ScanPageUploadMetadata(
        @NotBlank(message = "is required")
        @Pattern(regexp = "3\\.0", message = "must be the supported contract version 3.0")
        String contractVersion,

        @NotBlank(message = "is required")
        @Pattern(regexp = UUID_PATTERN, message = "must be a canonical UUID")
        String syncUuid,

        @NotBlank(message = "is required")
        @Pattern(regexp = UUID_PATTERN, message = "must be a canonical UUID")
        String resultUuid,

        @NotBlank(message = "is required")
        @Pattern(regexp = UUID_PATTERN, message = "must be a canonical UUID")
        String scanUuid,

        @NotBlank(message = "is required")
        @Pattern(regexp = UUID_PATTERN, message = "must be a canonical UUID")
        String scanPageUuid,

        @NotBlank(message = "is required")
        @Pattern(regexp = UUID_PATTERN, message = "must be a canonical UUID")
        String answerSheetUuid,

        @NotBlank(message = "is required")
        @Pattern(regexp = UUID_PATTERN, message = "must be a canonical UUID")
        String pageUuid,

        @NotBlank(message = "is required")
        @Pattern(regexp = UUID_PATTERN, message = "must be a canonical UUID")
        String assignmentUuid,

        @Positive(message = "must be positive")
        long classListId,

        @Positive(message = "must be positive")
        int pageNumber,

        @Positive(message = "must be positive")
        int captureNumber,

        @NotBlank(message = "is required")
        String scannerVersion,

        @NotBlank(message = "is required")
        @Pattern(regexp = SHA256_PATTERN, message = "must be a lowercase SHA-256 hex value")
        String qrPayloadHash,

        @NotBlank(message = "is required")
        @Pattern(regexp = SHA256_PATTERN, message = "must be a lowercase SHA-256 hex value")
        String imageHash,

        @NotNull(message = "is required")
        Instant capturedAt
) {
    public static final String UUID_PATTERN =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
    public static final String SHA256_PATTERN = "^[0-9a-f]{64}$";
}
