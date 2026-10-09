package com.capstone.assessment.v3.report.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel counterpart of {@link ReportPdfDocument}: the same Marka identity header, title
 * band, detail block, summary row, green table header and footer, laid out on a sheet that
 * also prints cleanly (A4 portrait, fit to width, repeated table header, Page X of Y).
 * Cell styles are cached per workbook - POI caps a workbook at 64k styles.
 */
final class ReportExcelSheet {

    private final XSSFWorkbook workbook;
    private final XSSFSheet sheet;
    private final int columnCount;
    private final Map<String, XSSFCellStyle> styles;
    private int row;

    ReportExcelSheet(XSSFWorkbook workbook, String sheetName, float[] columnWidthsInChars,
                     Map<String, XSSFCellStyle> sharedStyles) {
        this.workbook = workbook;
        this.sheet = workbook.createSheet(sheetName);
        this.columnCount = columnWidthsInChars.length;
        this.styles = sharedStyles;
        for (int i = 0; i < columnWidthsInChars.length; i++) {
            sheet.setColumnWidth(i, Math.round(columnWidthsInChars[i] * 256));
        }
        sheet.setDisplayGridlines(false);
        PrintSetup print = sheet.getPrintSetup();
        print.setPaperSize(PrintSetup.A4_PAPERSIZE);
        print.setLandscape(false);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.setFitToPage(true);
        sheet.setHorizontallyCenter(true);
    }

    static Map<String, XSSFCellStyle> newStyleCache() {
        return new HashMap<>();
    }

    int currentRow() {
        return row;
    }

    void header(String schoolName, String title) throws IOException {
        byte[] logo = logoPng();
        int picture = workbook.addPicture(logo, XSSFWorkbook.PICTURE_TYPE_PNG);
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        // A 48x48 px (36 pt, like the PDF logo) square over A1:A2: 48 px wide inside column A,
        // and exactly as tall as the school and system rows below (20 + 16 pt). Keep column A
        // at least ~7 characters (49 px) wide so it doesn't cover the school name. The size is
        // set on the anchor directly: Picture#resize scales the anchor's cells, not the image.
        XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, Units.pixelToEMU(48), 0, 0, 0, 0, 2);
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
        drawing.createPicture(anchor, picture);

        Row school = sheet.createRow(row++);
        school.setHeightInPoints(20);
        set(school, 1, schoolName == null ? "" : schoolName.toUpperCase(java.util.Locale.ROOT), style("school"));
        Row system = sheet.createRow(row++);
        system.setHeightInPoints(16);
        set(system, 1, "Marka", style("system"));
        row++;
        Row band = sheet.createRow(row);
        band.setHeightInPoints(26);
        for (int c = 0; c < columnCount; c++) {
            set(band, c, c == 0 ? title : "", style("title"));
        }
        merge(row, row, 0, columnCount - 1);
        row += 2;
    }

    void section(String label) {
        Row strip = sheet.createRow(row);
        strip.setHeightInPoints(18);
        for (int c = 0; c < columnCount; c++) {
            set(strip, c, c == 0 ? label.toUpperCase(java.util.Locale.ROOT) : "", style("section"));
        }
        merge(row, row, 0, columnCount - 1);
        row++;
    }

    /** "Label : value" pairs: left pair in columns [labelCol, valueCol..], right pair likewise. */
    void details(List<String[]> left, List<String[]> right, int leftLabel, int leftValueEnd,
                 int rightLabel, int rightValueEnd) {
        int rows = Math.max(left.size(), right.size());
        for (int i = 0; i < rows; i++) {
            Row line = sheet.createRow(row);
            line.setHeightInPoints(15);
            if (i < left.size()) {
                set(line, leftLabel, left.get(i)[0], style("label"));
                set(line, leftLabel + 1, left.get(i)[1], style("value"));
                if (leftValueEnd > leftLabel + 1) merge(row, row, leftLabel + 1, leftValueEnd);
            }
            if (i < right.size()) {
                set(line, rightLabel, right.get(i)[0], style("label"));
                set(line, rightLabel + 1, right.get(i)[1], style("value"));
                if (rightValueEnd > rightLabel + 1) merge(row, row, rightLabel + 1, rightValueEnd);
            }
            row++;
        }
        row++;
    }

    /** Summary cards as a label row, a big value row and a note row, each card spanning columns. */
    void cards(List<ReportPdfDocument.Card> cards, int[][] spans) {
        Row labels = sheet.createRow(row);
        Row values = sheet.createRow(row + 1);
        Row notes = sheet.createRow(row + 2);
        values.setHeightInPoints(22);
        for (int i = 0; i < cards.size(); i++) {
            int from = spans[i][0];
            int to = spans[i][1];
            for (int c = from; c <= to; c++) {
                set(labels, c, c == from ? cards.get(i).label() : "", style("cardLabel"));
                set(values, c, c == from ? cards.get(i).value() : "", style("cardValue"));
                set(notes, c, c == from ? nullToEmpty(cards.get(i).note()) : "", style("cardNote"));
            }
            if (to > from) {
                merge(row, row, from, to);
                merge(row + 1, row + 1, from, to);
                merge(row + 2, row + 2, from, to);
            }
        }
        row += 4;
    }

    void tableHeader(String[] headers) {
        Row header = sheet.createRow(row);
        header.setHeightInPoints(20);
        for (int c = 0; c < headers.length; c++) {
            set(header, c, headers[c], style("th"));
        }
        // Printed sheets repeat the table header on every page.
        sheet.setRepeatingRows(new CellRangeAddress(row, row, 0, headers.length - 1));
        row++;
    }

    /** Merges one column over a run of table rows, e.g. a competency spanning its students. */
    void mergeColumn(int firstRow, int lastRow, int column) {
        if (lastRow > firstRow) {
            sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, column, column));
            // A merged cell shows only its first cell's style; keep the label at the top.
            sheet.getRow(firstRow).getCell(column).setCellStyle(style("groupLabel"));
        }
    }

    /** One table row. Numbers stay numeric so the sheet can still be sorted and summed.
     *  Returns the sheet row index it was written to. */
    int tableRow(Object[] values, Color[] colors, boolean[] centered, String percentColumnsMask) {
        int written = row;
        Row line = sheet.createRow(row++);
        for (int c = 0; c < values.length; c++) {
            Color color = colors == null ? null : colors[c];
            boolean center = centered != null && centered[c];
            boolean percent = percentColumnsMask != null && c < percentColumnsMask.length()
                    && percentColumnsMask.charAt(c) == '%';
            Cell cell = line.createCell(c);
            Object value = values[c];
            if (value instanceof Number number) {
                // Percent columns hold the fraction (0.8800) under Excel's own 0.00% format, so
                // they display "88.00%" everywhere and still calculate correctly.
                cell.setCellValue(percent ? number.doubleValue() / 100.0 : number.doubleValue());
            } else {
                cell.setCellValue(value == null ? "" : value.toString());
            }
            cell.setCellStyle(bodyStyle(color, center, percent && value instanceof Number));
        }
        return written;
    }

    void note(String text) {
        Row line = sheet.createRow(row);
        set(line, 0, text, style("note"));
        merge(row, row, 0, columnCount - 1);
        row++;
    }

    /** Signature over printed name: blank rows to sign in, the name in capitals on the line, the
     *  role below - as on the PDF. */
    void signature(String name, String role, int column) {
        row += 3;
        Row nameRow = sheet.createRow(row++);
        nameRow.setHeightInPoints(18);
        set(nameRow, column, name == null ? "" : name.toUpperCase(java.util.Locale.ROOT), style("signName"));
        set(nameRow, column + 1, "", style("signName"));
        merge(nameRow.getRowNum(), nameRow.getRowNum(), column, column + 1);
        Row roleRow = sheet.createRow(row++);
        set(roleRow, column, role, style("signRole"));
        merge(roleRow.getRowNum(), roleRow.getRowNum(), column, column + 1);
    }

    void footer(String left) {
        sheet.getFooter().setLeft(left);
        sheet.getFooter().setRight("Marka  |  Page &P of &N");
    }

    void skip(int rows) {
        row += rows;
    }

    // ---------------------------------------------------------------- styles

    private XSSFCellStyle bodyStyle(Color color, boolean center, boolean percent) {
        String key = "td|" + (color == null ? "-" : Integer.toHexString(color.getRGB())) + "|" + center + "|" + percent;
        return styles.computeIfAbsent(key, k -> {
            XSSFCellStyle style = workbook.createCellStyle();
            XSSFFont font = workbook.createFont();
            font.setFontHeightInPoints((short) 10);
            if (color != null) {
                font.setBold(true);
                font.setColor(new XSSFColor(color, null));
            }
            style.setFont(font);
            thinBorders(style);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            style.setAlignment(center ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
            if (percent) {
                style.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
            }
            return style;
        });
    }

    private XSSFCellStyle style(String kind) {
        return styles.computeIfAbsent(kind, k -> {
            XSSFCellStyle style = workbook.createCellStyle();
            XSSFFont font = workbook.createFont();
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            switch (kind) {
                case "school" -> { font.setBold(true); font.setFontHeightInPoints((short) 14); }
                case "system" -> { font.setBold(true); font.setFontHeightInPoints((short) 10); font.setColor(xssf(ReportPdfDocument.BRAND)); }
                case "title" -> {
                    font.setBold(true); font.setFontHeightInPoints((short) 14); font.setColor(xssf(Color.WHITE));
                    fill(style, ReportPdfDocument.BRAND); style.setAlignment(HorizontalAlignment.CENTER);
                }
                case "section" -> {
                    font.setBold(true); font.setFontHeightInPoints((short) 10); font.setColor(xssf(ReportPdfDocument.BRAND));
                    fill(style, ReportPdfDocument.BRAND_SOFT);
                    style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN);
                    style.setTopBorderColor(xssf(ReportPdfDocument.BRAND_LINE)); style.setBottomBorderColor(xssf(ReportPdfDocument.BRAND_LINE));
                }
                case "label" -> { font.setBold(true); font.setFontHeightInPoints((short) 10); }
                case "value" -> font.setFontHeightInPoints((short) 10);
                case "cardLabel" -> { font.setFontHeightInPoints((short) 9); font.setColor(xssf(ReportPdfDocument.MUTED)); style.setAlignment(HorizontalAlignment.CENTER); }
                case "cardValue" -> { font.setBold(true); font.setFontHeightInPoints((short) 15); style.setAlignment(HorizontalAlignment.CENTER); }
                case "cardNote" -> { font.setFontHeightInPoints((short) 9); font.setColor(xssf(ReportPdfDocument.MUTED)); style.setAlignment(HorizontalAlignment.CENTER); }
                case "th" -> {
                    font.setBold(true); font.setFontHeightInPoints((short) 10); font.setColor(xssf(Color.WHITE));
                    fill(style, ReportPdfDocument.BRAND); style.setAlignment(HorizontalAlignment.CENTER);
                    style.setWrapText(true); thinBorders(style);
                }
                case "note" -> { font.setItalic(true); font.setFontHeightInPoints((short) 9); font.setColor(xssf(ReportPdfDocument.MUTED)); style.setWrapText(false); }
                case "signName" -> {
                    font.setBold(true); font.setFontHeightInPoints((short) 10); style.setAlignment(HorizontalAlignment.CENTER);
                    style.setVerticalAlignment(VerticalAlignment.BOTTOM);
                    style.setBorderBottom(BorderStyle.THIN); style.setBottomBorderColor(xssf(ReportPdfDocument.MUTED));
                }
                case "signRole" -> { font.setFontHeightInPoints((short) 9); font.setColor(xssf(ReportPdfDocument.MUTED)); style.setAlignment(HorizontalAlignment.CENTER); }
                case "groupLabel" -> {
                    font.setBold(true); font.setFontHeightInPoints((short) 10);
                    style.setVerticalAlignment(VerticalAlignment.TOP); style.setWrapText(true); thinBorders(style);
                }
                default -> font.setFontHeightInPoints((short) 10);
            }
            style.setFont(font);
            return style;
        });
    }

    private void thinBorders(XSSFCellStyle style) {
        XSSFColor grid = xssf(ReportPdfDocument.GRID);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(grid);
        style.setBottomBorderColor(grid);
        style.setLeftBorderColor(grid);
        style.setRightBorderColor(grid);
    }

    private void fill(XSSFCellStyle style, Color color) {
        style.setFillForegroundColor(xssf(color));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private static XSSFColor xssf(Color color) {
        return new XSSFColor(color, null);
    }

    private void set(Row target, int column, String value, XSSFCellStyle style) {
        Cell cell = target.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void merge(int firstRow, int lastRow, int firstColumn, int lastColumn) {
        if (lastColumn > firstColumn || lastRow > firstRow) {
            sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn));
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ---------------------------------------------------------------- logo image

    /** Rendered at 4x the 48 px it is shown at, so the mark stays crisp when printed. */
    private static final int LOGO_PIXELS = 192;
    private static byte[] cachedLogo;

    /** The Marka mark on its tile (same drawing as ReportPdfDocument#drawLogo) rendered once to PNG. */
    static synchronized byte[] logoPng() throws IOException {
        if (cachedLogo != null) {
            return cachedLogo;
        }
        float s = LOGO_PIXELS / 64f;
        BufferedImage image = new BufferedImage(LOGO_PIXELS, LOGO_PIXELS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(s, s);
            g.setColor(MarkaMark.TILE);
            g.fill(new RoundRectangle2D.Float(0, 0, 64, 64, 28, 28));
            g.setColor(MarkaMark.GREEN);
            g.fill(MarkaMark.onTile());
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            cachedLogo = output.toByteArray();
            return cachedLogo;
        }
    }
}
