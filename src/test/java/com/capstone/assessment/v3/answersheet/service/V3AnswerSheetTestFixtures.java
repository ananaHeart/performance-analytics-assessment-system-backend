package com.capstone.assessment.v3.answersheet.service;

import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.AssignmentContext;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.GenerationPlan;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.PaperSize;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Question;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Template;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.TemplateRegion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class V3AnswerSheetTestFixtures {

    private V3AnswerSheetTestFixtures() {
    }

    static AssignmentContext activeAssignment() {
        return new AssignmentContext(
                5001L,
                "00000000-0000-4000-8000-000000005001",
                1006L,
                "00000000-0000-4000-8000-000000001006",
                1,
                "English Quiz 2",
                "active",
                10,
                "planned",
                "active",
                "Grade 7",
                "Rizal",
                "English"
        );
    }

    static PaperSize a4() {
        return new PaperSize(
                1,
                "A4",
                "A4",
                new BigDecimal("595.276"),
                new BigDecimal("841.890"),
                true
        );
    }

    static List<Question> tenValidQuestions() {
        List<Question> questions = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            questions.add(new Question(
                    7000L + index,
                    "00000000-0000-4700-8000-%012d".formatted(7000 + index),
                    index <= 5 ? 8001L : 8002L,
                    index <= 5 ? 1 : 2,
                    5,
                    index <= 5 ? index : index - 5,
                    index,
                    1,
                    "multiple_choice",
                    4,
                    List.of("A", "B", "C", "D"),
                    1,
                    1,
                    1
            ));
        }
        return List.copyOf(questions);
    }

    static Template validatedTemplate() {
        ObjectMapper objectMapper = new ObjectMapper();
        List<TemplateRegion> regions = new ArrayList<>();
        regions.add(region(objectMapper, 1, "MARKER_TOP_LEFT", 1, "registration_marker",
                null, null, "20.000", "806.890", "15.000", "15.000",
                "{\"shape\":\"nested_square\",\"inner_inset_ratio\":0.28}"));
        regions.add(region(objectMapper, 2, "MARKER_TOP_RIGHT", 2, "registration_marker",
                null, null, "560.276", "806.890", "15.000", "15.000",
                "{\"shape\":\"nested_square\",\"inner_inset_ratio\":0.28}"));
        regions.add(region(objectMapper, 3, "MARKER_BOTTOM_LEFT", 3, "registration_marker",
                null, null, "20.000", "20.000", "15.000", "15.000",
                "{\"shape\":\"nested_square\",\"inner_inset_ratio\":0.28}"));
        regions.add(region(objectMapper, 4, "MARKER_BOTTOM_RIGHT", 4, "registration_marker",
                null, null, "560.276", "20.000", "15.000", "15.000",
                "{\"shape\":\"nested_square\",\"inner_inset_ratio\":0.28}"));
        regions.add(region(objectMapper, 5, "PAGE_QR", 5, "page_identity",
                null, null, "477.276", "698.890", "83.000", "83.000",
                "{\"error_correction\":\"M\",\"quiet_zone_pt\":6.0}"));

        double[] centers = {
                604.334, 571.223, 538.112, 505.001, 471.890,
                438.778, 405.667, 372.556, 339.445, 306.334
        };
        for (int index = 0; index < centers.length; index++) {
            double centerY = centers[index];
            String geometry = """
                    {"item_number":%d,"option_keys":["A","B","C","D"],
                     "bubble_centers_pt":[[79.0,%.3f],[103.0,%.3f],[127.0,%.3f],[151.0,%.3f]],
                     "bubble_radius_pt":6.4}
                    """.formatted(index + 1, centerY, centerY, centerY, centerY);
            regions.add(region(
                    objectMapper,
                    101 + index,
                    "MC_ITEM_%02d".formatted(index + 1),
                    10 + index,
                    "objective_bubbles",
                    1,
                    "multiple_choice",
                    "72.600",
                    BigDecimal.valueOf(centerY - 6.4).toPlainString(),
                    "84.800",
                    "12.800",
                    geometry
            ));
        }

        return new Template(
                1L,
                V3AnswerSheetService.VALIDATED_TEMPLATE_CODE,
                "Validated A4 10-item Multiple Choice",
                "2",
                1,
                "A4",
                new BigDecimal("595.276"),
                new BigDecimal("841.890"),
                "portrait",
                10,
                10,
                4,
                2,
                "2.0.0",
                "pdf_bottom_left",
                new BigDecimal("100.00"),
                "a".repeat(64),
                List.copyOf(regions)
        );
    }

    static GenerationPlan generationPlan() {
        String qrPayload = V3AnswerSheetService.legacyFixedQrPayload(1006L);
        return new GenerationPlan(
                activeAssignment(),
                a4(),
                validatedTemplate(),
                tenValidQuestions(),
                "00000000-0000-4000-8000-000000009001",
                "00000000-0000-4000-8000-000000009002",
                1,
                qrPayload,
                "b".repeat(64),
                "c".repeat(64),
                "d".repeat(64),
                Instant.parse("2026-09-01T00:00:00Z")
        );
    }

    private static TemplateRegion region(
            ObjectMapper objectMapper,
            long id,
            String code,
            int order,
            String type,
            Integer questionTypeId,
            String questionType,
            String x,
            String y,
            String width,
            String height,
            String geometry
    ) {
        try {
            return new TemplateRegion(
                    id,
                    code,
                    order,
                    type,
                    questionTypeId,
                    questionType,
                    "fixed_context_v2",
                    "objective_bubbles".equals(type) ? "none" : null,
                    new BigDecimal(x),
                    new BigDecimal(y),
                    new BigDecimal(width),
                    new BigDecimal(height),
                    objectMapper.readTree(geometry),
                    geometry,
                    Integer.toHexString(code.hashCode()).repeat(16).substring(0, 64),
                    true
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
