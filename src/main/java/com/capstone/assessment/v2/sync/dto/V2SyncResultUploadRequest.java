package com.capstone.assessment.v2.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;
import java.util.List;

public record V2SyncResultUploadRequest(
        @NotBlank String resultUuid,
        @NotBlank String syncAction,
        @NotNull Long classListId,
        @NotNull @Positive Integer attemptNumber,
        @NotNull OffsetDateTime checkedAt,
        @Valid V2SyncScanSessionUploadRequest scanSession,
        @Valid @NotEmpty List<V2SyncAnswerUploadRequest> answers
) {
}
