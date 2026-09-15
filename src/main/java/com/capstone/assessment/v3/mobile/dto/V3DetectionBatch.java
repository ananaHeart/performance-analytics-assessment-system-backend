package com.capstone.assessment.v3.mobile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record V3DetectionBatch(
        @NotNull @Pattern(regexp = "3\\.0") String contractVersion,
        @NotNull @Pattern(regexp = V3ScanPageUploadMetadata.UUID_PATTERN) String syncUuid,
        @NotNull @Pattern(regexp = V3ScanPageUploadMetadata.UUID_PATTERN) String operationUuid,
        @NotNull @Size(min = 1, max = 200) List<@NotNull @Valid Detection> detections) {
    public record Detection(
            @NotNull @Pattern(regexp = V3ScanPageUploadMetadata.UUID_PATTERN) String detectionUuid,
            @NotNull @Pattern(regexp = V3ScanPageUploadMetadata.UUID_PATTERN) String regionUuid,
            @NotNull @Pattern(regexp = V3ScanPageUploadMetadata.UUID_PATTERN) String questionUuid,
            @NotNull @Pattern(regexp = "detected|blank|multiple_marks|uncertain") String detectionStatus,
            @Pattern(regexp = "[A-D]") String detectedOption,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal confidence) { }
}
