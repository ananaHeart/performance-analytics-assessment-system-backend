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
import com.capstone.assessment.v3.report.service.MarkaMark;

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
    // Print palette (RGB 0-1): dark ink text, Marka green accents, grey lines dark enough for
    // school printers. Bubbles keep their original black outline for the scanner.
    private static final float[] INK = {0.10f, 0.13f, 0.16f};
    private static final float[] MUTED = {0.36f, 0.40f, 0.45f};
    private static final float[] BLACK = {0f, 0f, 0f};
    private static final float[] BRAND = {0f, 0.70f, 0.086f};
    private static final float[] BRAND_DARK = {0.05f, 0.40f, 0.14f};
    private static final float[] BRAND_TINT = {0.90f, 0.96f, 0.91f};
    private static final float[] CARD = {0.95f, 0.97f, 0.96f};
    private static final float[] LINE = {0.70f, 0.73f, 0.76f};
    private static final float[] WRITE_LINE = {0.52f, 0.57f, 0.62f};

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

    // Page-one header: everything stays above the body top (160 pt from the top edge), clear
    // of the corner markers, so no scanned position moves.
    private void drawPageOneHeader(
            PDPageContentStream content, float pageWidth, float pageHeight,
            DynamicSheetContext context, DynamicPage page
    ) throws IOException {
        float left = 42f;
        float right = pageWidth - left;
        float width = right - left;
        String schoolName = context == null || context.schoolName() == null || context.schoolName().isBlank()
                ? "SAN ROQUE NATIONAL HIGH SCHOOL" : context.schoolName();
        String assessmentName = context == null || context.assessmentName() == null || context.assessmentName().isBlank()
                ? "Assessment" : context.assessmentName();
        String subject = context == null || context.subject() == null ? "" : context.subject();
        String gradeSection = context == null || context.gradeSection() == null ? "" : context.gradeSection();

        // Identity row: logo, school name, title and page.
        MarkaMark.draw(content, left, pageHeight - 66, 26);
        setFill(content, INK);
        drawText(content, FONT_BOLD, 12.5f, left + 34, pageHeight - 51,
                fitText(schoolName.toUpperCase(Locale.ROOT), FONT_BOLD, 12.5f, width * 0.58f));
        setFill(content, MUTED);
        drawText(content, FONT_REGULAR, 7.5f, left + 34, pageHeight - 62, "Marka  |  Learner Answer Sheet");
        setFill(content, BRAND_DARK);
        drawRightText(content, FONT_BOLD, 12.5f, right, pageHeight - 51, "ANSWER SHEET");
        setFill(content, MUTED);
        drawRightText(content, FONT_REGULAR, 7.5f, right, pageHeight - 62,
                "Page " + page.pageNumber() + " of " + page.totalPages());
        content.setStrokingColor(BRAND[0], BRAND[1], BRAND[2]);
        content.setLineWidth(1.4f);
        content.moveTo(left, pageHeight - 71);
        content.lineTo(right, pageHeight - 71);
        content.stroke();

        // Assessment details in a light card.
        float cardTop = pageHeight - 77;
        float cardHeight = 27;
        setFill(content, CARD);
        content.addRect(left, cardTop - cardHeight, width, cardHeight);
        content.fill();
        String maxPoints = context == null ? "" : formatPoints(context.totalPoints());
        float[] ratios = {0.40f, 0.18f, 0.26f, 0.16f};
        String[] labels = {"ASSESSMENT", "SUBJECT", "GRADE & SECTION", "TOTAL POINTS"};
        String[] values = {assessmentName, subject, gradeSection, maxPoints};
        float cellX = left + 8;
        for (int i = 0; i < labels.length; i++) {
            float cellWidth = width * ratios[i];
            setFill(content, MUTED);
            drawText(content, FONT_BOLD, 6f, cellX, cardTop - 10, labels[i]);
            setFill(content, INK);
            drawText(content, FONT_BOLD, 9f, cellX, cardTop - 21,
                    fitText(values[i], FONT_BOLD, 9f, cellWidth - 12));
            cellX += cellWidth;
        }

        // What the learner writes by hand.
        float fieldsY = pageHeight - 124;
        float scoreWidth = 92;
        float dateWidth = 92;
        float lrnWidth = 124;
        float nameWidth = width - scoreWidth - dateWidth - lrnWidth - 30;
        float fieldX = left;
        drawField(content, "Name", fieldX, fieldsY, nameWidth);
        fieldX += nameWidth + 10;
        drawField(content, "LRN", fieldX, fieldsY, lrnWidth);
        fieldX += lrnWidth + 10;
        drawField(content, "Date", fieldX, fieldsY, dateWidth);
        fieldX += dateWidth + 10;
        drawField(content, "Score", fieldX, fieldsY, scoreWidth - 26);
        setFill(content, INK);
        drawText(content, FONT_BOLD, 8.5f, fieldX + scoreWidth - 24, fieldsY,
                "/ " + (context == null ? "" : formatNumber(context.totalPoints())));

        // How to answer.
        float instructionY = pageHeight - 151;
        setFill(content, BRAND_TINT);
        content.addRect(left, instructionY, width, 17);
        content.fill();
        setFill(content, BRAND_DARK);
        content.addRect(left, instructionY, 3, 17);
        content.fill();
        setFill(content, INK);
        drawText(content, FONT_REGULAR, 7.4f, left + 9, instructionY + 5.5f,
                fitText("Fully shade one bubble per item with a dark pencil or pen. Write only on the lines "
                        + "and inside the boxes. Do not fold or staple this sheet.",
                        FONT_REGULAR, 7.4f, width - 14));
        setFill(content, BLACK);
    }

    // Later pages: a slim header above the body top (70 pt from the top edge).
    private void drawContinuationHeader(PDPageContentStream content, float pageWidth, float pageHeight,
                                         int pageNumber, int totalPages) throws IOException {
        float left = 42f;
        float right = pageWidth - left;
        MarkaMark.draw(content, left, pageHeight - 60, 16);
        setFill(content, INK);
        drawText(content, FONT_BOLD, 9f, left + 22, pageHeight - 52, "ANSWER SHEET (continued)");
        setFill(content, MUTED);
        drawRightText(content, FONT_REGULAR, 8f, right, pageHeight - 52,
                "Page " + pageNumber + " of " + totalPages);
        content.setStrokingColor(BRAND[0], BRAND[1], BRAND[2]);
        content.setLineWidth(1f);
        content.moveTo(left, pageHeight - 63);
        content.lineTo(right, pageHeight - 63);
        content.stroke();
        setFill(content, BLACK);
    }

    // Part header strip. Instructions that do not fit beside the title move under it instead
    // of being cut off with "...".
    private void drawPartHeader(PDPageContentStream content, PartHeader header,
                                Map<String, PartPoints> headerPointsByPartName) throws IOException {
        float x = (float) header.x();
        float y = (float) header.y();
        float width = (float) header.width();
        float height = (float) header.height();
        setFill(content, BRAND_TINT);
        content.addRect(x, y, width, height);
        content.fill();
        setFill(content, BRAND_DARK);
        content.addRect(x, y, 3, height);
        content.fill();

        String title = header.title();
        PartPoints partPoints = headerPointsByPartName.get(partBaseName(title));
        String pointsText = partPoints == null ? null
                : formatPoints(partPoints.pointsPerItem(), partPoints.itemCount() > 1);
        String instruction = header.instructions() == null ? "" : header.instructions().trim();

        float titleX = x + 9;
        float titleEnd = titleX + textWidth(FONT_BOLD, 9.5f, title)
                + (pointsText == null ? 0 : 8 + textWidth(FONT_REGULAR, 8.5f, pointsText));
        float rightEdge = x + width - 8;
        boolean oneLine = instruction.isEmpty()
                || textWidth(FONT_REGULAR, 7f, instruction) <= rightEdge - titleEnd - 12;
        float titleBaseline = oneLine ? y + 8.5f : y + 13.5f;

        setFill(content, BRAND_DARK);
        drawText(content, FONT_BOLD, 9.5f, titleX, titleBaseline, title);
        if (pointsText != null) {
            setFill(content, MUTED);
            drawText(content, FONT_REGULAR, 8.5f, titleX + textWidth(FONT_BOLD, 9.5f, title) + 8,
                    titleBaseline, pointsText);
        }
        if (!instruction.isEmpty()) {
            setFill(content, INK);
            if (oneLine) {
                drawRightText(content, FONT_REGULAR, 7f, rightEdge, y + 8.5f, instruction);
            } else {
                drawText(content, FONT_REGULAR, 6.6f, titleX, y + 4.5f,
                        fitText(instruction, FONT_REGULAR, 6.6f, rightEdge - titleX));
            }
        }
        setFill(content, BLACK);
    }

    // draw_objective_region (py:1119-1137)
    private void drawObjectiveRegion(PDPageContentStream content, DynamicRegion region) throws IOException {
        Rectangle r = region.rectangle();
        float centerY = (float) (r.y() + (r.height() / 2));
        content.setStrokingColor(LINE[0], LINE[1], LINE[2]);
        content.setLineWidth(0.5f);
        content.moveTo((float) r.x(), (float) r.y());
        content.lineTo((float) (r.x() + r.width()), (float) r.y());
        content.stroke();

        setFill(content, INK);
        drawRightText(content, FONT_BOLD, 9.5f, (float) r.x() + 28, centerY - 3, region.globalItemNumber() + ".");
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
            content.setStrokingColor(WRITE_LINE[0], WRITE_LINE[1], WRITE_LINE[2]);
            content.setLineWidth(0.8f);
            content.addRect(x, y, width, height);
            content.fillAndStroke();
        }

        setFill(content, INK);
        drawText(content, FONT_BOLD, 9.5f, x + 8, y + height - 14, region.globalItemNumber() + ".");
        // Parts whose items are all worth the same show their points once in the part header.
        if (!headerPointsByPartName.containsKey(region.partName())) {
            drawRightText(content, FONT_REGULAR, 7, x + width - 8, y + height - 14,
                    formatPoints(region.maximumPoints(), false));
        }

        int lineCount = region.responseLineCount() == null ? 1 : region.responseLineCount();
        float lineTop = y + height - 25;
        float availableHeight = Math.max(12f, lineTop - (y + 10));
        float spacing = availableHeight / lineCount;
        content.setStrokingColor(WRITE_LINE[0], WRITE_LINE[1], WRITE_LINE[2]);
        content.setLineWidth(0.6f);
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

    // Footer: left of the QR code and below the body bottom (102 pt), so nothing scanned moves.
    // The short reference is the start of the manifest hash, to trace a printed sheet if needed.
    private void drawTechnicalFooter(PDPageContentStream content, float pageWidth, String manifestHash,
                                      int pageNumber, int totalPages, Rectangle qrRect) throws IOException {
        float left = 42f;
        float footerRight = (float) qrRect.x() - 12f;
        content.setStrokingColor(LINE[0], LINE[1], LINE[2]);
        content.setLineWidth(0.5f);
        content.moveTo(left, 66f);
        content.lineTo(footerRight, 66f);
        content.stroke();
        setFill(content, INK);
        drawText(content, FONT_BOLD, 7.5f, left, 54f, "Page " + pageNumber + " of " + totalPages);
        setFill(content, MUTED);
        drawText(content, FONT_REGULAR, 6.5f, left, 43f,
                fitText("Print at actual size (100%). Keep the corner squares and QR code clean.",
                        FONT_REGULAR, 6.5f, footerRight - left));
        String reference = manifestHash == null ? "" : manifestHash.substring(0, Math.min(8, manifestHash.length()));
        drawRightText(content, FONT_REGULAR, 6f, footerRight, 54f, "Marka  |  Ref " + reference);
        setFill(content, BLACK);
    }

    private void drawField(PDPageContentStream content, String label, float x, float y, float width)
            throws IOException {
        setFill(content, MUTED);
        drawText(content, FONT_BOLD, 7f, x, y, label.toUpperCase(Locale.ROOT));
        float valueX = x + textWidth(FONT_BOLD, 7f, label.toUpperCase(Locale.ROOT)) + 5;
        content.setStrokingColor(INK[0], INK[1], INK[2]);
        content.setLineWidth(0.7f);
        content.moveTo(valueX, y - 2);
        content.lineTo(x + width, y - 2);
        content.stroke();
    }

    /** "50" or "1.5" for the score field. */
    private static String formatNumber(double points) {
        return points == Math.floor(points)
                ? Long.toString((long) points)
                : String.format(Locale.ROOT, "%.1f", points);
    }

    private static void setFill(PDPageContentStream content, float[] color) throws IOException {
        content.setNonStrokingColor(color[0], color[1], color[2]);
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
