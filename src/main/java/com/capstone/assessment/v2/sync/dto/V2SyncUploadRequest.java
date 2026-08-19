package com.capstone.assessment.v2.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record V2SyncUploadRequest(
        @NotBlank String contractVersion,
        @NotBlank String syncUuid,
        String deviceIdentifier,
        @NotNull OffsetDateTime uploadedAt,
        @NotNull Long testId,
        @Valid @NotEmpty List<V2SyncResultUploadRequest> results
) {
}
