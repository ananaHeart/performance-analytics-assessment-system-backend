package com.capstone.assessment.v3.report.service;

import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse.AcceptedAnswer;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse.AnswerKey;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse.Option;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse.Part;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse.Question;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse.Rubric;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.report.dto.V3ReportReferenceDataResponse;
import com.capstone.assessment.v3.report.repository.V3ReportRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V3QuestionnairePdfRendererTest {

    private static final V3AuthenticatedUser TEACHER = new V3AuthenticatedUser(
            42L, "SCHOOL-001", "teacher@example.com", "teacher", "active", "session");
    private static final String SECRET = "KEYSECRET";

    private final V3ReportRepository repository = mock(V3ReportRepository.class);
    private final V3QuestionnairePdfRenderer renderer = new V3QuestionnairePdfRenderer(repository);

    @Test
    void printsEveryQuestionNumberedLikeTheAnswerSheetAndNoAnswers() throws Exception {
        when(repository.findSchool("SCHOOL-001")).thenReturn(Optional.of(
                new V3ReportReferenceDataResponse.SchoolOption("SCHOOL-001", "San Roque National High School")));
        when(repository.findTeacherName(42L)).thenReturn(Optional.of("JUAN DELA CRUZ"));

        byte[] pdf = renderer.render(TEACHER, sampleAssessment());
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "questionnaire-sample.pdf"), pdf);

        String text;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text).contains("TEST QUESTIONNAIRE", "SAN ROQUE NATIONAL HIGH SCHOOL", "Juan Dela Cruz",
                "Sep 15 – 17, 2026", "This test has 11 items worth 21 pts in 5 parts");
        // Numbered 1..11 across parts, in part order then item order, as on the answer sheet.
        for (int number = 1; number <= 11; number++) {
            assertThat(text).contains(number + ". ");
        }
        assertThat(text).contains("A. Chlorophyll", "B. False", "(Give 3.)");
        assertThat(text).doesNotContain(SECRET);
    }

    @Test
    void anAssessmentWithoutQuestionsCannotBePrinted() {
        V3AssessmentResponse empty = assessment(List.of());

        assertThatThrownBy(() -> renderer.render(TEACHER, empty))
                .isInstanceOfSatisfying(V3AuthException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ASSESSMENT_HAS_NO_QUESTIONS"));
    }

    private static V3AssessmentResponse sampleAssessment() {
        List<Part> parts = new ArrayList<>();
        // Listed out of order on purpose: parts print by part order.
        parts.add(part(2, "Part II", "true_false", "True or False", null, List.of(
                question(1, "Plants need sunlight to make their own food.", "1", tf()),
                question(2, "The mitochondria is where photosynthesis happens.", "1", tf()))));
        parts.add(part(1, "Part I", "multiple_choice", "Multiple Choice",
                "Choose the letter of the correct answer.", List.of(
                question(2, "Which gas do plants take in during photosynthesis?", "1",
                        mc("Oxygen", "Carbon dioxide", "Nitrogen", "Hydrogen")),
                question(1, "What gives leaves their green color?", "1",
                        mc("Chlorophyll", "Carotene", "Melanin", "Hemoglobin")),
                question(3, "Which part of the plant absorbs most of the water and minerals the plant needs "
                                + "from the soil, especially during long dry seasons when rain is scarce?", "1",
                        mc("The leaves, through tiny openings on their underside",
                                "The roots, through root hairs that reach into the soil",
                                "The flowers, through their petals and nectar",
                                "The stem, through its outer bark only")))));
        parts.add(part(3, "Part III", "identification", "Identification", null, List.of(
                question(1, "The process by which plants make food using light energy.", "1", List.of()),
                question(2, "The green pigment found in chloroplasts.", "1", List.of()),
                question(3, "The tiny openings on leaves where gases are exchanged.", "1", List.of()))));
        parts.add(part(4, "Part IV", "enumeration", "Enumeration", null, List.of(
                withExpected(question(1, "List the three raw materials of photosynthesis.", "3", List.of()), 3))));
        parts.add(part(5, "Part V", "essay", "Essay", null, List.of(
                question(1, "Explain why photosynthesis is important to animals and people.", "5", List.of()),
                question(2, "Describe one way people can protect forests in their community.", "5", List.of()))));
        return assessment(parts);
    }

    private static V3AssessmentResponse assessment(List<Part> parts) {
        return new V3AssessmentResponse(1006L, "test-uuid", 5001L, "assignment-uuid", 9L, 2, "Term 2",
                "Science Quiz 3: Photosynthesis", "quiz", "Use a black pen or pencil.", 11, new BigDecimal("21"),
                "active", 1, Instant.parse("2026-09-15T00:00:00Z"), Instant.parse("2026-09-17T08:00:00Z"),
                "open", false, false, null, null, null,
                new V3AssessmentResponse.AssignmentContext(3L, 1, "2026-2027", 7, "Grade 7", "Rizal", 4, "Science"),
                parts, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
    }

    private static Part part(int order, String name, String typeCode, String typeName, String instructions,
            List<Question> questions) {
        return new Part(order, order, name, typeCode, typeName, questions.size(), BigDecimal.ONE, instructions,
                questions, List.of());
    }

    private static Question question(int item, String text, String points, List<Option> options) {
        return new Question(item, "q-" + item, item, text, new BigDecimal(points), null, null, null, null,
                false, false, options, new AnswerKey("option", "A", "exact", SECRET + " explanation"),
                List.of(new AcceptedAnswer(1L, 1, SECRET + " accepted", "exact", false, BigDecimal.ONE, true)),
                new Rubric(1L, "r", SECRET + " rubric", SECRET, BigDecimal.ONE, "active", List.of()));
    }

    private static Question withExpected(Question base, int expected) {
        return new Question(base.questionId(), base.questionUuid(), base.itemNumber(), base.questionText(),
                base.maximumPoints(), base.responseInstructions(), base.maximumResponseLength(), expected,
                base.responseRegionSize(), base.forcePageBreakBefore(), base.answerOrderRequired(), base.options(),
                base.answerKey(), base.acceptedAnswers(), base.rubric());
    }

    private static List<Option> mc(String a, String b, String c, String d) {
        return List.of(new Option(1, "A", a, 1), new Option(2, "B", b, 2),
                new Option(3, "C", c, 3), new Option(4, "D", d, 4));
    }

    private static List<Option> tf() {
        return List.of(new Option(1, "A", "True", 1), new Option(2, "B", "False", 2));
    }
}
