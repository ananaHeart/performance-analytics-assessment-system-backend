package com.capstone.assessment.v3.mobile.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.capstone.assessment.v3.mobile.dto.V3VerificationBatch.PageDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata.UUID_PATTERN;

/** Explicit 3.1 written contract; the 3.0 objective DTO is unchanged. */
public record V3WrittenVerificationBatch(
        @NotNull @Pattern(regexp="3\\.1") String contractVersion,
        @NotNull @Pattern(regexp=UUID_PATTERN) String syncUuid,
        @NotNull @Pattern(regexp=UUID_PATTERN) String operationUuid,
        @NotNull @Pattern(regexp=UUID_PATTERN) String assignmentUuid,
        @NotNull @Size(min=1,max=50) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull @Pattern(regexp=UUID_PATTERN) String resultUuid,
            @NotNull @Min(1) @Max(9007199254740991L) Long expectedRevision,
            @NotNull @Min(1) Integer testVersionNumber,
            @NotNull @Pattern(regexp="[0-9a-f]{64}") String evaluationReferenceHash,
            @NotNull @Size(max=200) List<@NotNull @Valid PageDecision> pageDecisions,
            @NotNull @Size(min=1,max=200) List<@NotNull @Valid Answer> answers) { }
    public record Answer(@NotNull @Pattern(regexp=UUID_PATTERN) String answerUuid,
            @NotNull @Pattern(regexp=UUID_PATTERN) String verificationUuid,
            @NotNull @Pattern(regexp=UUID_PATTERN) String questionUuid,
            @NotNull @Pattern(regexp=UUID_PATTERN) String regionUuid,
            @NotNull @Pattern(regexp=UUID_PATTERN) String scanPageUuid,
            @NotNull @Valid Evaluation evaluation, @Size(max=4000) String comment,@NotNull Instant clientDecidedAt) { }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="kind")
    @JsonSubTypes({@JsonSubTypes.Type(value=Manual.class,name="manual"),@JsonSubTypes.Type(value=Rubric.class,name="rubric")})
    public sealed interface Evaluation permits Manual,Rubric {
        String answerStatus();String responseText();List<String> attachmentUuids();
    }
    public record Manual(@NotNull @Pattern(regexp="answered|blank") String answerStatus,
            @Size(max=10000) String responseText,
            @NotNull @Size(max=20) List<@NotNull @Pattern(regexp=UUID_PATTERN) String> attachmentUuids,
            @NotNull @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer=6,fraction=2) BigDecimal points) implements Evaluation { }
    public record Rubric(@NotNull @Pattern(regexp="answered|blank") String answerStatus,
            @Size(max=10000) String responseText,
            @NotNull @Size(max=20) List<@NotNull @Pattern(regexp=UUID_PATTERN) String> attachmentUuids,
            @NotNull @Min(1) @Max(9007199254740991L) Long rubricId,
            @NotNull @Size(min=1,max=100) List<@NotNull @Valid CriterionScore> criterionScores) implements Evaluation { }
    public record CriterionScore(@NotNull @Min(1) @Max(9007199254740991L) Long rubricCriterionId,
            @NotNull @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer=6,fraction=2) BigDecimal pointsAwarded,
            @Size(max=4000) String comment) { }
}
