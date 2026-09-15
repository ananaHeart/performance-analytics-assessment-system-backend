package com.capstone.assessment.v3.mobile.dto;

import com.fasterxml.jackson.annotation.*;
import com.capstone.assessment.v3.mobile.dto.V3WrittenVerificationBatch.CriterionScore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static com.capstone.assessment.v3.mobile.dto.V3ReopenRequest.UUID;

/** Complete teacher review of one reopened result; corrections and official scoring commit together. */
public record V3CorrectionRequest(
        @NotNull @Pattern(regexp="3\\.2") String contractVersion,
        @NotNull @Pattern(regexp=UUID) String syncUuid,
        @NotNull @Pattern(regexp=UUID) String operationUuid,
        @NotNull @Pattern(regexp=UUID) String reopenOperationUuid,
        @NotNull @Min(1) @Max(9007199254740991L) Long expectedRevision,
        @NotNull @Min(1) @Max(2147483647) Long expectedScoreVersion,
        @NotNull @Min(1) Integer testVersionNumber,
        @NotNull @Pattern(regexp="[0-9a-f]{64}") String evaluationReferenceHash,
        @NotBlank @Size(max=50) String reasonCode,@Size(max=4000) String comment,
        @NotNull @Size(min=1,max=200) List<@NotNull @Valid Answer> answers) {
    public record Answer(@NotNull @Pattern(regexp=UUID) String answerUuid,
            @NotNull @Pattern(regexp=UUID) String verificationUuid,
            @NotNull @Pattern(regexp=UUID) String questionUuid,
            @NotNull @Pattern(regexp=UUID) String regionUuid,
            @NotNull @Pattern(regexp=UUID) String scanPageUuid,
            @NotNull @Valid Evaluation evaluation,@Size(max=4000) String comment,@NotNull Instant clientDecidedAt) { }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="kind")
    @JsonSubTypes({@JsonSubTypes.Type(value=Objective.class,name="objective"),@JsonSubTypes.Type(value=Manual.class,name="manual"),@JsonSubTypes.Type(value=Rubric.class,name="rubric")})
    public sealed interface Evaluation permits Objective,Manual,Rubric { String answerStatus(); }
    public record Objective(@NotNull @Pattern(regexp="answered|blank") String answerStatus,
            @NotNull @Pattern(regexp=UUID) String detectionUuid,@Pattern(regexp="A|B|C|D") String selectedOption) implements Evaluation { }
    public record Manual(@NotNull @Pattern(regexp="answered|blank") String answerStatus,@Size(max=10000) String responseText,
            @NotNull @Size(max=20) List<@NotNull @Pattern(regexp=UUID) String> attachmentUuids,
            @NotNull @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer=6,fraction=2) BigDecimal points) implements Evaluation { }
    public record Rubric(@NotNull @Pattern(regexp="answered|blank") String answerStatus,@Size(max=10000) String responseText,
            @NotNull @Size(max=20) List<@NotNull @Pattern(regexp=UUID) String> attachmentUuids,
            @NotNull @Min(1) @Max(9007199254740991L) Long rubricId,
            @NotNull @Size(min=1,max=100) List<@NotNull @Valid CriterionScore> criterionScores) implements Evaluation { }
    public static V3WrittenVerificationBatch.Evaluation written(Evaluation e){
        if(e instanceof Manual m)return new V3WrittenVerificationBatch.Manual(m.answerStatus(),m.responseText(),m.attachmentUuids(),m.points());
        if(e instanceof Rubric r)return new V3WrittenVerificationBatch.Rubric(r.answerStatus(),r.responseText(),r.attachmentUuids(),r.rubricId(),r.criterionScores());
        throw new IllegalArgumentException("Written evaluation required");
    }
}
