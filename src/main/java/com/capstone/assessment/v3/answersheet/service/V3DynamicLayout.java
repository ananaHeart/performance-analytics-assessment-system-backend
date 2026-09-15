package com.capstone.assessment.v3.answersheet.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Immutable A4 portrait MC geometry. Capacity is derived, never achieved by shrinking marks. */
public final class V3DynamicLayout {
    public static final String CODE = "OMR-A4-DYNAMIC-CTX-V3";
    public static final String VERSION = "3";
    public static final String SCANNER_VERSION = "3.0.0";
    public static final int MANIFEST_VERSION = 2;
    public static final int MAX_PAGES = 12;
    public static final double WIDTH = 595.276, HEIGHT = 841.890;
    public static final double BODY_TOP = HEIGHT - 238, BODY_BOTTOM = 80;
    public static final double ROW_PITCH = 34, RADIUS = 6.4;
    public static final int PAGE_CAPACITY = (int)Math.floor((BODY_TOP - BODY_BOTTOM - 2 * RADIUS) / ROW_PITCH) + 1;
    public static final int MAX_QUESTIONS = PAGE_CAPACITY * MAX_PAGES;
    private V3DynamicLayout() { }

    public static int pages(int questions) {
        if (questions < 5 || questions > MAX_QUESTIONS)
            throw new IllegalArgumentException("Dynamic A4 supports 5.." + MAX_QUESTIONS + " questions.");
        return (questions + PAGE_CAPACITY - 1) / PAGE_CAPACITY;
    }
    public static int pageQuestions(int questions, int page) {
        int pages = pages(questions);
        if (page < 1 || page > pages) throw new IllegalArgumentException("Invalid page number.");
        return Math.min(PAGE_CAPACITY, questions - (page - 1) * PAGE_CAPACITY);
    }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String compactUuid(String value) {
        UUID uuid = UUID.fromString(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array());
    }
    public static String qr(String sheet, String page, String assignment, int number, int count, String geometryHash) {
        if (number < 1 || number > count || count > MAX_PAGES) throw new IllegalArgumentException("Invalid QR page count.");
        String digest = Base64.getUrlEncoder().withoutPadding().encodeToString(HexFormat.of().parseHex(geometryHash));
        String json = "{\"v\":3,\"as\":\"%s\",\"pg\":\"%s\",\"ta\":\"%s\",\"pn\":%d,\"pc\":%d,\"tc\":\"%s\",\"tv\":\"%s\",\"gh\":\"%s\"}"
                .formatted(compactUuid(sheet), compactUuid(page), compactUuid(assignment), number, count, CODE, VERSION, digest);
        if (json.getBytes(StandardCharsets.UTF_8).length > 256) throw new IllegalArgumentException("QR exceeds 256 bytes.");
        return json;
    }
}
