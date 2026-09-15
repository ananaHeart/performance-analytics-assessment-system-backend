package com.capstone.assessment.v2.assessment.service;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentPartResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentQuestionResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2OmrSheetPrintServiceTest {

    private static final float POINTS_PER_INCH = 72f;
    private static final float RENDER_DPI = 300f;

    private final V2OmrSheetPrintService printService = new V2OmrSheetPrintService();

    @Test
    void rendersScannerCompatiblePdfWithDecodableCompactQr() throws Exception {
        byte[] pdf = printService.renderSheet(assessment("active", List.of(6, 4), "multiple_choice"));
        writeSampleWhenRequested(pdf);

        assertTrue(pdf.length > 1_000);
        assertEquals("%PDF", new String(pdf, 0, 4));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(V2OmrSheetPrintService.PAGE_WIDTH,
                    document.getPage(0).getMediaBox().getWidth(), 0.001f);
            assertEquals(V2OmrSheetPrintService.PAGE_HEIGHT,
                    document.getPage(0).getMediaBox().getHeight(), 0.001f);
            assertSameBounds(document.getPage(0).getMediaBox(), document.getPage(0).getCropBox());
            assertSameBounds(document.getPage(0).getMediaBox(), document.getPage(0).getTrimBox());
            assertSameBounds(document.getPage(0).getMediaBox(), document.getPage(0).getBleedBox());
            assertEquals("None", document.getDocumentCatalog().getViewerPreferences().getPrintScaling());
            assertEquals("MediaBox", document.getDocumentCatalog().getViewerPreferences().getPrintArea());
            assertEquals("MediaBox", document.getDocumentCatalog().getViewerPreferences().getPrintClip());

            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("BUBBLE ANSWER SHEET"));
            assertTrue(text.contains("Options: A/B/C/D"));
            assertFalse(text.contains("ID/LRN"));
            assertFalse(text.contains("ClassList"));
            assertFalse(text.contains("Score"));
            assertFalse(text.contains("QID"));

            Result decodedQr = decodeQr(document);
            assertEquals(V2OmrSheetPrintService.qrPayload(1006L), decodedQr.getText());

            BufferedImage renderedPage = new PDFRenderer(document).renderImageWithDPI(0, RENDER_DPI);
            assertRegistrationMarker(renderedPage, 20, 20);
            assertRegistrationMarker(renderedPage, V2OmrSheetPrintService.PAGE_WIDTH - 35, 20);
            assertRegistrationMarker(renderedPage, 20, V2OmrSheetPrintService.PAGE_HEIGHT - 35);
            assertRegistrationMarker(
                    renderedPage,
                    V2OmrSheetPrintService.PAGE_WIDTH - 35,
                    V2OmrSheetPrintService.PAGE_HEIGHT - 35
            );
        }
    }

    private void assertSameBounds(PDRectangle expected, PDRectangle actual) {
        assertEquals(expected.getLowerLeftX(), actual.getLowerLeftX(), 0.001f);
        assertEquals(expected.getLowerLeftY(), actual.getLowerLeftY(), 0.001f);
        assertEquals(expected.getWidth(), actual.getWidth(), 0.001f);
        assertEquals(expected.getHeight(), actual.getHeight(), 0.001f);
    }

    private void assertRegistrationMarker(BufferedImage page, float markerX, float markerY) {
        int outerPixel = pdfPixel(page, markerX + 2, markerY + 2);
        int innerPixel = pdfPixel(page, markerX + 7.5f, markerY + 7.5f);

        assertTrue(red(outerPixel) < 32 && green(outerPixel) < 32 && blue(outerPixel) < 32);
        assertTrue(red(innerPixel) > 223 && green(innerPixel) > 223 && blue(innerPixel) > 223);
    }

    private int pdfPixel(BufferedImage page, float pointX, float pointY) {
        float scale = RENDER_DPI / POINTS_PER_INCH;
        int pixelX = Math.round(pointX * scale);
        int pixelY = Math.round((V2OmrSheetPrintService.PAGE_HEIGHT - pointY) * scale);
        return page.getRGB(pixelX, pixelY);
    }

    private int red(int rgb) {
        return (rgb >> 16) & 0xff;
    }

    private int green(int rgb) {
        return (rgb >> 8) & 0xff;
    }

    private int blue(int rgb) {
        return rgb & 0xff;
    }

    private void writeSampleWhenRequested(byte[] pdf) throws Exception {
        String requestedOutput = System.getProperty("omr.sample.output");
        if (requestedOutput == null || requestedOutput.isBlank()) {
            return;
        }
        Path output = Path.of(requestedOutput).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output, pdf);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage rendered = new PDFRenderer(document).renderImageWithDPI(0, 150);
            String fileName = output.getFileName().toString();
            String pngName = fileName.endsWith(".pdf")
                    ? fileName.substring(0, fileName.length() - 4) + ".png"
                    : fileName + ".png";
            ImageIO.write(rendered, "png", output.resolveSibling(pngName).toFile());
        }
    }

    @Test
    void usesExactMobileScannerBubbleCoordinates() {
        assertEquals(6.4f, V2OmrSheetPrintService.BUBBLE_RADIUS, 0.0001f);
        assertEquals(79f, V2OmrSheetPrintService.BUBBLE_X_CENTERS[0], 0.0001f);
        assertEquals(103f, V2OmrSheetPrintService.BUBBLE_X_CENTERS[1], 0.0001f);
        assertEquals(127f, V2OmrSheetPrintService.BUBBLE_X_CENTERS[2], 0.0001f);
        assertEquals(151f, V2OmrSheetPrintService.BUBBLE_X_CENTERS[3], 0.0001f);
        assertEquals(604.334f, V2OmrSheetPrintService.bubbleCenterY(0), 0.0001f);
        assertEquals(306.334f, V2OmrSheetPrintService.bubbleCenterY(9), 0.0001f);
    }

    @Test
    void rejectsDraftAssessment() {
        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> printService.renderSheet(assessment("draft", List.of(10), "multiple_choice"))
        );

        assertEquals("ASSESSMENT_NOT_ACTIVE", exception.getCode());
    }

    @Test
    void rejectsUnsupportedItemCount() {
        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> printService.renderSheet(assessment("active", List.of(9), "multiple_choice"))
        );

        assertEquals("UNSUPPORTED_OMR_ITEM_COUNT", exception.getCode());
    }

    @Test
    void rejectsUnsupportedPartType() {
        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> printService.renderSheet(assessment("active", List.of(5, 5), "true_false"))
        );

        assertEquals("UNSUPPORTED_OMR_PART_TYPE", exception.getCode());
    }

    @Test
    void rejectsFiveOptionAssessment() {
        V2AssessmentResponse assessment = assessment("active", List.of(10), "multiple_choice");
        V2AssessmentPartResponse originalPart = assessment.parts().get(0);
        List<V2AssessmentQuestionResponse> questions = new ArrayList<>(originalPart.questions());
        V2AssessmentQuestionResponse original = questions.get(0);
        questions.set(0, new V2AssessmentQuestionResponse(
                original.questionId(),
                original.itemNumber(),
                original.questionText(),
                original.optionA(),
                original.optionB(),
                original.optionC(),
                original.optionD(),
                "E",
                original.correctOption(),
                original.skillIds()
        ));
        V2AssessmentResponse fiveOptionAssessment = withParts(assessment, List.of(new V2AssessmentPartResponse(
                originalPart.testPartId(),
                originalPart.partOrder(),
                originalPart.partName(),
                originalPart.partType(),
                originalPart.numberOfItems(),
                originalPart.pointsPerItem(),
                questions
        )));

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> printService.renderSheet(fiveOptionAssessment)
        );

        assertEquals("UNSUPPORTED_OMR_OPTIONS", exception.getCode());
    }

    private Result decodeQr(PDDocument document) throws Exception {
        BufferedImage page = new PDFRenderer(document).renderImageWithDPI(0, RENDER_DPI);
        float scale = RENDER_DPI / POINTS_PER_INCH;
        float qrX = V2OmrSheetPrintService.PAGE_WIDTH - 118;
        float qrY = V2OmrSheetPrintService.PAGE_HEIGHT - 143;
        float qrSize = 83;
        int cropX = Math.round(qrX * scale);
        int cropY = Math.round((V2OmrSheetPrintService.PAGE_HEIGHT - qrY - qrSize) * scale);
        int cropSize = Math.round(qrSize * scale);
        BufferedImage qrImage = page.getSubimage(cropX, cropY, cropSize, cropSize);
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(qrImage))
        );
        return new MultiFormatReader().decode(bitmap);
    }

    private V2AssessmentResponse assessment(String status, List<Integer> partSizes, String partType) {
        List<V2AssessmentPartResponse> parts = new ArrayList<>();
        int nextQuestion = 1;
        for (int partIndex = 0; partIndex < partSizes.size(); partIndex++) {
            int partSize = partSizes.get(partIndex);
            List<V2AssessmentQuestionResponse> questions = new ArrayList<>();
            for (int item = 1; item <= partSize; item++) {
                questions.add(new V2AssessmentQuestionResponse(
                        9000L + nextQuestion,
                        item,
                        "Question " + nextQuestion,
                        "A",
                        "B",
                        "C",
                        "D",
                        null,
                        "A",
                        List.of(100L)
                ));
                nextQuestion++;
            }
            parts.add(new V2AssessmentPartResponse(
                    201L + partIndex,
                    partIndex + 1,
                    "Part " + (partIndex + 1),
                    partType,
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
                partSizes.stream().mapToInt(Integer::intValue).sum(),
                status,
                Instant.parse("2026-08-11T02:00:00Z"),
                Instant.parse("2026-08-11T02:00:00Z"),
                parts
        );
    }

    private V2AssessmentResponse withParts(
            V2AssessmentResponse assessment,
            List<V2AssessmentPartResponse> parts
    ) {
        return new V2AssessmentResponse(
                assessment.testId(),
                assessment.classAssignmentId(),
                assessment.classId(),
                assessment.academicYearId(),
                assessment.yearName(),
                assessment.gradeLevelId(),
                assessment.gradeLevelName(),
                assessment.sectionId(),
                assessment.sectionName(),
                assessment.subjectId(),
                assessment.subjectName(),
                assessment.termPeriodId(),
                assessment.termName(),
                assessment.testName(),
                assessment.testType(),
                assessment.testDate(),
                assessment.instructions(),
                assessment.totalItems(),
                assessment.status(),
                assessment.createdAt(),
                assessment.updatedAt(),
                parts
        );
    }
}
