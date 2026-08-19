package com.capstone.assessment.v2.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record V2SyncDetectionUploadRequest(
        @NotNull Long questionId,
        Integer itemNumber,
        String detectedOption,
        @NotNull BigDecimal confidenceScore,
        @NotBlank String detectionStatus,
        @NotBlank String verificationStatus,
        Object rawMarkInformation,
        @NotNull OffsetDateTime detectedAt
) {
}
