package com.capstone.assessment.v3.report.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkaMarkTest {

    private static final int GREEN = 0x00B316;
    private static final int TILE = 0xE6F8E8;

    @Test
    void markSpansSixToFiftyEightOnTheTileLikeTheWebFavicon() {
        Rectangle2D bounds = MarkaMark.onTile().getBounds2D();
        assertEquals(6, bounds.getMinX(), 0.01);
        assertEquals(6, bounds.getMinY(), 0.01);
        assertEquals(58, bounds.getMaxX(), 0.01);
        assertEquals(58, bounds.getMaxY(), 0.01);
    }

    @Test
    void excelLogoIsTheGreenMarkOnTheTile() throws Exception {
        BufferedImage logo = ImageIO.read(new ByteArrayInputStream(ReportExcelSheet.logoPng()));
        assertEquals(192, logo.getWidth());
        assertEquals(192, logo.getHeight());
        float s = 192 / 64f;
        // The middle of the frame's left bar, then plain tile between the bar and the edge.
        assertEquals(GREEN, logo.getRGB(Math.round(frameLeftBarX() * s), Math.round(32 * s)) & 0xFFFFFF);
        assertEquals(TILE, logo.getRGB(Math.round(2.5f * s), Math.round(32 * s)) & 0xFFFFFF);
    }

    @Test
    void excelHeaderShowsTheLogoAsA48PixelSquareOverA1A2() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ReportExcelSheet sheet = new ReportExcelSheet(workbook, "Report", new float[]{7f, 30f},
                    ReportExcelSheet.newStyleCache());
            sheet.header("Test School", "ITEM ANALYSIS");
            XSSFSheet xlsx = workbook.getSheetAt(0);
            XSSFPicture logo = (XSSFPicture) xlsx.getDrawingPatriarch().getShapes().get(0);
            XSSFClientAnchor anchor = logo.getClientAnchor();

            assertEquals(0, anchor.getCol1());
            assertEquals(0, anchor.getRow1());
            assertEquals(0, anchor.getCol2());
            assertEquals(Units.pixelToEMU(48), anchor.getDx2());
            assertEquals(2, anchor.getRow2());
            assertEquals(0, anchor.getDy2());
            // Rows 1-2 together are 48 px tall, so the square isn't stretched.
            float rowsPoints = xlsx.getRow(0).getHeightInPoints() + xlsx.getRow(1).getHeightInPoints();
            assertEquals(48, Units.pointsToPixel(rowsPoints), 0.01);
            assertTrue(xlsx.getColumnWidthInPixels(0) >= 48);
        }
    }

    @Test
    void pdfHeaderDrawsTheGreenMarkOnFirstAndContinuationPages() throws Exception {
        ReportPdfDocument pdf = new ReportPdfDocument("Test School", "ITEM ANALYSIS", "Footer");
        while (!pdf.ensure(400)) {
            pdf.gap(400);
        }
        try (PDDocument document = Loader.loadPDF(pdf.finish())) {
            assertEquals(2, document.getNumberOfPages());
            float scale = 4f;
            PDFRenderer renderer = new PDFRenderer(document);
            // The logo's top-left corner sits at (40, 34) from the page's top-left: 36 pt on
            // the first page, 18 pt in the compact continuation header.
            assertFrameIsGreen(renderer.renderImage(0, scale, ImageType.RGB), scale, 36);
            assertFrameIsGreen(renderer.renderImage(1, scale, ImageType.RGB), scale, 18);
        }
    }

    private static void assertFrameIsGreen(BufferedImage page, float scale, float logoSize) {
        float unit = logoSize / 64f;
        int x = Math.round((40 + frameLeftBarX() * unit) * scale);
        int y = Math.round((34 + 32 * unit) * scale);
        assertEquals(GREEN, page.getRGB(x, y) & 0xFFFFFF);
    }

    /** The centre of the master's left frame bar (x 4..9.34), placed on the tile. */
    private static float frameLeftBarX() {
        return 32 + (6.67f - 32) * 52f / 56f;
    }
}
