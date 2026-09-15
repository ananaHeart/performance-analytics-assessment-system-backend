package com.capstone.assessment.v2.assessment.service;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentPartResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentQuestionResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Profile("v2")
@Service
public class V2OmrSheetPrintService {

    static final String TEMPLATE_VERSION = "OMR-A4-10-MC-CTX-V2";
    static final int TEMPLATE_ITEM_COUNT = 10;
    static final float PAGE_WIDTH = 595.276f;
    static final float PAGE_HEIGHT = 841.890f;
    static final float BUBBLE_RADIUS = 6.4f;
    static final float FIRST_ROW_Y = 604.334f;
    static final float ROW_INTERVAL = 33.111f;
    static final float[] BUBBLE_X_CENTERS = {79f, 103f, 127f, 151f};
    static final float[] BUBBLE_Y_CENTERS = {
            604.334f, 571.223f, 538.112f, 505.001f, 471.890f,
            438.778f, 405.667f, 372.556f, 339.445f, 306.334f
    };

    private static final List<String> OPTIONS = List.of("A", "B", "C", "D");
    private static final float MARKER_SIZE = 15f;
    private static final float MARKER_MARGIN = 20f;
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] renderSheet(V2AssessmentResponse assessment) {
        validatePrintableAssessment(assessment);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDRectangle pageBox = new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT);
            PDPage page = new PDPage(pageBox);
            page.setMediaBox(pageBox);
            page.setCropBox(pageBox);
            page.setTrimBox(pageBox);
            page.setBleedBox(pageBox);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawSheet(content, assessment);
            }

            document.getDocumentInformation().setTitle("SMART Assessment Bubble Answer Sheet");
            document.getDocumentInformation().setAuthor("SMART Assessment System");
            document.getDocumentInformation().setSubject("Fixed-layout OMR template " + TEMPLATE_VERSION);
            PDViewerPreferences viewerPreferences = new PDViewerPreferences();
            viewerPreferences.setPrintScaling(PDViewerPreferences.PRINT_SCALING.None);
            viewerPreferences.setPrintArea(PDViewerPreferences.BOUNDARY.MediaBox);
            viewerPreferences.setPrintClip(PDViewerPreferences.BOUNDARY.MediaBox);
            document.getDocumentCatalog().setViewerPreferences(viewerPreferences);
            document.save(output);
            return output.toByteArray();
        } catch (IOException | WriterException exception) {
            throw new V2AuthException(
                    "OMR_PDF_GENERATION_FAILED",
                    "The Bubble Answer Sheet could not be generated.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private void drawSheet(PDPageContentStream content, V2AssessmentResponse assessment)
            throws IOException, WriterException {
        drawCornerMarkers(content);

        drawCenteredText(content, FONT_BOLD, 15, PAGE_WIDTH / 2, PAGE_HEIGHT - 45,
                "Performance Analytics Assessment System");
        drawCenteredText(content, FONT_BOLD, 11, PAGE_WIDTH / 2, PAGE_HEIGHT - 63,
                "BUBBLE ANSWER SHEET");
        drawRightText(content, FONT_REGULAR, 7, PAGE_WIDTH - 36, PAGE_HEIGHT - 45, TEMPLATE_VERSION);

        drawQrCode(content, qrPayload(assessment.testId()), PAGE_WIDTH - 118, PAGE_HEIGHT - 143, 83);

        float fieldRight = PAGE_WIDTH - 136;
        drawLabeledValue(content, "Student:", "", 36, PAGE_HEIGHT - 91, fieldRight - 36);
        drawLabeledValue(content, "Grade/Section:",
                "%s - %s".formatted(safe(assessment.gradeLevelName()), safe(assessment.sectionName())),
                36, PAGE_HEIGHT - 116, fieldRight - 36);
        drawLabeledValue(content, "Subject:", safe(assessment.subjectName()),
                36, PAGE_HEIGHT - 141, fieldRight - 36);
        drawLabeledValue(content, "Test Name:", safe(assessment.testName()),
                36, PAGE_HEIGHT - 166, fieldRight - 36);

        float instructionY = PAGE_HEIGHT - 193;
        drawText(content, FONT_REGULAR, 12, 36, instructionY,
                "Instructions: Fill one bubble completely for each item. Use a dark pencil or black pen.");
        content.setLineWidth(0.8f);
        content.moveTo(36, instructionY - 8);
        content.lineTo(PAGE_WIDTH - 36, instructionY - 8);
        content.stroke();

        for (int itemIndex = 0; itemIndex < TEMPLATE_ITEM_COUNT; itemIndex++) {
            float centerY = bubbleCenterY(itemIndex);
            drawRightText(content, FONT_BOLD, 8.5f, 59, centerY - 3, (itemIndex + 1) + ".");
            for (int optionIndex = 0; optionIndex < OPTIONS.size(); optionIndex++) {
                drawBubble(content, BUBBLE_X_CENTERS[optionIndex], centerY, OPTIONS.get(optionIndex));
            }
        }

        drawText(content, FONT_REGULAR, 7, 36, 40,
                "Items: 10   Type: multiple_choice   Options: A/B/C/D");
        drawRightText(content, FONT_REGULAR, 7, PAGE_WIDTH - 36, 40,
                "Print at Actual Size / 100% - do not use Fit to Page");
    }

    private void drawCornerMarkers(PDPageContentStream content) throws IOException {
        drawCornerMarker(content, MARKER_MARGIN, MARKER_MARGIN);
        drawCornerMarker(content, PAGE_WIDTH - MARKER_MARGIN - MARKER_SIZE, MARKER_MARGIN);
        drawCornerMarker(content, MARKER_MARGIN, PAGE_HEIGHT - MARKER_MARGIN - MARKER_SIZE);
        drawCornerMarker(content,
                PAGE_WIDTH - MARKER_MARGIN - MARKER_SIZE,
                PAGE_HEIGHT - MARKER_MARGIN - MARKER_SIZE);
    }

    private void drawCornerMarker(PDPageContentStream content, float x, float y) throws IOException {
        content.setNonStrokingColor(0, 0, 0);
        content.addRect(x, y, MARKER_SIZE, MARKER_SIZE);
        content.fill();

        float inset = MARKER_SIZE * 0.28f;
        content.setNonStrokingColor(1f, 1f, 1f);
        content.addRect(x + inset, y + inset, MARKER_SIZE - (2 * inset), MARKER_SIZE - (2 * inset));
        content.fill();
        content.setNonStrokingColor(0, 0, 0);
    }

    private void drawBubble(PDPageContentStream content, float centerX, float centerY, String label)
            throws IOException {
        drawCircle(content, centerX, centerY, BUBBLE_RADIUS);
        drawCenteredText(content, FONT_BOLD, 6.5f, centerX, centerY - 2.2f, label);
    }

    private void drawCircle(PDPageContentStream content, float centerX, float centerY, float radius)
            throws IOException {
        float control = radius * 0.552284749831f;
        content.setLineWidth(0.9f);
        content.moveTo(centerX + radius, centerY);
        content.curveTo(centerX + radius, centerY + control,
                centerX + control, centerY + radius, centerX, centerY + radius);
        content.curveTo(centerX - control, centerY + radius,
                centerX - radius, centerY + control, centerX - radius, centerY);
        content.curveTo(centerX - radius, centerY - control,
                centerX - control, centerY - radius, centerX, centerY - radius);
        content.curveTo(centerX + control, centerY - radius,
                centerX + radius, centerY - control, centerX + radius, centerY);
        content.closePath();
        content.stroke();
    }

    private void drawQrCode(PDPageContentStream content, String payload, float x, float y, float size)
            throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 0);
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 1, 1, hints);

        content.setNonStrokingColor(1f, 1f, 1f);
        content.addRect(x, y, size, size);
        content.fill();

        float quietZone = 6f;
        float codeSize = size - (2 * quietZone);
        float moduleWidth = codeSize / matrix.getWidth();
        float moduleHeight = codeSize / matrix.getHeight();
        content.setNonStrokingColor(0, 0, 0);
        for (int matrixY = 0; matrixY < matrix.getHeight(); matrixY++) {
            for (int matrixX = 0; matrixX < matrix.getWidth(); matrixX++) {
                if (matrix.get(matrixX, matrixY)) {
                    float moduleX = x + quietZone + (matrixX * moduleWidth);
                    float moduleY = y + quietZone + ((matrix.getHeight() - matrixY - 1) * moduleHeight);
                    content.addRect(moduleX, moduleY, moduleWidth, moduleHeight);
                }
            }
        }
        content.fill();
    }

    private void drawLabeledValue(
            PDPageContentStream content,
            String label,
            String value,
            float x,
            float y,
            float width
    ) throws IOException {
        drawText(content, FONT_REGULAR, 12, x, y, label);
        float labelWidth = textWidth(FONT_REGULAR, 12, label);
        float valueX = x + labelWidth + 6;
        float availableWidth = Math.max(0, width - labelWidth - 6);
        drawText(content, FONT_BOLD, 12, valueX, y, fitText(value, FONT_BOLD, 12, availableWidth));
        content.setLineWidth(0.7f);
        content.moveTo(valueX, y - 3);
        content.lineTo(x + width, y - 3);
        content.stroke();
    }

    private void drawText(
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

    private void drawCenteredText(
            PDPageContentStream content,
            PDFont font,
            float fontSize,
            float centerX,
            float y,
            String text
    ) throws IOException {
        drawText(content, font, fontSize, centerX - (textWidth(font, fontSize, text) / 2), y, text);
    }

    private void drawRightText(
            PDPageContentStream content,
            PDFont font,
            float fontSize,
            float rightX,
            float y,
            String text
    ) throws IOException {
        drawText(content, font, fontSize, rightX - textWidth(font, fontSize, text), y, text);
    }

    private float textWidth(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(safePdfText(text)) / 1000f * fontSize;
    }

    private String fitText(String value, PDFont font, float fontSize, float availableWidth) throws IOException {
        String normalized = safe(value);
        if (textWidth(font, fontSize, normalized) <= availableWidth) {
            return normalized;
        }
        String suffix = "...";
        String shortened = normalized;
        while (!shortened.isEmpty()
                && textWidth(font, fontSize, shortened + suffix) > availableWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened.isEmpty() ? "" : shortened + suffix;
    }

    static String qrPayload(long testId) {
        return "{\"v\":2,\"tv\":\"%s\",\"t\":%d,\"q\":\"MC\",\"n\":10}"
                .formatted(TEMPLATE_VERSION, testId);
    }

    static float bubbleCenterY(int zeroBasedItemIndex) {
        return BUBBLE_Y_CENTERS[zeroBasedItemIndex];
    }

    private void validatePrintableAssessment(V2AssessmentResponse assessment) {
        if (assessment == null) {
            throw badRequest("ASSESSMENT_REQUIRED", "Assessment is required.");
        }
        if (!"active".equalsIgnoreCase(assessment.status())) {
            throw badRequest("ASSESSMENT_NOT_ACTIVE", "Only active assessments can be printed as Bubble Answer Sheets.");
        }
        List<V2AssessmentQuestionResponse> questions = flattenQuestions(assessment);
        if (questions.size() != TEMPLATE_ITEM_COUNT) {
            throw badRequest(
                    "UNSUPPORTED_OMR_ITEM_COUNT",
                    "The validated Bubble Answer Sheet supports exactly 10 multiple-choice items."
            );
        }
        for (V2AssessmentPartResponse part : safeParts(assessment)) {
            if (!"multiple_choice".equalsIgnoreCase(part.partType())) {
                throw badRequest(
                        "UNSUPPORTED_OMR_PART_TYPE",
                        "All test parts must use multiple_choice for this Bubble Answer Sheet."
                );
            }
        }
        for (V2AssessmentQuestionResponse question : questions) {
            if (isBlank(question.optionA())
                    || isBlank(question.optionB())
                    || isBlank(question.optionC())
                    || isBlank(question.optionD())
                    || !isBlank(question.optionE())
                    || !OPTIONS.contains(safe(question.correctOption()).toUpperCase())) {
                throw badRequest(
                        "UNSUPPORTED_OMR_OPTIONS",
                        "Every printed question must use exactly options A, B, C, and D."
                );
            }
        }
    }

    private List<V2AssessmentQuestionResponse> flattenQuestions(V2AssessmentResponse assessment) {
        List<V2AssessmentQuestionResponse> questions = new ArrayList<>();
        for (V2AssessmentPartResponse part : safeParts(assessment)) {
            if (part.questions() != null) {
                questions.addAll(part.questions());
            }
        }
        return questions;
    }

    private List<V2AssessmentPartResponse> safeParts(V2AssessmentResponse assessment) {
        return assessment.parts() == null ? List.of() : assessment.parts();
    }

    private V2AuthException badRequest(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.BAD_REQUEST);
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safePdfText(String value) {
        return safe(value)
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201c', '"')
                .replace('\u201d', '"');
    }
}
