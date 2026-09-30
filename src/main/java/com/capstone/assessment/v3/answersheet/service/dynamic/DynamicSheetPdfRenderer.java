package com.capstone.assessment.v3.answersheet.service.dynamic;

import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicManifest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicPage;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetContext;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicRegion;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.PartHeader;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.Rectangle;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.RegionOption;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.RegistrationMarker;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * PDFBox port of the reference generator's {@code draw_*} functions
 * (generate_dynamic_answer_sheet.py, py:878-1251). Draws all five dynamic question
 * types (multiple_choice, true_false, identification, enumeration, essay), unlike the
 * physically validated fixed-sheet renderer, which only knows multiple_choice.
 *
 * <p>Isolated in its own package - does not share code with
 * {@code V3AnswerSheetPdfRenderer} (the fixed A4 10-item MC template). Purely
 * decorative details (rounded corners, exact tint shades) are simplified to plain
 * rectangles/solid fills where PDFBox has no direct equivalent; every coordinate that
 * affects scanning (marker rectangles, QR rectangle, bubble centers, region
 * rectangles, line positions) is drawn at the exact point values the packer computed
 * and that {@code DynamicSheetPackerTest} already verifies against the confirmed
 * reference manifests.
 */
public final class DynamicSheetPdfRenderer {

    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float QUIET_ZONE = 6f;
    private static final String CONTINUED_SUFFIX = " (continued)";

    /** Part name -> its per-item points, only for parts where every item is worth the same.
     *  Those points print once in the green part header ("1 pt each") instead of beside
     *  every item. Printed text only: no region, bubble, marker or QR position changes, so
     *  the manifest and the mobile scanner are unaffected. Passed as a parameter, not kept
     *  in a field: one renderer instance is shared across concurrent requests. */
    private static Map<String, PartPoints> uniformPartPoints(DynamicManifest manifest) {
        Map<String, Set<Long>> partIds = new HashMap<>();
        Map<String, Set<Double>> points = new HashMap<>();
        Map<String, Integer> itemCounts = new HashMap<>();
        for (DynamicPage page : manifest.pages()) {
            for (DynamicRegion region : page.regions()) {
                if (region.partName() == null) continue;
                partIds.computeIfAbsent(region.partName(), name -> new HashSet<>()).add(region.testPartId());
                points.computeIfAbsent(region.partName(), name -> new HashSet<>()).add(region.maximumPoints());
                itemCounts.merge(region.partName(), 1, Integer::sum);
            }
        }
        Map<String, PartPoints> uniform = new HashMap<>();
        points.forEach((name, values) -> {
            // Two different parts sharing one name can't be told apart from the header title.
            if (values.size() == 1 && partIds.get(name).size() == 1) {
                uniform.put(name, new PartPoints(values.iterator().next(), itemCounts.get(name)));
            }
        });
        return uniform;
    }

    private record PartPoints(double pointsPerItem, int itemCount) {
    }

    private static String partBaseName(String title) {
        return title != null && title.endsWith(CONTINUED_SUFFIX)
                ? title.substring(0, title.length() - CONTINUED_SUFFIX.length())
                : title;
    }

    public byte[] render(DynamicManifest manifest, DynamicSheetContext context) {
        Map<String, PartPoints> headerPointsByPartName = uniformPartPoints(manifest);
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            for (DynamicPage page : manifest.pages()) {
                float pageWidth = (float) page.widthPt();
                float pageHeight = (float) page.heightPt();
                PDRectangle box = new PDRectangle(pageWidth, pageHeight);
                PDPage pdPage = new PDPage(box);
                pdPage.setMediaBox(box);
                pdPage.setCropBox(box);
                document.addPage(pdPage);

                try (PDPageContentStream content = new PDPageContentStream(document, pdPage)) {
                    for (RegistrationMarker marker : page.markers()) {
                        drawCornerMarker(content, marker);
                    }

                    if (page.pageNumber() == 1) {
                        drawPageOneHeader(content, pageWidth, pageHeight, context, page);
                    } else {
                        drawContinuationHeader(content, pageWidth, pageHeight, page.pageNumber(), page.totalPages());
                    }

                    Rectangle qr = page.qrRectangle();
                    drawQrCode(content, page.qrPayload(), qr);

                    for (PartHeader header : page.partHeaders()) {
                        drawPartHeader(content, header, headerPointsByPartName);
                    }
                    for (DynamicRegion region : page.regions()) {
                        if ("objective_bubbles".equals(region.regionType())) {
                            drawObjectiveRegion(content, region);
                        } else {
                            drawWrittenRegion(content, region, headerPointsByPartName);
                        }
                    }

                    drawTechnicalFooter(content, pageWidth, manifest.manifestHash(),
                            page.pageNumber(), page.totalPages(), qr);
                }
            }

            document.getDocumentInformation().setTitle(
                    (manifest.paperSizeCode()) + " Dynamic Answer Sheet");
            document.save(output);
            return output.toByteArray();
        } catch (IOException | WriterException e) {
            throw new IllegalStateException("Dynamic answer sheet PDF generation failed.", e);
        }
    }

    // draw_corner_marker (py:878-895)
    private void drawCornerMarker(PDPageContentStream content, RegistrationMarker marker) throws IOException {
        Rectangle r = marker.rectangle();
        float x = (float) r.x();
        float y = (float) r.y();
        float size = (float) r.width();
        content.setNonStrokingColor(0, 0, 0);
        content.addRect(x, y, size, size);
        content.fill();
        if ("hollow".equals(marker.style())) {
            float inset = size * 0.28f;
            content.setNonStrokingColor(1f, 1f, 1f);
            content.addRect(x + inset, y + inset, size - (2 * inset), size - (2 * inset));
            content.fill();
        }
        content.setNonStrokingColor(0, 0, 0);
    }

    // draw_qr_code (py:898-914)
    private void drawQrCode(PDPageContentStream content, String payload, Rectangle box) throws IOException, WriterException {
        float x = (float) box.x();
        float y = (float) box.y();
        float size = (float) box.width();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 0);
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 1, 1, hints);

        content.setNonStrokingColor(1f, 1f, 1f);
        content.addRect(x, y, size, size);
        content.fill();

        float codeSize = size - (2 * QUIET_ZONE);
        float moduleWidth = codeSize / matrix.getWidth();
        float moduleHeight = codeSize / matrix.getHeight();
        content.setNonStrokingColor(0, 0, 0);
        for (int my = 0; my < matrix.getHeight(); my++) {
            for (int mx = 0; mx < matrix.getWidth(); mx++) {
                if (matrix.get(mx, my)) {
                    content.addRect(
                            x + QUIET_ZONE + (mx * moduleWidth),
                            y + QUIET_ZONE + ((matrix.getHeight() - my - 1) * moduleHeight),
                            moduleWidth, moduleHeight);
                }
            }
        }
        content.fill();
    }

    // draw_page_one_header (py:959-1021)
    private void drawPageOneHeader(
            PDPageContentStream content, float pageWidth, float pageHeight,
            DynamicSheetContext context, DynamicPage page
    ) throws IOException {
        float contentMarginX = 42f;
        float contentRight = pageWidth - contentMarginX;
        String schoolName = context == null || context.schoolName() == null || context.schoolName().isBlank()
                ? "SAN ROQUE NATIONAL HIGH SCHOOL" : context.schoolName();
        String assessmentName = context == null || context.assessmentName() == null || context.assessmentName().isBlank()
                ? "Assessment" : context.assessmentName();
        String subject = context == null || context.subject() == null ? "" : context.subject();
        String gradeSection = context == null || context.gradeSection() == null ? "" : context.gradeSection();

        drawText(content, FONT_BOLD, 11.5f, contentMarginX, pageHeight - 43,
                fitText(schoolName, FONT_BOLD, 11.5f, pageWidth * 0.56f));
        drawRightText(content, FONT_BOLD, 9.5f, contentRight, pageHeight - 43, "ASSESSMENT ANSWER SHEET");
        content.setStrokingColor(0.16f, 0.19f, 0.22f);
        content.setLineWidth(0.7f);
        content.moveTo(contentMarginX, pageHeight - 55);
        content.lineTo(contentRight, pageHeight - 55);
        content.stroke();

        String maxPoints = context == null ? "" : formatPoints(context.totalPoints());
        float metadataWidth = contentRight - contentMarginX;
        float[] ratios = {0.37f, 0.15f, 0.23f, 0.13f, 0.12f};
        String[] labels = {"ASSESSMENT", "SUBJECT", "GRADE & SECTION", "PAGE", "MAX POINTS"};
        String[] values = {assessmentName, subject, gradeSection, page.pageNumber() + " of " + page.totalPages(), maxPoints};
        float cellX = contentMarginX;
        for (int i = 0; i < labels.length; i++) {
            float cellWidth = metadataWidth * ratios[i];
            drawText(content, FONT_BOLD, 6.2f, cellX, pageHeight - 75, labels[i]);
            drawText(content, FONT_BOLD, 8.2f, cellX, pageHeight - 86,
                    fitText(values[i], FONT_BOLD, 8.2f, cellWidth - 7));
            cellX += cellWidth;
        }

        drawField(content, "Name", "", contentMarginX, pageHeight - 113, metadataWidth);

        float instructionY = pageHeight - 151;
        content.setNonStrokingColor(0.96f, 0.97f, 0.97f);
        content.addRect(contentMarginX, instructionY, pageWidth - (2 * contentMarginX), 17);
        content.fill();
        content.setNonStrokingColor(0.18f, 0.22f, 0.27f);
        drawText(content, FONT_REGULAR, 7.2f, contentMarginX + 7, instructionY + 5.5f,
                "Shade objective bubbles completely. Write responses only on the provided lines.");
        content.setNonStrokingColor(0, 0, 0);
    }

    // draw_continuation_header (py:1044-1062)
    private void drawContinuationHeader(PDPageContentStream content, float pageWidth, float pageHeight,
                                         int pageNumber, int totalPages) throws IOException {
        float contentMarginX = 42f;
        float contentRight = pageWidth - contentMarginX;
        drawRightText(content, FONT_BOLD, 7.5f, contentRight, pageHeight - 47,
                "CONTINUATION | PAGE " + pageNumber + " OF " + totalPages);
        content.setStrokingColor(0.78f, 0.82f, 0.84f);
        content.setLineWidth(0.5f);
        content.moveTo(contentMarginX, pageHeight - 55);
        content.lineTo(contentRight, pageHeight - 55);
        content.stroke();
    }

    // draw_part_header (py:1101-1116)
    private void drawPartHeader(PDPageContentStream content, PartHeader header,
                                Map<String, PartPoints> headerPointsByPartName) throws IOException {
        content.setNonStrokingColor(0.93f, 0.97f, 0.94f);
        content.addRect((float) header.x(), (float) header.y(), (float) header.width(), (float) header.height());
        content.fill();
        content.setNonStrokingColor(0.08f, 0.36f, 0.18f);
        float titleX = (float) header.x() + 7;
        drawText(content, FONT_BOLD, 9, titleX, (float) header.y() + 8, header.title());
        float leftEnd = titleX + textWidth(FONT_BOLD, 9, header.title());
        PartPoints partPoints = headerPointsByPartName.get(partBaseName(header.title()));
        if (partPoints != null) {
            // "1 pt each" for a multi-item part; just "5 pts" when the part has one item.
            String pointsText = formatPoints(partPoints.pointsPerItem(), partPoints.itemCount() > 1);
            drawText(content, FONT_REGULAR, 8.5f, leftEnd + 10, (float) header.y() + 8, pointsText);
            leftEnd += 10 + textWidth(FONT_REGULAR, 8.5f, pointsText);
        }
        if (header.instructions() != null && !header.instructions().isBlank()) {
            // Never let the right-aligned instructions run into the title and points.
            float rightEdge = (float) (header.x() + header.width()) - 7;
            float available = Math.min((float) header.width() * 0.48f, rightEdge - leftEnd - 14);
            String instruction = available <= 0 ? "" : fitText(header.instructions(), FONT_REGULAR, 7, available);
            content.setNonStrokingColor(0.18f, 0.22f, 0.20f);
            drawRightText(content, FONT_REGULAR, 7, (float) (header.x() + header.width()) - 7,
                    (float) header.y() + 8, instruction);
        }
        content.setNonStrokingColor(0, 0, 0);
    }

    // draw_objective_region (py:1119-1137)
    private void drawObjectiveRegion(PDPageContentStream content, DynamicRegion region) throws IOException {
        Rectangle r = region.rectangle();
        float centerY = (float) (r.y() + (r.height() / 2));
        content.setStrokingColor(0.78f, 0.78f, 0.78f);
        content.setLineWidth(0.45f);
        content.moveTo((float) r.x(), (float) r.y());
        content.lineTo((float) (r.x() + r.width()), (float) r.y());
        content.stroke();

        drawRightText(content, FONT_BOLD, 9, (float) r.x() + 28, centerY - 3, region.globalItemNumber() + ".");
        for (RegionOption option : region.options()) {
            float cx = (float) option.centerX();
            float cy = (float) option.centerY();
            drawCircle(content, cx, cy, (float) DynamicSheetPacker.BUBBLE_RADIUS);
            drawCenteredText(content, FONT_BOLD, 6.5f, cx, cy - 2.2f, bubbleLabel(region.questionType(), option.key()));
        }
    }

    /**
     * The stored option key stays "A"/"B" for true_false (matches scoring/detection everywhere
     * else); only the printed bubble label shows the student-facing "T"/"F" instead.
     */
    private static String bubbleLabel(String questionType, String key) {
        if (!"true_false".equals(questionType)) return key;
        return "A".equals(key) ? "T" : "B".equals(key) ? "F" : key;
    }

    // draw_written_region (py:1140-1181)
    private void drawWrittenRegion(PDPageContentStream content, DynamicRegion region,
                                   Map<String, PartPoints> headerPointsByPartName) throws IOException {
        Rectangle r = region.rectangle();
        float x = (float) r.x();
        float y = (float) r.y();
        float width = (float) r.width();
        float height = (float) r.height();
        String type = region.questionType();

        if ("essay".equals(type)) {
            content.setNonStrokingColor(1f, 1f, 1f);
            content.setStrokingColor(0.72f, 0.78f, 0.82f);
            content.setLineWidth(0.7f);
            content.addRect(x, y, width, height);
            content.fillAndStroke();
        }

        content.setNonStrokingColor(0.08f, 0.11f, 0.14f);
        drawText(content, FONT_BOLD, 8.5f, x + 8, y + height - 14, region.globalItemNumber() + ".");
        // Parts whose items are all worth the same show their points once in the part header.
        if (!headerPointsByPartName.containsKey(region.partName())) {
            drawRightText(content, FONT_REGULAR, 7, x + width - 8, y + height - 14,
                    formatPoints(region.maximumPoints(), false));
        }

        int lineCount = region.responseLineCount() == null ? 1 : region.responseLineCount();
        float lineTop = y + height - 25;
        float availableHeight = Math.max(12f, lineTop - (y + 10));
        float spacing = availableHeight / lineCount;
        content.setStrokingColor(0.66f, 0.72f, 0.76f);
        content.setLineWidth(0.45f);
        for (int i = 0; i < lineCount; i++) {
            float lineY = lineTop - ((i + 1) * spacing);
            float lineX;
            if ("enumeration".equals(type)) {
                content.setNonStrokingColor(0.25f, 0.25f, 0.25f);
                drawText(content, FONT_REGULAR, 7, x + 8, lineY + 2, (i + 1) + ".");
                lineX = x + 24;
            } else if ("identification".equals(type)) {
                lineX = x + 34;
            } else {
                lineX = x + 8;
            }
            content.moveTo(lineX, lineY);
            content.lineTo(x + width - 8, lineY);
            content.stroke();
        }
        content.setNonStrokingColor(0, 0, 0);
    }

    // draw_technical_footer (py:1065-1098)
    private void drawTechnicalFooter(PDPageContentStream content, float pageWidth, String manifestHash,
                                      int pageNumber, int totalPages, Rectangle qrRect) throws IOException {
        float contentMarginX = 42f;
        float footerRight = (float) qrRect.x() - 10f;
        float footerWidth = footerRight - contentMarginX;
        float footerY = 39f;
        content.setNonStrokingColor(0.32f, 0.37f, 0.42f);
        drawText(content, FONT_REGULAR, 5.5f, contentMarginX, footerY,
                fitText("Geometry " + manifestHash.substring(0, Math.min(12, manifestHash.length())),
                        FONT_REGULAR, 5.5f, footerWidth * 0.42f));
        drawCenteredText(content, FONT_REGULAR, 5.5f, contentMarginX + (footerWidth / 2), footerY + 14,
                "Print at Actual Size / 100%");
        drawRightText(content, FONT_REGULAR, 5.5f, footerRight, footerY,
                fitText(pageNumber + "/" + totalPages, FONT_REGULAR, 5.5f, footerWidth * 0.54f));
        content.setNonStrokingColor(0, 0, 0);
    }

    private void drawField(PDPageContentStream content, String label, String value, float x, float y, float width)
            throws IOException {
        drawText(content, FONT_REGULAR, 9, x, y, label + ":");
        float labelWidth = textWidth(FONT_REGULAR, 9, label + ":");
        float valueX = x + labelWidth + 5;
        content.setLineWidth(0.5f);
        content.moveTo(valueX, y - 2);
        content.lineTo(x + width, y - 2);
        content.stroke();
    }

    private String formatPoints(double points) {
        return formatPoints(points, false);
    }

    /** "1 pt", "5 pts", "1.5 pts"; with each=true, "1 pt each" for a part header. */
    private String formatPoints(double points, boolean each) {
        String number = points == Math.floor(points)
                ? Long.toString((long) points)
                : String.format(Locale.ROOT, "%.1f", points);
        String unit = points == 1 ? " pt" : " pts";
        return number + unit + (each ? " each" : "");
    }

    private void drawCircle(PDPageContentStream content, float centerX, float centerY, float radius) throws IOException {
        float control = radius * 0.552284749831f;
        content.setNonStrokingColor(1f, 1f, 1f);
        content.setStrokingColor(0, 0, 0);
        content.setLineWidth(0.9f);
        content.moveTo(centerX + radius, centerY);
        content.curveTo(centerX + radius, centerY + control, centerX + control, centerY + radius, centerX, centerY + radius);
        content.curveTo(centerX - control, centerY + radius, centerX - radius, centerY + control, centerX - radius, centerY);
        content.curveTo(centerX - radius, centerY - control, centerX - control, centerY - radius, centerX, centerY - radius);
        content.curveTo(centerX + control, centerY - radius, centerX + radius, centerY - control, centerX + radius, centerY);
        content.closePath();
        content.fillAndStroke();
        content.setNonStrokingColor(0, 0, 0);
    }

    private void drawText(PDPageContentStream content, PDFont font, float size, float x, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(safe(text));
        content.endText();
    }

    private void drawCenteredText(PDPageContentStream content, PDFont font, float size, float centerX, float y, String text)
            throws IOException {
        drawText(content, font, size, centerX - (textWidth(font, size, text) / 2), y, text);
    }

    private void drawRightText(PDPageContentStream content, PDFont font, float size, float rightX, float y, String text)
            throws IOException {
        drawText(content, font, size, rightX - textWidth(font, size, text), y, text);
    }

    private float textWidth(PDFont font, float size, String text) throws IOException {
        return font.getStringWidth(safe(text)) / 1000f * size;
    }

    private String fitText(String value, PDFont font, float size, float availableWidth) throws IOException {
        String normalized = safe(value);
        if (textWidth(font, size, normalized) <= availableWidth) {
            return normalized;
        }
        String suffix = "...";
        String shortened = normalized;
        while (!shortened.isEmpty() && textWidth(font, size, shortened + suffix) > availableWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened.isEmpty() ? "" : shortened + suffix;
    }

    private String safe(String value) {
        return value == null ? "" : value
                .replace('–', '-').replace('—', '-')
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"');
    }
}
