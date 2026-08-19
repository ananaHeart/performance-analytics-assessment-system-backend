package com.capstone.assessment.v2.assessment.service;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentPartResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentQuestionResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2OmrSheetPrintServiceTest {

    private final V2OmrSheetPrintService printService = new V2OmrSheetPrintService();

    @Test
    void rendersActiveTenItemMultipleChoiceSheet() {
        String html = printService.renderSheet(assessment("active", 10, "multiple_choice"), 501L);

        assertTrue(html.contains("OMR-A4-10-MC-CTX-V2"));
        assertTrue(html.contains("&quot;cl&quot;:501"));
        assertTrue(html.contains("QID 9001"));
        assertTrue(html.contains("<span class=\"bubble\">A</span>"));
        assertTrue(html.contains("<span class=\"bubble\">E</span>"));
    }

    @Test
    void rejectsDraftAssessment() {
        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> printService.renderSheet(assessment("draft", 10, "multiple_choice"), 501L)
        );

        assertEquals("ASSESSMENT_NOT_ACTIVE", exception.getCode());
    }

    @Test
    void rejectsUnsupportedItemCount() {
        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> printService.renderSheet(assessment("active", 9, "multiple_choice"), 501L)
        );

        assertEquals("UNSUPPORTED_OMR_ITEM_COUNT", exception.getCode());
    }

    @Test
    void rejectsUnsupportedPartType() {
        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> printService.renderSheet(assessment("active", 10, "true_false"), 501L)
        );

        assertEquals("UNSUPPORTED_OMR_PART_TYPE", exception.getCode());
    }

    private V2AssessmentResponse assessment(String status, int itemCount, String partType) {
        List<V2AssessmentQuestionResponse> questions = new ArrayList<>();
        for (int item = 1; item <= itemCount; item++) {
            questions.add(new V2AssessmentQuestionResponse(
                    9000L + item,
                    item,
                    "Question " + item,
                    "A",
                    "B",
                    "C",
                    "D",
                    null,
                    "A",
                    List.of(100L)
            ));
        }
        return new V2AssessmentResponse(
                101L,
                500L,
                300L,
                1,
                "2026-2027",
                7,
                "Grade 7",
                4,
                "Section A",
                3,
                "Mathematics",
                11,
                "First Quarter",
                "Quiz 1",
                "quiz",
                LocalDate.of(2026, 8, 20),
                "Shade only one answer.",
                itemCount,
                status,
                Instant.parse("2026-08-11T02:00:00Z"),
                Instant.parse("2026-08-11T02:00:00Z"),
                List.of(new V2AssessmentPartResponse(
                        201L,
                        1,
                        "Part I",
                        partType,
                        itemCount,
                        BigDecimal.ONE,
                        questions
                ))
        );
    }
}
