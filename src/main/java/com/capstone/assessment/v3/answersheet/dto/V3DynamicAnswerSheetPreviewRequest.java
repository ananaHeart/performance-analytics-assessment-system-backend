package com.capstone.assessment.v3.answersheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Request body for the dynamic mixed-question answer-sheet PDF preview endpoint.
 *
 * <p>This is deliberately separate from {@link V3GenerateAnswerSheetRequest}: that one
 * generates and persists a version of the physically validated fixed A4 10-item MC
 * template through {@code V3AnswerSheetService}. This one takes a self-contained
 * question/part payload and returns a PDF straight from the new dynamic packer/renderer
 * (see {@code com.capstone.assessment.v3.answersheet.service.dynamic}), with no database
 * read or write. It exists so a real, physically printable mixed-question sheet can be
 * produced for scanner acceptance testing before the dynamic template is wired into the
 * eligibility/persistence pipeline (a separate, larger piece of work - see
 * docs/V3_DYNAMIC_ANSWER_SHEET_SCHEMA_DELTA_HANDOFF.md).
 */
public record V3DynamicAnswerSheetPreviewRequest(
        @NotBlank(message = "Paper size is required.")
        @Pattern(regexp = "A4|US_LETTER|US_LEGAL", message = "Paper size must be A4, US_LETTER, or US_LEGAL.")
        String paperSizeCode,

        @NotBlank(message = "answerSheetUuid is required.")
        String answerSheetUuid,

        @NotBlank(message = "assignmentUuid is required.")
        String assignmentUuid,

        @Positive(message = "testAssignmentId must be positive.")
        long testAssignmentId,

        @Positive(message = "testVersionNumber must be positive.")
        int testVersionNumber,

        String schoolName,
        String assessmentName,
        String subject,
        String gradeSection,

        @NotEmpty(message = "At least one part is required.")
        @Valid
        List<PartInput> parts
) {

    public record PartInput(
            @Positive long testPartId,
            @Positive int partOrder,
            @NotBlank(message = "partName is required.")
            String partName,
            String instructions,
            @NotEmpty(message = "Each part requires at least one question.")
            @Valid
            List<QuestionInput> questions
    ) {
    }

    public record QuestionInput(
            @Positive long questionId,
            @NotBlank(message = "questionUuid is required.")
            String questionUuid,
            @NotBlank(message = "questionType is required.")
            @Pattern(
                    regexp = "multiple_choice|true_false|identification|enumeration|essay",
                    message = "questionType must be one of multiple_choice, true_false, identification, enumeration, essay."
            )
            String questionType,
            Integer partItemNumber,
            Integer globalItemNumber,
            @NotNull(message = "maximumPoints is required.")
            Double maximumPoints,
            String questionText,
            String responseRegionSize,
            Integer expectedResponseCount,
            boolean forcePageBreakBefore,
            List<OptionInput> options
    ) {
    }

    public record OptionInput(String key, String storedValue) {
    }
}
