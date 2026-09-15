package com.capstone.assessment.v3.mobile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import static com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata.UUID_PATTERN;

/** Objective-only implementation of the proposed verification batch. Written evaluations remain separate work. */
public record V3VerificationBatch(
        @NotNull @Pattern(regexp="3\\.0") String contractVersion,
        @NotNull @Pattern(regexp=UUID_PATTERN) String syncUuid,
        @NotNull @Pattern(regexp=UUID_PATTERN) String operationUuid,
        @NotNull @Pattern(regexp=UUID_PATTERN) String assignmentUuid,
        @NotNull @Size(min=1,max=50) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull @Pattern(regexp=UUID_PATTERN) String resultUuid,
            @NotNull @Min(1) @Max(9007199254740991L) Long expectedRevision,
            @NotNull @Size(max=200) List<@NotNull @Valid PageDecision> pageDecisions,
            @NotNull @Size(max=200) List<@NotNull @Valid Answer> answers) { }
    public record PageDecision(@NotNull @Pattern(regexp=UUID_PATTERN) String verificationUuid,
            @NotNull @Pattern(regexp=UUID_PATTERN) String scanPageUuid,
            @NotNull @Pattern(regexp="accepted|rescan_requested|rejected") String action,
            @Size(min=1,max=50) String reasonCode, @Size(max=4000) String comment,
            @NotNull Instant clientDecidedAt) { }
    public record Answer(@NotNull @Pattern(regexp=UUID_PATTERN) String answerUuid,
            @NotNull @Pattern(regexp=UUID_PATTERN) String verificationUuid,
            @NotNull @Pattern(regexp=UUID_PATTERN) String questionUuid,
            @NotNull @Pattern(regexp=UUID_PATTERN) String regionUuid,
            @NotNull @Pattern(regexp=UUID_PATTERN) String scanPageUuid,
            @NotNull @Valid ObjectiveEvaluation evaluation,
            @Size(max=4000) String comment, @NotNull Instant clientDecidedAt) { }
    public record ObjectiveEvaluation(@NotNull @Pattern(regexp="objective") String kind,
            @NotNull @Pattern(regexp=UUID_PATTERN) String detectionUuid) { }
}
