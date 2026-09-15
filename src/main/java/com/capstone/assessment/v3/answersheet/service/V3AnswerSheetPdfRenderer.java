package com.capstone.assessment.v3.answersheet.service;

import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.GenerationPlan;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Question;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.TemplateRegion;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Profile("v3")
@Service
public class V3AnswerSheetPdfRenderer {

    private static final String VALIDATED_TEMPLATE_CODE = "OMR-A4-10-MC-CTX-V2";
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] render(GenerationPlan plan) {
        return renderPages(List.of(plan));
    }

    public byte[] renderPages(List<GenerationPlan> plans) {
        plans.forEach(this::requireValidatedPlan);
        GenerationPlan plan = plans.get(0);
        float pageWidth = plan.paperSize().widthPoints().floatValue();
        float pageHeight = plan.paperSize().heightPoints().floatValue();

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (GenerationPlan current : plans) {
            PDRectangle pageBox = new PDRectangle(pageWidth, pageHeight);
            PDPage page = new PDPage(pageBox);
            page.setMediaBox(pageBox);
            page.setCropBox(pageBox);
            page.setTrimBox(pageBox);
            page.setBleedBox(pageBox);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawPage(content, current, pageWidth, pageHeight);
                drawText(content, FONT_REGULAR, 7, 36, 55, "Page " + (plans.indexOf(current) + 1) + " / " + plans.size());
            }

            }
            document.getDocumentInformation().setTitle("SMART Assessment Bubble Answer Sheet");
            document.getDocumentInformation().setAuthor("SMART Assessment System");
            document.getDocumentInformation().setSubject(
                    "Immutable answer-sheet template " + plan.template().code()
            );
            PDViewerPreferences preferences = new PDViewerPreferences();
            preferences.setPrintScaling(PDViewerPreferences.PRINT_SCALING.None);
            preferences.setPrintArea(PDViewerPreferences.BOUNDARY.MediaBox);
            preferences.setPrintClip(PDViewerPreferences.BOUNDARY.MediaBox);
            document.getDocumentCatalog().setViewerPreferences(preferences);
            document.save(output);
            return output.toByteArray();
        } catch (IOException | WriterException exception) {
            throw new V3AuthException(
                    "ANSWER_SHEET_PDF_GENERATION_FAILED",
                    "The Bubble Answer Sheet could not be generated.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private void drawPage(
            PDPageContentStream content,
            GenerationPlan plan,
            float pageWidth,
            float pageHeight
    ) throws IOException, WriterException {
        List<TemplateRegion> regions = plan.template().regions();
        for (TemplateRegion marker : regions.stream()
                .filter(region -> "registration_marker".equals(region.type()))
                .toList()) {
            drawRegistrationMarker(content, marker);
        }

        drawCenteredText(content, FONT_BOLD, 15, pageWidth / 2, pageHeight - 45,
                "Performance Analytics Assessment System");
        drawCenteredText(content, FONT_BOLD, 11, pageWidth / 2, pageHeight - 63,
                "BUBBLE ANSWER SHEET");
        drawRightText(content, FONT_REGULAR, 7, pageWidth - 36, pageHeight - 45,
                plan.template().code());

        TemplateRegion qrRegion = regions.stream()
                .filter(region -> "page_identity".equals(region.type()))
                .findFirst()
                .orElseThrow(() -> invalidTemplate("The validated template is missing its QR region."));
        drawQrCode(
                content,
                plan.qrPayload(),
                qrRegion.xPoints().floatValue(),
                qrRegion.yPoints().floatValue(),
                qrRegion.widthPoints().floatValue(),
                quietZone(qrRegion)
        );

        float fieldRight = pageWidth - 136;
        drawLabeledValue(content, "Student:", "", 36, pageHeight - 91, fieldRight - 36);
        drawLabeledValue(
                content,
                "Grade/Section:",
                "%s - %s".formatted(
                        safe(plan.assignment().gradeLevelName()),
                        safe(plan.assignment().sectionName())
                ),
                36,
                pageHeight - 116,
                fieldRight - 36
        );
        drawLabeledValue(content, "Subject:", safe(plan.assignment().subjectName()),
                36, pageHeight - 141, fieldRight - 36);
        drawLabeledValue(content, "Test Name:", safe(plan.assignment().testName()),
                36, pageHeight - 166, fieldRight - 36);

        float instructionY = pageHeight - 193;
        drawText(content, FONT_REGULAR, 12, 36, instructionY,
                "Instructions: Fill one bubble completely for each item. Use a dark pencil or black pen.");
        content.setLineWidth(0.8f);
        content.moveTo(36, instructionY - 8);
        content.lineTo(pageWidth - 36, instructionY - 8);
        content.stroke();

        List<TemplateRegion> objectiveRegions = regions.stream()
                .filter(region -> "objective_bubbles".equals(region.type()))
                .sorted(Comparator.comparingInt(TemplateRegion::order))
                .toList();
        for (int index = 0; index < plan.questions().size(); index++) {
            drawObjectiveRow(content, objectiveRegions.get(index), plan.questions().get(index));
        }

        drawText(content, FONT_REGULAR, 7, 36, 40,
                "Items on page: " + plan.questions().size() + "   Type: multiple_choice   Options: A/B/C/D");
        drawRightText(content, FONT_REGULAR, 7, pageWidth - 36, 40,
                "Print at Actual Size / 100% - do not use Fit to Page");
    }

    private void drawRegistrationMarker(PDPageContentStream content, TemplateRegion region)
            throws IOException {
        float x = region.xPoints().floatValue();
        float y = region.yPoints().floatValue();
        float width = region.widthPoints().floatValue();
        float height = region.heightPoints().floatValue();
        content.setNonStrokingColor(0, 0, 0);
        content.addRect(x, y, width, height);
        content.fill();

        float insetRatio = decimal(region.geometry(), "inner_inset_ratio", 0.28f);
        float insetX = width * insetRatio;
        float insetY = height * insetRatio;
        content.setNonStrokingColor(1f, 1f, 1f);
        content.addRect(x + insetX, y + insetY, width - (2 * insetX), height - (2 * insetY));
        content.fill();
        content.setNonStrokingColor(0, 0, 0);
    }

    private void drawObjectiveRow(
            PDPageContentStream content,
            TemplateRegion region,
            Question question
    ) throws IOException {
        JsonNode keys = region.geometry().get("option_keys");
        JsonNode centers = region.geometry().get("bubble_centers_pt");
        float radius = decimal(region.geometry(), "bubble_radius_pt", 6.4f);
        float centerY = centers.get(0).get(1).floatValue();
        drawRightText(content, FONT_BOLD, 8.5f, 59, centerY - 3,
                question.globalItemNumber() + ".");
        for (int index = 0; index < keys.size(); index++) {
            JsonNode center = centers.get(index);
            drawBubble(
                    content,
                    center.get(0).floatValue(),
                    center.get(1).floatValue(),
                    radius,
                    keys.get(index).asText()
            );
        }
    }

    private void drawBubble(
            PDPageContentStream content,
            float centerX,
            float centerY,
            float radius,
            String label
    ) throws IOException {
        drawCircle(content, centerX, centerY, radius);
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

    private void drawQrCode(
            PDPageContentStream content,
            String payload,
            float x,
            float y,
            float size,
            float quietZone
    ) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 0);
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 1, 1, hints);

        content.setNonStrokingColor(1f, 1f, 1f);
        content.addRect(x, y, size, size);
        content.fill();

        float codeSize = size - (2 * quietZone);
        float moduleWidth = codeSize / matrix.getWidth();
        float moduleHeight = codeSize / matrix.getHeight();
        content.setNonStrokingColor(0, 0, 0);
        for (int matrixY = 0; matrixY < matrix.getHeight(); matrixY++) {
            for (int matrixX = 0; matrixX < matrix.getWidth(); matrixX++) {
                if (matrix.get(matrixX, matrixY)) {
                    content.addRect(
                            x + quietZone + (matrixX * moduleWidth),
                            y + quietZone + ((matrix.getHeight() - matrixY - 1) * moduleHeight),
                            moduleWidth,
                            moduleHeight
                    );
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

    private String fitText(String value, PDFont font, float fontSize, float availableWidth)
            throws IOException {
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

    private void requireValidatedPlan(GenerationPlan plan) {
        if (plan == null
                || plan.template() == null
                || !(VALIDATED_TEMPLATE_CODE.equals(plan.template().code()) || V3DynamicLayout.CODE.equals(plan.template().code()))
                || plan.questions() == null
                || plan.questions().isEmpty()
                || (V3DynamicLayout.CODE.equals(plan.template().code()) ? plan.questions().size() > V3DynamicLayout.PAGE_CAPACITY : plan.questions().size() != 10)) {
            throw invalidTemplate("Only the physically validated A4 10-item MC template can be rendered.");
        }
    }

    private float quietZone(TemplateRegion region) {
        return decimal(region.geometry(), "quiet_zone_pt", 6f);
    }

    private float decimal(JsonNode node, String key, float fallback) {
        JsonNode value = node == null ? null : node.get(key);
        return value != null && value.isNumber() ? value.floatValue() : fallback;
    }

    private V3AuthException invalidTemplate(String message) {
        return new V3AuthException("VALIDATED_TEMPLATE_GEOMETRY_INVALID", message, HttpStatus.CONFLICT);
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
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
