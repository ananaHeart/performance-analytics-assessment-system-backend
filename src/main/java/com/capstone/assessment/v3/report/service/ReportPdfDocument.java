package com.capstone.assessment.v3.report.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared page frame for the teacher-side report PDFs: one school identity header and
 * footer for every report, while each report composes its own body from sections, detail
 * boxes, summary cards and tables. Mostly white paper; green only for the title band,
 * section labels and table headers; status meaning is shown with colored text only.
 * Not thread-safe: create one per export.
 */
final class ReportPdfDocument {

    static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    static final float MARGIN = 40f;
    static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float TOP = PAGE_HEIGHT - 34f;
    /** Body content never goes below this line; the footer lives underneath it. */
    private static final float BODY_BOTTOM = 52f;

    static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final PDFont ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    // The web dashboard's brand tokens (--primary-dark / --primary-soft), so print matches screen.
    static final Color BRAND = new Color(47, 125, 22);
    static final Color BRAND_SOFT = new Color(240, 249, 236);
    static final Color BRAND_LINE = new Color(186, 222, 172);
    static final Color GRID = new Color(214, 221, 216);
    static final Color TEXT = new Color(30, 34, 38);
    static final Color MUTED = new Color(105, 112, 120);
    static final Color GOOD = new Color(46, 125, 50);
    static final Color AMBER = new Color(183, 121, 31);
    static final Color ORANGE = new Color(224, 108, 0);
    static final Color BAD = new Color(198, 40, 40);

    enum Align { LEFT, CENTER, RIGHT }

    record Column(String header, float width, Align align, boolean wrap) {
        static Column left(String header, float width) { return new Column(header, width, Align.LEFT, true); }
        static Column center(String header, float width) { return new Column(header, width, Align.CENTER, false); }
        static Column centerWrap(String header, float width) { return new Column(header, width, Align.CENTER, true); }
    }

    record Cell(String text, Color color, boolean bold) {
        static Cell of(String text) { return new Cell(text, null, false); }
        static Cell colored(String text, Color color) { return new Cell(text, color, color != null); }
    }

    record Card(String label, String value, String note, Color valueColor) {
        Card(String label, String value, String note) {
            this(label, value, note, null);
        }
    }

    private static final float BODY_SIZE = 8.8f;
    private static final float LINE = 11f;

    private final PDDocument document = new PDDocument();
    private final String schoolName;
    private final String title;
    private final String footerLeft;
    private PDPageContentStream content;
    private float y;
    /** Redrawn at the top of a new page while a table is open, so headers repeat. */
    private Runnable continuation;

    ReportPdfDocument(String schoolName, String title, String footerLeft) throws IOException {
        this.schoolName = schoolName == null || schoolName.isBlank() ? "School" : schoolName;
        this.title = title;
        this.footerLeft = footerLeft;
        addPage(true);
    }

    // ---------------------------------------------------------------- page frame

    private void addPage(boolean first) throws IOException {
        if (content != null) {
            content.close();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        if (first) {
            drawFullHeader();
        } else {
            drawCompactHeader();
        }
    }

    private void drawFullHeader() throws IOException {
        float logoSize = 36f;
        drawLogo(MARGIN, TOP - logoSize, logoSize);
        text(BOLD, 13.5f, TEXT, MARGIN + logoSize + 10, TOP - 15, upper(schoolName));
        text(BOLD, 9.5f, BRAND, MARGIN + logoSize + 10, TOP - 29, "Marka");
        hline(TOP - logoSize - 8, MARGIN, MARGIN + CONTENT_WIDTH, GRID, 0.6f);

        float bandTop = TOP - logoSize - 16;
        float bandHeight = 24f;
        fillRect(MARGIN, bandTop - bandHeight, CONTENT_WIDTH, bandHeight, BRAND);
        centered(BOLD, 14f, Color.WHITE, MARGIN + CONTENT_WIDTH / 2, bandTop - 16.5f, title);
        y = bandTop - bandHeight - 12;
    }

    private void drawCompactHeader() throws IOException {
        float logoSize = 18f;
        drawLogo(MARGIN, TOP - logoSize, logoSize);
        text(BOLD, 9f, TEXT, MARGIN + logoSize + 7, TOP - 12.5f, upper(schoolName));
        rightText(BOLD, 9f, BRAND, MARGIN + CONTENT_WIDTH, TOP - 12.5f, title);
        hline(TOP - logoSize - 6, MARGIN, MARGIN + CONTENT_WIDTH, GRID, 0.6f);
        y = TOP - logoSize - 18;
    }

    /** Starts a new page if fewer than {@code height} points remain for the body.
     *  Returns true when a page break happened. */
    boolean ensure(float height) throws IOException {
        if (y - height < BODY_BOTTOM) {
            addPage(false);
            if (continuation != null) {
                continuation.run();
            }
            return true;
        }
        return false;
    }

    void gap(float points) {
        y -= points;
    }

    byte[] finish() throws IOException {
        content.close();
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            try (PDPageContentStream footer = new PDPageContentStream(
                    document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                PDPageContentStream saved = content;
                content = footer;
                hline(40f, MARGIN, MARGIN + CONTENT_WIDTH, GRID, 0.6f);
                text(ITALIC, 7.5f, MUTED, MARGIN, 29f, footerLeft);
                rightText(REGULAR, 7.5f, MUTED, MARGIN + CONTENT_WIDTH, 29f,
                        "Marka   |   Page " + (i + 1) + " of " + total);
                content = saved;
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.save(output);
            document.close();
            return output.toByteArray();
        }
    }

    // ---------------------------------------------------------------- body blocks

    /** A light green label strip, optionally numbered, that opens a section. */
    void section(Integer number, String label) throws IOException {
        ensure(18 + 40);
        float height = 18f;
        fillRect(MARGIN, y - height, CONTENT_WIDTH, height, BRAND_SOFT);
        strokeRect(MARGIN, y - height, CONTENT_WIDTH, height, BRAND_LINE, 0.6f);
        float textX = MARGIN + 8;
        if (number != null) {
            circle(MARGIN + 15, y - height / 2, 6.5f, BRAND);
            centered(BOLD, 8f, Color.WHITE, MARGIN + 15, y - height / 2 - 2.8f, number.toString());
            textX = MARGIN + 27;
        }
        text(BOLD, 9.2f, BRAND, textX, y - 12.5f, upper(label));
        y -= height;
    }

    /** Bordered two-column "Label : value" box directly under a section strip. */
    void details(List<String[]> left, List<String[]> right) throws IOException {
        float half = CONTENT_WIDTH / 2;
        float labelWidth = 80f;
        float valueWidth = half - labelWidth - 24;
        int rows = Math.max(left.size(), right.size());
        List<Float> heights = new ArrayList<>();
        float total = 12;
        for (int i = 0; i < rows; i++) {
            int lines = Math.max(
                    i < left.size() ? wrap(left.get(i)[1], REGULAR, BODY_SIZE, valueWidth, 2).size() : 1,
                    i < right.size() ? wrap(right.get(i)[1], REGULAR, BODY_SIZE, valueWidth, 2).size() : 1);
            heights.add(lines * LINE + 3);
            total += lines * LINE + 3;
        }
        ensure(total);
        float top = y;
        strokeRect(MARGIN, top - total, CONTENT_WIDTH, total, GRID, 0.6f);
        vline(MARGIN + half, top - 5, top - total + 5, GRID, 0.6f);
        float rowY = top - 6;
        for (int i = 0; i < rows; i++) {
            if (i < left.size()) {
                detailRow(MARGIN + 10, rowY, left.get(i), labelWidth, valueWidth);
            }
            if (i < right.size()) {
                detailRow(MARGIN + half + 10, rowY, right.get(i), labelWidth, valueWidth);
            }
            rowY -= heights.get(i);
        }
        y = top - total;
    }

    private void detailRow(float x, float rowTop, String[] pair, float labelWidth, float valueWidth) throws IOException {
        text(BOLD, BODY_SIZE, TEXT, x, rowTop - 9, pair[0]);
        text(REGULAR, BODY_SIZE, MUTED, x + labelWidth, rowTop - 9, ":");
        List<String> lines = wrap(pair[1], REGULAR, BODY_SIZE, valueWidth, 2);
        for (int i = 0; i < lines.size(); i++) {
            text(REGULAR, BODY_SIZE, TEXT, x + labelWidth + 10, rowTop - 9 - i * LINE, lines.get(i));
        }
    }

    /** A bordered row of equal summary cells: small label, big value, small note. */
    void cards(List<Card> cards) throws IOException {
        float height = 56f;
        ensure(height);
        float width = CONTENT_WIDTH / cards.size();
        strokeRect(MARGIN, y - height, CONTENT_WIDTH, height, GRID, 0.6f);
        for (int i = 0; i < cards.size(); i++) {
            float center = MARGIN + width * i + width / 2;
            if (i > 0) {
                vline(MARGIN + width * i, y - 8, y - height + 8, GRID, 0.6f);
            }
            Card card = cards.get(i);
            centered(REGULAR, 8f, MUTED, center, y - 14, card.label());
            centered(BOLD, 15f, card.valueColor() == null ? TEXT : card.valueColor(), center, y - 34, card.value());
            if (card.note() != null) {
                centered(REGULAR, 7.8f, MUTED, center, y - 47, card.note());
            }
        }
        y -= height;
    }

    /** Draws a table; text columns wrap instead of being cut, and the header row repeats
     *  on every page the table continues onto. */
    void table(List<Column> columns, List<Cell[]> rows) throws IOException {
        table(columns, rows, false);
    }

    /** With groupFirstColumn, a row whose first cell is blank continues the group above it,
     *  like a merged cell: the group label prints once as one block at the top of the group,
     *  no line splits the group, the group's last row grows if the label is taller than its
     *  rows, and a group that continues on a new page repeats its label there. */
    void table(List<Column> columns, List<Cell[]> rows, boolean groupFirstColumn) throws IOException {
        float headerHeight = 18f;
        ensure(headerHeight + 22);
        drawTableHeader(columns, headerHeight);
        continuation = () -> {
            try {
                drawTableHeader(columns, headerHeight);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
        try {
            Cell groupLabel = null;
            List<String> labelLines = List.of();
            float labelHeight = 0;
            float groupHeightOnPage = 0;
            boolean labelDrawn = true;
            for (int r = 0; r < rows.size(); r++) {
                Cell[] row = rows.get(r);
                boolean startsGroup = groupFirstColumn && startsGroup(row);
                if (startsGroup) {
                    groupLabel = row[0];
                    labelLines = wrap(groupLabel.text(), groupLabel.bold() ? BOLD : REGULAR, BODY_SIZE,
                            columns.get(0).width() - 10, 6);
                    labelHeight = labelLines.size() * LINE + 7;
                    groupHeightOnPage = 0;
                    labelDrawn = false;
                }
                boolean lastOfGroup = !(groupFirstColumn && r + 1 < rows.size() && !startsGroup(rows.get(r + 1)));

                List<List<String>> lines = new ArrayList<>();
                int maxLines = 1;
                for (int c = 0; c < columns.size(); c++) {
                    if (groupFirstColumn && c == 0) {
                        lines.add(List.of());
                        continue;
                    }
                    Column column = columns.get(c);
                    Cell cell = cellAt(row, c);
                    List<String> cellLines = wrap(cell.text(), cell.bold() ? BOLD : REGULAR, BODY_SIZE,
                            column.width() - 10, column.wrap() ? 3 : 1);
                    lines.add(cellLines);
                    maxLines = Math.max(maxLines, cellLines.size());
                }
                float rowHeight = Math.max(18f, maxLines * LINE + 7);
                boolean grouped = groupFirstColumn && groupLabel != null;
                // The whole label block must fit on the page where it is drawn.
                float needed = grouped && !labelDrawn ? Math.max(rowHeight, labelHeight) : rowHeight;
                if (ensure(needed) && grouped && !startsGroup) {
                    labelDrawn = false;
                    groupHeightOnPage = 0;
                }
                if (grouped && lastOfGroup) {
                    float labelLeft = labelDrawn ? labelHeight - groupHeightOnPage : labelHeight;
                    rowHeight = Math.max(rowHeight, labelLeft);
                }

                float x = MARGIN;
                for (int c = 0; c < columns.size(); c++) {
                    Column column = columns.get(c);
                    if (groupFirstColumn && c == 0) {
                        if (grouped && !labelDrawn) {
                            PDFont font = groupLabel.bold() ? BOLD : REGULAR;
                            Color color = groupLabel.color() == null ? TEXT : groupLabel.color();
                            for (int l = 0; l < labelLines.size(); l++) {
                                aligned(font, BODY_SIZE, color, x, column.width(), y - 12.2f - l * LINE,
                                        labelLines.get(l), column.align());
                            }
                            labelDrawn = true;
                        }
                        x += column.width();
                        continue;
                    }
                    Cell cell = cellAt(row, c);
                    PDFont font = cell.bold() ? BOLD : REGULAR;
                    Color color = cell.color() == null ? TEXT : cell.color();
                    List<String> cellLines = lines.get(c);
                    // Shorter cells sit vertically centered next to a wrapped neighbour.
                    int rowLines = Math.max(1, (int) ((rowHeight - 7) / LINE));
                    float offset = (rowLines - cellLines.size()) * LINE / 2;
                    for (int l = 0; l < cellLines.size(); l++) {
                        aligned(font, BODY_SIZE, color, x, column.width(), y - 12.2f - offset - l * LINE,
                                cellLines.get(l), column.align());
                    }
                    x += column.width();
                }
                float lineStart = lastOfGroup ? MARGIN : MARGIN + columns.get(0).width();
                hline(y - rowHeight, lineStart, MARGIN + CONTENT_WIDTH, GRID, 0.5f);
                verticalGrid(columns, y, y - rowHeight);
                y -= rowHeight;
                groupHeightOnPage += rowHeight;
            }
        } finally {
            continuation = null;
        }
    }

    private static boolean startsGroup(Cell[] row) {
        return row.length > 0 && row[0] != null && !row[0].text().isBlank();
    }

    private static Cell cellAt(Cell[] row, int column) {
        return column < row.length && row[column] != null ? row[column] : Cell.of("");
    }

    private void drawTableHeader(List<Column> columns, float height) throws IOException {
        fillRect(MARGIN, y - height, CONTENT_WIDTH, height, BRAND);
        float x = MARGIN;
        for (Column column : columns) {
            aligned(BOLD, 8.4f, Color.WHITE, x, column.width(), y - 12.3f, column.header(), column.align());
            x += column.width();
        }
        y -= height;
    }

    private void verticalGrid(List<Column> columns, float top, float bottom) throws IOException {
        float x = MARGIN;
        vline(x, top, bottom, GRID, 0.5f);
        for (Column column : columns) {
            x += column.width();
            vline(x, top, bottom, GRID, 0.5f);
        }
    }

    /** Small wrapped text, e.g. a legend or an empty-state message. */
    void note(String message) throws IOException {
        List<String> lines = wrap(message, ITALIC, 7.8f, CONTENT_WIDTH, 6);
        ensure(lines.size() * 10 + 8);
        y -= 6;
        for (String line : lines) {
            y -= 10;
            text(ITALIC, 7.8f, MUTED, MARGIN, y + 2, line);
        }
    }

    /** One line of small muted text, e.g. a confidential identifier kept out of the spotlight. */
    void smallPrint(String message) throws IOException {
        ensure(14);
        y -= 11;
        text(REGULAR, 7f, MUTED, MARGIN + 2, y + 2, message);
    }

    /** Two bordered side-by-side bullet lists, e.g. Strengths | Areas for Improvement. */
    void twoLists(String leftTitle, List<String> leftItems, String rightTitle, List<String> rightItems) throws IOException {
        float gutter = 10f;
        float boxWidth = (CONTENT_WIDTH - gutter) / 2;
        float textWidth = boxWidth - 26;
        List<List<String>> left = new ArrayList<>();
        List<List<String>> right = new ArrayList<>();
        int leftLines = 0;
        int rightLines = 0;
        for (String item : leftItems) {
            List<String> lines = wrap(item, REGULAR, BODY_SIZE, textWidth, 3);
            left.add(lines);
            leftLines += lines.size();
        }
        for (String item : rightItems) {
            List<String> lines = wrap(item, REGULAR, BODY_SIZE, textWidth, 3);
            right.add(lines);
            rightLines += lines.size();
        }
        float height = Math.max(leftLines, rightLines) * LINE + 30;
        ensure(height);
        drawList(MARGIN, boxWidth, height, leftTitle, left);
        drawList(MARGIN + boxWidth + gutter, boxWidth, height, rightTitle, right);
        y -= height;
    }

    private void drawList(float x, float width, float height, String title, List<List<String>> items) throws IOException {
        strokeRect(x, y - height, width, height, GRID, 0.6f);
        text(BOLD, 8.6f, BRAND, x + 9, y - 13, upper(title));
        float lineY = y - 27;
        for (List<String> lines : items) {
            text(BOLD, BODY_SIZE, BRAND, x + 11, lineY, "•");
            for (String line : lines) {
                text(REGULAR, BODY_SIZE, TEXT, x + 21, lineY, line);
                lineY -= LINE;
            }
        }
    }

    /** Numbered green-circle bullet list inside a bordered box. */
    void numberedList(List<String> items) throws IOException {
        float textWidth = CONTENT_WIDTH - 36;
        for (int i = 0; i < items.size(); i++) {
            List<String> lines = wrap(items.get(i), REGULAR, BODY_SIZE, textWidth, 4);
            float height = lines.size() * LINE + 7;
            ensure(height);
            circle(MARGIN + 14, y - 9, 6f, BRAND);
            centered(BOLD, 7.5f, Color.WHITE, MARGIN + 14, y - 11.6f, Integer.toString(i + 1));
            for (int l = 0; l < lines.size(); l++) {
                text(REGULAR, BODY_SIZE, TEXT, MARGIN + 28, y - 12 - l * LINE, lines.get(l));
            }
            y -= height;
        }
    }

    // ---------------------------------------------------------------- test questionnaire blocks

    private static final float QUESTION_SIZE = 10f;
    private static final float QUESTION_LINE = 13.5f;
    private static final float QUESTION_INDENT = 26f;

    /** A light green box with a bold heading over wrapped text, e.g. the general directions. */
    void directions(String heading, String value) throws IOException {
        float textWidth = CONTENT_WIDTH - 20;
        List<String> lines = wrap(value, REGULAR, 9.2f, textWidth, 12);
        float height = 24 + lines.size() * 12f;
        ensure(height);
        fillRect(MARGIN, y - height, CONTENT_WIDTH, height, BRAND_SOFT);
        strokeRect(MARGIN, y - height, CONTENT_WIDTH, height, BRAND_LINE, 0.6f);
        text(BOLD, 8.8f, BRAND, MARGIN + 10, y - 13, upper(heading));
        for (int i = 0; i < lines.size(); i++) {
            text(REGULAR, 9.2f, TEXT, MARGIN + 10, y - 27 - i * 12f, lines.get(i));
        }
        y -= height;
    }

    /** Wrapped text across as many lines as it needs, e.g. a part's directions. */
    void paragraph(String value, PDFont font, float size, Color color) throws IOException {
        float line = size * 1.35f;
        for (String part : wrap(value, font, size, CONTENT_WIDTH, 40)) {
            ensure(line);
            y -= line;
            text(font, size, color, MARGIN, y + 3, part);
        }
    }

    /**
     * One numbered test question with its lettered choices, kept on one page. Short choices
     * share a row (all on one line, or a 2 x 2 grid); long ones take a line each.
     */
    void question(String number, String value, String note, List<String> choices) throws IOException {
        float textWidth = CONTENT_WIDTH - QUESTION_INDENT;
        List<String> lines = wrap(value, REGULAR, QUESTION_SIZE, textWidth, 30);
        List<String> noteLines = note == null || note.isBlank()
                ? List.of() : wrap(note, ITALIC, 8.6f, textWidth, 4);
        int perRow = choicesPerRow(choices, textWidth);
        List<List<String>> choiceLines = new ArrayList<>();
        int choiceRows = 0;
        for (int i = 0; i < choices.size(); i++) {
            List<String> wrapped = perRow == 1
                    ? wrap(choices.get(i), REGULAR, QUESTION_SIZE, textWidth - 14, 6)
                    : List.of(choices.get(i));
            choiceLines.add(wrapped);
            if (perRow == 1) {
                choiceRows += wrapped.size();
            } else if (i % perRow == 0) {
                choiceRows++;
            }
        }
        float height = lines.size() * QUESTION_LINE + noteLines.size() * 11f
                + choiceRows * QUESTION_LINE + (choices.isEmpty() ? 6 : 9);
        ensure(height);
        float lineY = y - QUESTION_LINE + 3;
        rightText(BOLD, QUESTION_SIZE, TEXT, MARGIN + QUESTION_INDENT - 7, lineY, number);
        for (String line : lines) {
            text(REGULAR, QUESTION_SIZE, TEXT, MARGIN + QUESTION_INDENT, lineY, line);
            lineY -= QUESTION_LINE;
        }
        for (String line : noteLines) {
            text(ITALIC, 8.6f, MUTED, MARGIN + QUESTION_INDENT, lineY + 2, line);
            lineY -= 11f;
        }
        float columnWidth = textWidth / Math.max(perRow, 1);
        for (int i = 0; i < choiceLines.size(); i++) {
            List<String> wrapped = choiceLines.get(i);
            float x = MARGIN + QUESTION_INDENT + 8 + (perRow == 1 ? 0 : (i % perRow) * columnWidth);
            for (int l = 0; l < wrapped.size(); l++) {
                text(REGULAR, QUESTION_SIZE, TEXT, x + (l == 0 ? 0 : 14), lineY, wrapped.get(l));
                if (perRow == 1) {
                    lineY -= QUESTION_LINE;
                }
            }
            if (perRow > 1 && (i % perRow == perRow - 1 || i == choiceLines.size() - 1)) {
                lineY -= QUESTION_LINE;
            }
        }
        y -= height;
    }

    /** 4 (or all, if fewer) on one row when every choice fits a quarter; 2 when they fit a half. */
    private static int choicesPerRow(List<String> choices, float textWidth) {
        if (choices.isEmpty()) {
            return 1;
        }
        float widest = 0;
        for (String choice : choices) {
            widest = Math.max(widest, width(REGULAR, QUESTION_SIZE, choice));
        }
        if (choices.size() <= 4 && widest <= textWidth / 4 - 10) {
            return choices.size();
        }
        if (widest <= textWidth / 2 - 10) {
            return 2;
        }
        return 1;
    }

    /** Printed name over a signature line, e.g. the teacher who owns the report. */
    void signature(String name, String role) throws IOException {
        ensure(64);
        y -= 40;
        float lineX = MARGIN + 30;
        float lineWidth = 180f;
        hline(y, lineX, lineX + lineWidth, MUTED, 0.6f);
        centered(BOLD, 9f, TEXT, lineX + lineWidth / 2, y - 12, name == null || name.isBlank() ? " " : name);
        centered(REGULAR, 8f, MUTED, lineX + lineWidth / 2, y - 23, role);
        y -= 26;
    }

    // ---------------------------------------------------------------- the Marka logo

    /**
     * The Marka mark ({@link MarkaMark}) on its light green rounded tile, placed as in
     * web-dashboard/public/favicon.svg. Drawn as vectors so it stays sharp in print; the tile's
     * 64x64 units are flipped to PDF's upward y axis.
     */
    private void drawLogo(float x, float y0, float size) throws IOException {
        float s = size / 64f;
        roundedRect(x, y0, size, size, 14 * s, MarkaMark.TILE);
        content.setNonStrokingColor(MarkaMark.GREEN);
        float[] point = new float[6];
        PathIterator path = MarkaMark.onTile().getPathIterator(new AffineTransform(s, 0, 0, -s, x, y0 + size));
        for (; !path.isDone(); path.next()) {
            switch (path.currentSegment(point)) {
                case PathIterator.SEG_MOVETO -> content.moveTo(point[0], point[1]);
                case PathIterator.SEG_LINETO -> content.lineTo(point[0], point[1]);
                case PathIterator.SEG_CUBICTO ->
                        content.curveTo(point[0], point[1], point[2], point[3], point[4], point[5]);
                case PathIterator.SEG_CLOSE -> content.closePath();
                default -> throw new IllegalStateException("The Marka mark has only lines and cubic curves.");
            }
        }
        content.fill();
        content.setNonStrokingColor(Color.BLACK);
    }

    // ---------------------------------------------------------------- primitives

    private void text(PDFont font, float size, Color color, float x, float baseline, String value) throws IOException {
        content.setNonStrokingColor(color);
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, baseline);
        content.showText(safe(font, value));
        content.endText();
    }

    private void centered(PDFont font, float size, Color color, float center, float baseline, String value) throws IOException {
        text(font, size, color, center - width(font, size, value) / 2, baseline, value);
    }

    private void rightText(PDFont font, float size, Color color, float right, float baseline, String value) throws IOException {
        text(font, size, color, right - width(font, size, value), baseline, value);
    }

    private void aligned(PDFont font, float size, Color color, float x, float columnWidth, float baseline,
                         String value, Align align) throws IOException {
        switch (align) {
            case CENTER -> centered(font, size, color, x + columnWidth / 2, baseline, value);
            case RIGHT -> rightText(font, size, color, x + columnWidth - 5, baseline, value);
            default -> text(font, size, color, x + 5, baseline, value);
        }
    }

    private void fillRect(float x, float yBottom, float w, float h, Color color) throws IOException {
        content.setNonStrokingColor(color);
        content.addRect(x, yBottom, w, h);
        content.fill();
    }

    private void strokeRect(float x, float yBottom, float w, float h, Color color, float lineWidth) throws IOException {
        content.setStrokingColor(color);
        content.setLineWidth(lineWidth);
        content.addRect(x, yBottom, w, h);
        content.stroke();
    }

    private void hline(float atY, float x1, float x2, Color color, float lineWidth) throws IOException {
        content.setStrokingColor(color);
        content.setLineWidth(lineWidth);
        content.moveTo(x1, atY);
        content.lineTo(x2, atY);
        content.stroke();
    }

    private void vline(float atX, float y1, float y2, Color color, float lineWidth) throws IOException {
        content.setStrokingColor(color);
        content.setLineWidth(lineWidth);
        content.moveTo(atX, y1);
        content.lineTo(atX, y2);
        content.stroke();
    }

    private void circle(float cx, float cy, float r, Color color) throws IOException {
        float k = r * 0.552284749831f;
        content.setNonStrokingColor(color);
        content.moveTo(cx + r, cy);
        content.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        content.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        content.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        content.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        content.fill();
    }

    private void roundedRect(float x, float yBottom, float w, float h, float r, Color color) throws IOException {
        float k = r * 0.552284749831f;
        content.setNonStrokingColor(color);
        content.moveTo(x + r, yBottom);
        content.lineTo(x + w - r, yBottom);
        content.curveTo(x + w - r + k, yBottom, x + w, yBottom + r - k, x + w, yBottom + r);
        content.lineTo(x + w, yBottom + h - r);
        content.curveTo(x + w, yBottom + h - r + k, x + w - r + k, yBottom + h, x + w - r, yBottom + h);
        content.lineTo(x + r, yBottom + h);
        content.curveTo(x + r - k, yBottom + h, x, yBottom + h - r + k, x, yBottom + h - r);
        content.lineTo(x, yBottom + r);
        content.curveTo(x, yBottom + r - k, x + r - k, yBottom, x + r, yBottom);
        content.fill();
    }

    // ---------------------------------------------------------------- text helpers

    static float width(PDFont font, float size, String value) {
        try {
            return font.getStringWidth(safe(font, value)) / 1000f * size;
        } catch (IOException e) {
            return 0;
        }
    }

    /** Word-wraps to at most maxLines; only the last line is shortened with "..." if the
     *  text still does not fit, and a single over-long word is shortened rather than overflowing. */
    static List<String> wrap(String value, PDFont font, float size, float maxWidth, int maxLines) {
        String text = value == null ? "" : value.trim();
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        int index = 0;
        while (index < words.length && lines.size() < maxLines) {
            String candidate = current.isEmpty() ? words[index] : current + " " + words[index];
            if (current.isEmpty() || width(font, size, candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                index++;
            } else {
                lines.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(current.toString());
        }
        if (index < words.length && !lines.isEmpty()) {
            String last = lines.remove(lines.size() - 1);
            lines.add(last + " " + String.join(" ", List.of(words).subList(index, words.length)));
        }
        lines.replaceAll(line -> fit(line, font, size, maxWidth));
        return lines;
    }

    static String fit(String value, PDFont font, float size, float maxWidth) {
        if (width(font, size, value) <= maxWidth) {
            return value;
        }
        String cut = value;
        while (!cut.isEmpty() && width(font, size, cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    /** The standard PDF fonts only cover Latin-1 (WinAnsi); anything else becomes "?"
     *  instead of failing the whole export. */
    private static String safe(PDFont font, String value) {
        if (value == null) {
            return "";
        }
        try {
            font.encode(value);
            return value;
        } catch (IllegalArgumentException | IOException e) {
            StringBuilder cleaned = new StringBuilder();
            for (char ch : value.toCharArray()) {
                try {
                    font.encode(String.valueOf(ch));
                    cleaned.append(ch);
                } catch (IllegalArgumentException | IOException ignored) {
                    cleaned.append('?');
                }
            }
            return cleaned.toString();
        }
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(java.util.Locale.ROOT);
    }
}
