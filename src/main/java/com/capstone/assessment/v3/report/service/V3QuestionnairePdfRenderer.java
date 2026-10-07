package com.capstone.assessment.v3.report.service;

import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.report.repository.V3ReportRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The printable Test Questionnaire: questions and lettered choices only, never answer keys,
 * accepted answers or rubrics. Learners answer on the Marka answer sheet, so items are numbered
 * 1..N across parts exactly as that sheet numbers them (part order, then item order).
 */
@Profile("v3")
@Component
public class V3QuestionnairePdfRenderer {

    private static final String FOOTER =
            "Do not write on this questionnaire. Shade or write all answers on the Marka answer sheet.";

    private final V3ReportRepository repository;

    public V3QuestionnairePdfRenderer(V3ReportRepository repository) {
        this.repository = repository;
    }

    /** {@code assessment} must already be the caller's own assessment (V3AssessmentService checks it). */
    public byte[] render(V3AuthenticatedUser teacher, V3AssessmentResponse assessment) {
        List<V3AssessmentResponse.Part> parts = assessment.parts() == null ? List.of() : assessment.parts().stream()
                .filter(part -> part.questions() != null && !part.questions().isEmpty())
                .sorted(Comparator.comparingInt(V3AssessmentResponse.Part::partOrder))
                .toList();
        if (parts.isEmpty()) {
            throw new V3AuthException(
                    "ASSESSMENT_HAS_NO_QUESTIONS",
                    "Add questions to the assessment before printing its questionnaire.",
                    HttpStatus.CONFLICT
            );
        }
        String schoolName = repository.findSchool(teacher.schoolId())
                .map(school -> school.schoolName()).orElse(null);
        String teacherName = repository.findUserFullName(teacher.userId()).orElse(null);
        try {
            ReportPdfDocument pdf = new ReportPdfDocument(schoolName, "TEST QUESTIONNAIRE", FOOTER);
            writeDetails(pdf, assessment, teacherName);
            pdf.gap(10);
            pdf.directions("General Directions", generalDirections(assessment, parts));

            int number = 1;
            for (V3AssessmentResponse.Part part : parts) {
                pdf.gap(14);
                pdf.section(null, partLabel(part));
                pdf.paragraph(partDirections(part), ReportPdfDocument.ITALIC, 9.2f, ReportPdfDocument.TEXT);
                pdf.gap(4);
                boolean uniformPoints = uniformPoints(part) != null;
                for (V3AssessmentResponse.Question question : part.questions().stream()
                        .sorted(Comparator.comparingInt(V3AssessmentResponse.Question::itemNumber)).toList()) {
                    pdf.question(number++ + ".", questionText(part, question, uniformPoints),
                            question.responseInstructions(), choices(question));
                }
            }
            return pdf.finish();
        } catch (IOException exception) {
            throw new V3AuthException(
                    "QUESTIONNAIRE_PDF_GENERATION_FAILED",
                    "The test questionnaire could not be generated.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private static void writeDetails(ReportPdfDocument pdf, V3AssessmentResponse assessment, String teacherName)
            throws IOException {
        V3AssessmentResponse.AssignmentContext context = assessment.assignment();
        String grade = context == null ? null : context.gradeLevelName();
        String section = context == null ? null : context.sectionName();
        String gradeSection = grade == null || grade.isBlank() ? section
                : section == null || section.isBlank() ? grade : grade + " - " + section;
        List<String[]> left = new ArrayList<>();
        left.add(new String[]{"Assessment", orNotAvailable(assessment.testName())});
        left.add(new String[]{"Type", orNotAvailable(typeLabel(assessment.testType()))});
        left.add(new String[]{"Subject", orNotAvailable(context == null ? null : context.subjectName())});
        left.add(new String[]{"Grade & Section", orNotAvailable(gradeSection)});
        List<String[]> right = new ArrayList<>();
        right.add(new String[]{"Prepared By", orNotAvailable(V3ReportExportService.personName(teacherName))});
        right.add(new String[]{"School Year", orNotAvailable(context == null ? null : context.academicYearName())});
        right.add(new String[]{"Term", orNotAvailable(assessment.termName())});
        right.add(new String[]{"Date Conducted",
                V3ReportExportService.dateConducted(assessment.openAt(), assessment.closeAt())});
        pdf.section(null, "Assessment Details");
        pdf.details(left, right);
    }

    private static String generalDirections(V3AssessmentResponse assessment, List<V3AssessmentResponse.Part> parts) {
        int items = parts.stream().mapToInt(part -> part.questions().size()).sum();
        BigDecimal points = parts.stream()
                .flatMap(part -> part.questions().stream())
                .map(question -> question.maximumPoints() == null ? BigDecimal.ZERO : question.maximumPoints())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder text = new StringBuilder("This test has ")
                .append(items).append(items == 1 ? " item" : " items")
                .append(" worth ").append(points(points, false))
                .append(" in ").append(parts.size()).append(parts.size() == 1 ? " part" : " parts")
                .append(". Read the directions of each part carefully. Do not write on this questionnaire; "
                        + "shade or write all your answers on the Marka answer sheet.");
        if (assessment.instructions() != null && !assessment.instructions().isBlank()) {
            text.append(' ').append(assessment.instructions().trim());
        }
        return text.toString();
    }

    /** "Part 1 - Multiple Choice (10 items, 1 pt each)", without repeating a name equal to the type. */
    private static String partLabel(V3AssessmentResponse.Part part) {
        String name = part.partName() == null || part.partName().isBlank()
                ? "Part " + part.partOrder() : part.partName().trim();
        String type = part.questionTypeName();
        StringBuilder label = new StringBuilder(name);
        if (type != null && !type.isBlank() && !name.toLowerCase(Locale.ROOT).contains(type.toLowerCase(Locale.ROOT))) {
            label.append(" - ").append(type.trim());
        }
        int items = part.questions().size();
        label.append(" (").append(items).append(items == 1 ? " item" : " items");
        BigDecimal each = uniformPoints(part);
        if (each != null) {
            label.append(", ").append(points(each, items > 1));
        }
        return label.append(')').toString();
    }

    private static String partDirections(V3AssessmentResponse.Part part) {
        if (part.partInstructions() != null && !part.partInstructions().isBlank()) {
            return "Directions: " + part.partInstructions().trim();
        }
        String type = part.questionTypeCode() == null ? "" : part.questionTypeCode().toLowerCase(Locale.ROOT);
        return "Directions: " + switch (type) {
            case "multiple_choice" ->
                    "Read each question carefully. Choose the letter of the best answer and shade it on your answer sheet.";
            case "true_false" ->
                    "Decide whether each statement is true or false. Shade the letter of your answer on your answer sheet.";
            case "identification" ->
                    "Identify what is being described. Write your answer in its numbered box on your answer sheet.";
            case "enumeration" ->
                    "Give what is asked. Write your answers in their numbered box on your answer sheet.";
            case "essay" ->
                    "Answer in complete sentences. Write your answer in its numbered space on your answer sheet.";
            default -> "Answer each item on your answer sheet.";
        };
    }

    private static String questionText(V3AssessmentResponse.Part part, V3AssessmentResponse.Question question,
            boolean uniformPoints) {
        StringBuilder text = new StringBuilder(orNotAvailable(question.questionText()).trim());
        if ("enumeration".equalsIgnoreCase(part.questionTypeCode())
                && question.expectedResponseCount() != null && question.expectedResponseCount() > 1) {
            text.append(" (Give ").append(question.expectedResponseCount()).append(".)");
        }
        if (!uniformPoints && question.maximumPoints() != null) {
            text.append(" (").append(points(question.maximumPoints(), false)).append(')');
        }
        return text.toString();
    }

    /** "A. Photosynthesis" in option order; empty for written answers. */
    private static List<String> choices(V3AssessmentResponse.Question question) {
        if (question.options() == null) {
            return List.of();
        }
        return question.options().stream()
                .sorted(Comparator.comparingInt(V3AssessmentResponse.Option::optionOrder))
                .map(option -> option.optionKey() + ". " + orNotAvailable(option.optionText()).trim())
                .toList();
    }

    /** The points every question in the part shares, or null when they differ. */
    private static BigDecimal uniformPoints(V3AssessmentResponse.Part part) {
        BigDecimal shared = null;
        for (V3AssessmentResponse.Question question : part.questions()) {
            BigDecimal points = question.maximumPoints();
            if (points == null || (shared != null && shared.compareTo(points) != 0)) {
                return null;
            }
            shared = points;
        }
        return shared;
    }

    /** "1 pt", "5 pts", "1.5 pts"; with each=true, "1 pt each", as on the answer sheet. */
    private static String points(BigDecimal value, boolean each) {
        BigDecimal normalized = value.stripTrailingZeros();
        String number = normalized.scale() <= 0 ? normalized.toBigInteger().toString() : normalized.toPlainString();
        String unit = normalized.compareTo(BigDecimal.ONE) == 0 ? " pt" : " pts";
        return number + unit + (each ? " each" : "");
    }

    /** "quiz" -> "Quiz", "long_test" -> "Long Test". */
    private static String typeLabel(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        StringBuilder label = new StringBuilder();
        for (String word : code.trim().toLowerCase(Locale.ROOT).split("[_\\s]+")) {
            if (!word.isEmpty()) {
                label.append(label.isEmpty() ? "" : " ")
                        .append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return label.toString();
    }

    private static String orNotAvailable(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
