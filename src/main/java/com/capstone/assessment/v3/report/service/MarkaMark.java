package com.capstone.assessment.v3.report.service;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Marka mark, a green check mark in an open rounded square, ported from the approved
 * master web-dashboard/docs/branding/marka-mark.svg (revision r1, SHA-256
 * b3750deed7c13ec1abd8a8fddaf085dcc78caf4a81d7fbbe1d0e2b4d3bfafbff). The path data is copied
 * as-is; the PDF header ({@link ReportPdfDocument}) and the Excel header
 * ({@link ReportExcelSheet#logoPng()}) both draw it from here, so the two cannot drift apart.
 */
final class MarkaMark {

    static final Color GREEN = new Color(0x00, 0xB3, 0x16);
    /** The light green rounded tile behind the mark, as in web-dashboard/public/favicon.svg. */
    static final Color TILE = new Color(230, 248, 232);

    // The master's two filled paths (frame, check) in its 64x64 viewBox, y pointing down.
    private static final String FRAME = "M37.31 4 L15.35 4 C9.08 4 4 9.08 4 15.35 L4 48.65 C4 54.92 9.08 60 15.35 60 "
            + "L48.65 60 C54.92 60 60 54.92 60 48.65 L60 30.68 C60 29.2 58.8 28.01 57.33 28.01 "
            + "C55.85 28.01 54.66 29.2 54.66 30.68 L54.66 48.65 C54.66 51.97 51.97 54.66 48.65 54.66 "
            + "L15.35 54.66 C12.03 54.66 9.34 51.97 9.34 48.65 L9.34 15.35 C9.34 12.03 12.03 9.34 15.35 9.34 "
            + "L37.31 9.34 C38.78 9.34 39.98 8.14 39.98 6.67 C39.98 5.2 38.78 4 37.31 4 Z";
    private static final String CHECK = "M31.63 34.71 C37.47 26.91 43.35 19.15 50.09 12.13 "
            + "C52.06 10.07 54.74 8.18 57.57 9.87 C59.3 10.9 58.01 12.64 57.2 13.78 "
            + "C50.77 22.83 44.79 32.41 39.25 42.01 C37.49 45.05 36.37 47.03 32.46 47.26 "
            + "C29.87 47.42 27.85 46.13 26.37 44.02 C23.73 40.25 20.83 36.39 17.68 33.03 "
            + "C15.98 31.21 15.03 28.87 17.34 26.98 C20.73 24.22 24.33 25.62 26.79 28.75 "
            + "C28.3 30.67 30.04 32.73 31.63 34.71 Z";

    /** As on the web favicon: the master's 4..60 extent is scaled about the centre to 6..58. */
    private static final double TILE_SCALE = 52.0 / 56.0;

    private static final Pattern TOKEN = Pattern.compile("[MLCZ]|-?\\d+(?:\\.\\d+)?");

    private MarkaMark() {
    }

    /** Both paths placed on a 64x64 tile (y pointing down). Returns a new copy each call. */
    static Path2D onTile() {
        Path2D mark = new Path2D.Double();
        mark.append(parse(FRAME), false);
        mark.append(parse(CHECK), false);
        AffineTransform placement = new AffineTransform();
        placement.translate(32, 32);
        placement.scale(TILE_SCALE, TILE_SCALE);
        placement.translate(-32, -32);
        mark.transform(placement);
        return mark;
    }

    /** Reads absolute M/L/C/Z path data, the only commands the master uses. */
    static Path2D parse(String data) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(data);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        Path2D path = new Path2D.Double();
        int i = 0;
        while (i < tokens.size()) {
            String command = tokens.get(i++);
            int count = switch (command) {
                case "M", "L" -> 2;
                case "C" -> 6;
                case "Z" -> 0;
                default -> throw new IllegalArgumentException("Unsupported path command: " + command);
            };
            double[] n = new double[count];
            for (int k = 0; k < n.length; k++) {
                n[k] = Double.parseDouble(tokens.get(i++));
            }
            switch (command) {
                case "M" -> path.moveTo(n[0], n[1]);
                case "L" -> path.lineTo(n[0], n[1]);
                case "C" -> path.curveTo(n[0], n[1], n[2], n[3], n[4], n[5]);
                default -> path.closePath();
            }
        }
        return path;
    }
}
