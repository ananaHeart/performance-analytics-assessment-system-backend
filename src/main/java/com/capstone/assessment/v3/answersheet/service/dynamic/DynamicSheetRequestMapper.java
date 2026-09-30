package com.capstone.assessment.v3.answersheet.service.dynamic;

import com.capstone.assessment.v3.answersheet.dto.V3DynamicAnswerSheetPreviewRequest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicOptionInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicPartInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicQuestionInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetContext;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts the HTTP-facing {@link V3DynamicAnswerSheetPreviewRequest} into the packer's
 * internal {@link DynamicSheetRequest}, filling in sequential item numbering when the
 * caller omits it. Kept separate from the controller so it can be unit tested without a
 * Spring context.
 */
public final class DynamicSheetRequestMapper {

    private DynamicSheetRequestMapper() {
    }

    public static DynamicSheetRequest toPackerRequest(V3DynamicAnswerSheetPreviewRequest request) {
        List<DynamicPartInput> parts = new ArrayList<>();
        int globalCounter = 0;
        for (V3DynamicAnswerSheetPreviewRequest.PartInput partInput : request.parts()) {
            List<DynamicQuestionInput> questions = new ArrayList<>();
            int partItemCounter = 0;
            for (V3DynamicAnswerSheetPreviewRequest.QuestionInput questionInput : partInput.questions()) {
                globalCounter++;
                partItemCounter++;
                int globalItemNumber = questionInput.globalItemNumber() != null
                        ? questionInput.globalItemNumber() : globalCounter;
                int partItemNumber = questionInput.partItemNumber() != null
                        ? questionInput.partItemNumber() : partItemCounter;

                List<DynamicOptionInput> options = new ArrayList<>();
                String type = questionInput.questionType();
                if ("true_false".equals(type)) {
                    options.add(new DynamicOptionInput("T", "A"));
                    options.add(new DynamicOptionInput("F", "B"));
                } else if ("multiple_choice".equals(type)) {
                    if (questionInput.options() != null && !questionInput.options().isEmpty()) {
                        for (V3DynamicAnswerSheetPreviewRequest.OptionInput option : questionInput.options()) {
                            options.add(new DynamicOptionInput(option.key(), option.storedValue()));
                        }
                    } else {
                        options.add(new DynamicOptionInput("A", "A"));
                        options.add(new DynamicOptionInput("B", "B"));
                        options.add(new DynamicOptionInput("C", "C"));
                        options.add(new DynamicOptionInput("D", "D"));
                    }
                }

                String responseRegionSize = questionInput.responseRegionSize();
                if (responseRegionSize == null || responseRegionSize.isBlank()) {
                    responseRegionSize = switch (type) {
                        case "identification" -> "short";
                        case "enumeration" -> "medium";
                        case "essay" -> "long";
                        default -> null;
                    };
                }

                questions.add(new DynamicQuestionInput(
                        questionInput.questionId(),
                        questionInput.questionUuid(),
                        type,
                        partItemNumber,
                        globalItemNumber,
                        questionInput.maximumPoints(),
                        questionInput.questionText() == null ? "" : questionInput.questionText(),
                        responseRegionSize,
                        questionInput.expectedResponseCount(),
                        questionInput.forcePageBreakBefore(),
                        options
                ));
            }
            parts.add(new DynamicPartInput(
                    partInput.testPartId(), partInput.partOrder(), partInput.partName(),
                    partInput.instructions(), questions));
        }

        double totalPoints = parts.stream()
                .flatMap(part -> part.questions().stream())
                .mapToDouble(DynamicQuestionInput::maximumPoints)
                .sum();
        DynamicSheetContext context = new DynamicSheetContext(
                request.schoolName(), request.assessmentName(), request.subject(), request.gradeSection(), totalPoints);

        return new DynamicSheetRequest(
                request.answerSheetUuid(), request.testAssignmentId(), request.assignmentUuid(),
                request.paperSizeCode(), request.testVersionNumber(),
                DynamicSheetPacker.DEFAULT_SCANNER_VERSION,
                java.time.Instant.now().toString(),
                parts, context
        );
    }
}
