package com.capstone.assessment.v2.assessment.service;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentPartResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentQuestionResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Profile("v2")
@Service
public class V2QuestionnairePdfService {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 42f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);
    private static final float BOTTOM_MARGIN = 38f;
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH);
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    public byte[] renderQuestionnaire(V2AssessmentResponse assessment) {
        validatePrintableAssessment(assessment);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            QuestionnaireWriter writer = new QuestionnaireWriter(document, assessment.testName());
            writer.startFirstPage();
            writer.writeAssessmentHeader(assessment);

            int questionNumber = 1;
            for (V2AssessmentPartResponse part : sortedParts(assessment.parts())) {
                writer.writePartHeader(part);
                for (V2AssessmentQuestionResponse question : sortedQuestions(part.questions())) {
                    writer.writeQuestion(questionNumber++, part.partType(), question);
                }
            }
            writer.closeCurrentPage();
            addPageNumbers(document);

            document.getDocumentInformation().setTitle(safePdfText(assessment.testName()) + " - Test Questionnaire");
            document.getDocumentInformation().setAuthor("SMART Assessment System");
            document.getDocumentInformation().setSubject("Printable assessment questionnaire without answer keys");
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new V2AuthException(
                    "QUESTIONNAIRE_PDF_GENERATION_FAILED",
                    "The Test Questionnaire could not be generated.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private void validatePrintableAssessment(V2AssessmentResponse assessment) {
        if (assessment == null) {
            throw new V2AuthException(
                    "ASSESSMENT_NOT_FOUND",
                    "Assessment not found.",
                    HttpStatus.NOT_FOUND
            );
        }
        if (!"active".equalsIgnoreCase(assessment.status())) {
            throw new V2AuthException(
                    "ASSESSMENT_NOT_ACTIVE",
                    "Activate the assessment before generating its Test Questionnaire.",
                    HttpStatus.CONFLICT
            );
        }
        if (assessment.parts() == null || assessment.parts().isEmpty()
                || assessment.parts().stream().allMatch(part -> part.questions() == null || part.questions().isEmpty())) {
            throw new V2AuthException(
                    "ASSESSMENT_HAS_NO_QUESTIONS",
                    "The assessment has no questions to print.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private List<V2AssessmentPartResponse> sortedParts(List<V2AssessmentPartResponse> parts) {
        return parts.stream()
                .sorted(Comparator.comparing(
                        V2AssessmentPartResponse::partOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    private List<V2AssessmentQuestionResponse> sortedQuestions(List<V2AssessmentQuestionResponse> questions) {
        if (questions == null) {
            return List.of();
        }
        return questions.stream()
                .sorted(Comparator.comparing(
                        V2AssessmentQuestionResponse::itemNumber,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    private void addPageNumbers(PDDocument document) throws IOException {
        int totalPages = document.getNumberOfPages();
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            PDPage page = document.getPage(pageIndex);
            try (PDPageContentStream content = new PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
            )) {
                String label = "Page %d of %d".formatted(pageIndex + 1, totalPages);
                float width = textWidth(FONT_REGULAR, 8, label);
                drawText(content, FONT_REGULAR, 8, PAGE_WIDTH - MARGIN - width, 20, label);
            }
        }
    }

    private static String displayPartType(String partType) {
        if (partType == null || partType.isBlank()) {
            return "Questions";
        }
        return switch (partType.toLowerCase(Locale.ROOT)) {
            case "multiple_choice" -> "Multiple Choice";
            case "true_false" -> "True or False";
            default -> partType.replace('_', ' ');
        };
    }

    private static String displayPoints(BigDecimal pointsPerItem) {
        if (pointsPerItem == null) {
            return "";
        }
        BigDecimal normalized = pointsPerItem.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        String unit = normalized.compareTo(BigDecimal.ONE) == 0 ? "point" : "points";
        return "%s %s per item".formatted(normalized.toPlainString(), unit);
    }

    private static String safePdfText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2026', '.');
        StringBuilder safe = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            if (WINDOWS_1252.newEncoder().canEncode(character)) {
                safe.append(character);
            } else {
                safe.append('?');
            }
        });
        return safe.toString();
    }

    private static float textWidth(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(safePdfText(text)) / 1000f * fontSize;
    }

    private static void drawText(
            PDPageContentStream content,
            PDFont font,
            float fontSize,
            float x,
            float y,
            String text
    ) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(safePdfText(text));
        content.endText();
    }

    private static List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth)
            throws IOException {
        String normalized = safePdfText(text).trim();
        if (normalized.isEmpty()) {
            return List.of("");
        }

        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (textWidth(font, fontSize, candidate) <= maxWidth) {
                currentLine.setLength(0);
                currentLine.append(candidate);
                continue;
            }

            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
            }
            if (textWidth(font, fontSize, word) <= maxWidth) {
                currentLine.append(word);
                continue;
            }

            StringBuilder fragment = new StringBuilder();
            for (int index = 0; index < word.length(); index++) {
                String fragmentCandidate = fragment.toString() + word.charAt(index);
                if (!fragment.isEmpty() && textWidth(font, fontSize, fragmentCandidate) > maxWidth) {
                    lines.add(fragment.toString());
                    fragment.setLength(0);
                }
                fragment.append(word.charAt(index));
            }
            currentLine.append(fragment);
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private final class QuestionnaireWriter {

        private final PDDocument document;
        private final String assessmentName;
        private PDPageContentStream content;
        private float y;

        private QuestionnaireWriter(PDDocument document, String assessmentName) {
            this.document = document;
            this.assessmentName = safeValue(assessmentName, "Assessment");
        }

        private void startFirstPage() throws IOException {
            startPage(false);
        }

        private void startPage(boolean continuation) throws IOException {
            closeCurrentPage();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
            if (continuation) {
                drawText(content, FONT_BOLD, 10, MARGIN, y, assessmentName + " - continued");
                y -= 18;
                drawRule();
                y -= 15;
            }
        }

        private void closeCurrentPage() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }

        private void writeAssessmentHeader(V2AssessmentResponse assessment) throws IOException {
            drawCentered(FONT_BOLD, 11, "SMART ASSESSMENT SYSTEM");
            y -= 7;
            drawCentered(FONT_BOLD, 17, safeValue(assessment.testName(), "Test Questionnaire"));
            y -= 18;

            float rightColumn = PAGE_WIDTH / 2 + 12;
            drawText(content, FONT_BOLD, 9.5f, MARGIN, y, "Class:");
            drawText(content, FONT_REGULAR, 9.5f, MARGIN + 34, y,
                    "%s - %s".formatted(
                            safeValue(assessment.gradeLevelName(), "Not specified"),
                            safeValue(assessment.sectionName(), "Not specified")
                    ));
            drawText(content, FONT_BOLD, 9.5f, rightColumn, y, "Subject:");
            drawText(content, FONT_REGULAR, 9.5f, rightColumn + 43, y,
                    safeValue(assessment.subjectName(), "Not specified"));
            y -= 16;

            drawText(content, FONT_BOLD, 9.5f, MARGIN, y, "Term:");
            drawText(content, FONT_REGULAR, 9.5f, MARGIN + 34, y,
                    safeValue(assessment.termName(), "Not specified"));
            drawText(content, FONT_BOLD, 9.5f, rightColumn, y, "School year:");
            drawText(content, FONT_REGULAR, 9.5f, rightColumn + 62, y,
                    safeValue(assessment.yearName(), "Not specified"));
            y -= 16;

            drawText(content, FONT_BOLD, 9.5f, MARGIN, y, "Type:");
            drawText(content, FONT_REGULAR, 9.5f, MARGIN + 34, y,
                    safeValue(assessment.testType(), "Not specified"));
            drawText(content, FONT_BOLD, 9.5f, rightColumn, y, "Date:");
            drawText(content, FONT_REGULAR, 9.5f, rightColumn + 30, y,
                    assessment.testDate() == null ? "Not specified" : DISPLAY_DATE.format(assessment.testDate()));
            y -= 15;
            drawRule();
            y -= 22;

            drawText(content, FONT_BOLD, 10, MARGIN, y, "Student name:");
            drawLine(MARGIN + 72, y - 2, PAGE_WIDTH / 2 + 70, y - 2);
            drawText(content, FONT_BOLD, 10, PAGE_WIDTH / 2 + 90, y, "LRN:");
            drawLine(PAGE_WIDTH / 2 + 118, y - 2, PAGE_WIDTH - MARGIN, y - 2);
            y -= 25;

            drawText(content, FONT_BOLD, 10, MARGIN, y, "Instructions");
            y -= 14;
            List<String> instructionLines = wrapText(
                    safeValue(assessment.instructions(), "Answer every item clearly."),
                    FONT_REGULAR,
                    9.5f,
                    CONTENT_WIDTH
            );
            for (String line : instructionLines) {
                drawText(content, FONT_REGULAR, 9.5f, MARGIN, y, line);
                y -= 13;
            }
            y -= 7;
            drawRule();
            y -= 18;
        }

        private void writePartHeader(V2AssessmentPartResponse part) throws IOException {
            ensureSpace(38);
            String partName = safeValue(part.partName(), "Part " + safeValue(
                    part.partOrder() == null ? null : part.partOrder().toString(), ""
            ));
            String detail = displayPartType(part.partType());
            String points = displayPoints(part.pointsPerItem());
            if (!points.isBlank()) {
                detail += " | " + points;
            }

            drawText(content, FONT_BOLD, 12, MARGIN, y, partName);
            y -= 15;
            drawText(content, FONT_REGULAR, 8.5f, MARGIN, y, detail);
            y -= 10;
            drawRule();
            y -= 16;
        }

        private void writeQuestion(
                int questionNumber,
                String partType,
                V2AssessmentQuestionResponse question
        ) throws IOException {
            List<String> questionLines = wrapText(
                    questionNumber + ".  " + safeValue(question.questionText(), "[Question text unavailable]"),
                    FONT_REGULAR,
                    10,
                    CONTENT_WIDTH
            );
            List<String> choices = choices(partType, question);
            List<List<String>> choiceLines = new ArrayList<>();
            int totalChoiceLines = 0;
            for (String choice : choices) {
                List<String> wrapped = wrapText(choice, FONT_REGULAR, 9.5f, CONTENT_WIDTH - 20);
                choiceLines.add(wrapped);
                totalChoiceLines += wrapped.size();
            }

            float requiredHeight = (questionLines.size() * 13f) + (totalChoiceLines * 12.5f) + 13f;
            ensureSpace(Math.min(requiredHeight, PAGE_HEIGHT - MARGIN - BOTTOM_MARGIN));

            for (String line : questionLines) {
                ensureSpace(13);
                drawText(content, FONT_REGULAR, 10, MARGIN, y, line);
                y -= 13;
            }
            y -= 2;
            for (List<String> wrappedChoice : choiceLines) {
                for (String line : wrappedChoice) {
                    ensureSpace(12.5f);
                    drawText(content, FONT_REGULAR, 9.5f, MARGIN + 20, y, line);
                    y -= 12.5f;
                }
            }
            y -= 9;
        }

        private List<String> choices(String partType, V2AssessmentQuestionResponse question) {
            List<String> choices = new ArrayList<>();
            addChoice(choices, "A", question.optionA());
            addChoice(choices, "B", question.optionB());
            if (!"true_false".equalsIgnoreCase(partType)) {
                addChoice(choices, "C", question.optionC());
                addChoice(choices, "D", question.optionD());
                addChoice(choices, "E", question.optionE());
            }
            return choices;
        }

        private void addChoice(List<String> choices, String label, String value) {
            if (value != null && !value.isBlank()) {
                choices.add(label + ".  " + value);
            }
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight < BOTTOM_MARGIN) {
                startPage(true);
            }
        }

        private void drawCentered(PDFont font, float fontSize, String text) throws IOException {
            float width = textWidth(font, fontSize, text);
            drawText(content, font, fontSize, (PAGE_WIDTH - width) / 2, y, text);
            y -= fontSize + 3;
        }

        private void drawRule() throws IOException {
            drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y);
        }

        private void drawLine(float startX, float startY, float endX, float endY) throws IOException {
            content.setLineWidth(0.6f);
            content.moveTo(startX, startY);
            content.lineTo(endX, endY);
            content.stroke();
        }
    }
}
