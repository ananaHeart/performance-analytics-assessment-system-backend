package com.capstone.assessment.v3.answersheet.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3AnswerSheetPdfRendererTest {

    private static final float PAGE_WIDTH = 595.276f;
    private static final float PAGE_HEIGHT = 841.890f;
    private static final float RENDER_SCALE = 3f;

    private final V3AnswerSheetPdfRenderer renderer = new V3AnswerSheetPdfRenderer();

    @Test
    void rendersExactA4MarkersAndDecodableLegacyQr() throws Exception {
        byte[] pdfBytes = renderer.render(V3AnswerSheetTestFixtures.generationPlan());

        assertTrue(pdfBytes.length > 1_000);
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(PAGE_WIDTH, document.getPage(0).getMediaBox().getWidth(), 0.001f);
            assertEquals(PAGE_HEIGHT, document.getPage(0).getMediaBox().getHeight(), 0.001f);

            BufferedImage image = new PDFRenderer(document)
                    .renderImage(0, RENDER_SCALE, ImageType.RGB);
            assertDark(image, 22, 22);
            assertDark(image, PAGE_WIDTH - 22, 22);
            assertDark(image, 22, PAGE_HEIGHT - 22);
            assertDark(image, PAGE_WIDTH - 22, PAGE_HEIGHT - 22);

            BufferedImage qr = cropPdfRectangle(image, 477.276f, 698.890f, 83f, 83f);
            Result decoded = new MultiFormatReader().decode(new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(qr))
            ));
            assertEquals(V3AnswerSheetService.legacyFixedQrPayload(1006L), decoded.getText());

            if (Boolean.getBoolean("v3.writePdfFixture")) {
                Path preview = Path.of("tmp", "pdfs", "V3_VALIDATED_A4_10_MC_FIXTURE.png");
                Files.createDirectories(preview.getParent());
                ImageIO.write(image, "png", preview.toFile());
            }
        }

        if (Boolean.getBoolean("v3.writePdfFixture")) {
            Path output = Path.of("output", "pdf", "V3_VALIDATED_A4_10_MC_FIXTURE.pdf");
            Files.createDirectories(output.getParent());
            Files.write(output, pdfBytes);
        }
    }

    private void assertDark(BufferedImage image, float xPoints, float yPoints) {
        int x = Math.round(xPoints * RENDER_SCALE);
        int y = Math.round((PAGE_HEIGHT - yPoints) * RENDER_SCALE);
        int rgb = image.getRGB(x, y);
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        assertTrue(red < 80 && green < 80 && blue < 80,
                "Expected a dark registration-marker pixel at PDF point " + xPoints + "," + yPoints);
    }

    private BufferedImage cropPdfRectangle(
            BufferedImage image,
            float x,
            float y,
            float width,
            float height
    ) {
        int imageX = Math.round(x * RENDER_SCALE);
        int imageY = Math.round((PAGE_HEIGHT - y - height) * RENDER_SCALE);
        int imageWidth = Math.round(width * RENDER_SCALE);
        int imageHeight = Math.round(height * RENDER_SCALE);
        return image.getSubimage(imageX, imageY, imageWidth, imageHeight);
    }
}
