package com.capstone.assessment.v2.assessment.service;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentPartResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentQuestionResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2QuestionnairePdfServiceTest {

    private final V2QuestionnairePdfService pdfService = new V2QuestionnairePdfService();

    @Test
    void rendersMultiplePartsWithoutAnswerKeys() throws Exception {
        byte[] pdf = pdfService.renderQuestionnaire(assessment("active", 4, false));

        assertTrue(pdf.length > 1_000);
        assertEquals("%PDF", new String(pdf, 0, 4));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Science Quiz 1"));
            assertTrue(text.contains("Grade 10 - Acacia"));
            assertTrue(text.contains("Part I"));
            assertTrue(text.contains("Part II"));
            assertTrue(text.contains("Question 1"));
            assertTrue(text.contains("Question 4"));
            assertTrue(text.contains("Option A"));
            assertFalse(text.contains("Answer Key"));
            assertFalse(text.contains("Correct answer"));
        }
    }

    @Test
    void paginatesLongQuestionnaire() throws Exception {
        byte[] pdf = pdfService.renderQuestionnaire(assessment("active", 40, true));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Question 40"));
            assertTrue(text.contains("Page 1 of"));
        }
    }

    @Test
    void rejectsDraftAssessment() {
        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> pdfService.renderQuestionnaire(assessment("draft", 2, false))
        );

        assertEquals("ASSESSMENT_NOT_ACTIVE", exception.getCode());
    }

    private V2AssessmentResponse assessment(String status, int totalQuestions, boolean longText) {
        int firstPartSize = Math.max(1, totalQuestions / 2);
        List<V2AssessmentPartResponse> parts = new ArrayList<>();
        int nextQuestion = 1;
        for (int partIndex = 0; partIndex < 2; partIndex++) {
            int partSize = partIndex == 0 ? firstPartSize : totalQuestions - firstPartSize;
            if (partSize == 0) {
                continue;
            }
            List<V2AssessmentQuestionResponse> questions = new ArrayList<>();
            for (int itemNumber = 1; itemNumber <= partSize; itemNumber++) {
                String suffix = longText
                        ? " Explain the scientific relationship represented by the observation and select the best conclusion."
                        : "";
                questions.add(new V2AssessmentQuestionResponse(
                        1_000L + nextQuestion,
                        itemNumber,
                        "Question " + nextQuestion + suffix,
                        "Option A",
                        "Option B",
                        "Option C",
                        "Option D",
                        null,
                        "B",
                        List.of(100L)
                ));
                nextQuestion++;
            }
            parts.add(new V2AssessmentPartResponse(
                    200L + partIndex,
                    partIndex + 1,
                    "Part " + (partIndex == 0 ? "I" : "II"),
                    "multiple_choice",
                    partSize,
                    BigDecimal.ONE,
                    questions
            ));
        }

        return new V2AssessmentResponse(
                1006L,
                500L,
                300L,
                1,
                "2025-2026",
                10,
                "Grade 10",
                4,
                "Acacia",
                3,
                "Science",
                11,
                "First Quarter",
                "Science Quiz 1",
                "quiz",
                LocalDate.of(2026, 8, 27),
                "Choose the correct answer.",
                totalQuestions,
                status,
                Instant.parse("2026-08-24T02:00:00Z"),
                Instant.parse("2026-08-24T02:00:00Z"),
                parts
        );
    }
}
