package com.capstone.assessment.v2.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record V2SyncAnswerUploadRequest(
        @NotBlank String answerUuid,
        @NotNull Long questionId,
        String selectedOption,
        @NotBlank String answerStatus,
        @NotBlank String captureSource,
        @NotNull OffsetDateTime verifiedAt,
        String correctionReason
) {
}
