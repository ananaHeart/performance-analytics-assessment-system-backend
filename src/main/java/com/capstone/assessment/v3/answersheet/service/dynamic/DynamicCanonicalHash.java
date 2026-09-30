package com.capstone.assessment.v3.answersheet.service.dynamic;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Canonical JSON hashing and UUIDv5 helpers used by the dynamic answer-sheet packer.
 *
 * <p>The reference generator (generate_dynamic_answer_sheet.py) hashes a page's or
 * region's own geometry dict via
 * {@code json.dumps(value, ensure_ascii=True, separators=(",", ":"), sort_keys=True)}
 * then SHA-256. Mobile ({@code DynamicOmrDetector.kt}) never recomputes that hash from
 * raw geometry - it only checks that {@code page.geometryHash} (hex) and the QR
 * payload's {@code gh} field (its base64url encoding) agree with each other. So this
 * class does not need to be byte-identical to the Python implementation; it only needs
 * to be internally deterministic between {@link #sha256Hex} and {@link #sha256Base64Url}
 * for the same input, which it is by construction (both hash the same canonical bytes).
 */
public final class DynamicCanonicalHash {

    private DynamicCanonicalHash() {
    }

    /** Serializes a Map/List/String/Number/Boolean/null tree with sorted keys and no whitespace. */
    public static String canonicalJson(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out);
        return out.toString();
    }

    public static String sha256Hex(Object value) {
        byte[] digest = sha256(canonicalJson(value).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    public static String sha256Base64Url(Object value) {
        byte[] digest = sha256(canonicalJson(value).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    public static String sha256HexOfText(String text) {
        return HexFormat.of().formatHex(sha256(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** Base64url (no padding) encoding of the 16 raw bytes of a canonical UUID string. */
    public static String uuidToBase64Url(String uuid) {
        UUID parsed = UUID.fromString(uuid);
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(parsed.getMostSignificantBits());
        buffer.putLong(parsed.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    /**
     * RFC 4122 version-5 (SHA-1 namespace) UUID, matching Python's {@code uuid.uuid5}.
     * Used so region/page identifiers are deterministic from the answer-sheet UUID
     * instead of being re-randomized on every regeneration.
     */
    public static UUID uuid5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            ByteArrayOutputStream namespaceBytes = new ByteArrayOutputStream(16);
            ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer.putLong(namespace.getMostSignificantBits());
            buffer.putLong(namespace.getLeastSignificantBits());
            namespaceBytes.writeBytes(buffer.array());
            sha1.update(namespaceBytes.toByteArray());
            byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));

            hash[6] &= 0x0f;
            hash[6] |= 0x50; // version 5
            hash[8] &= 0x3f;
            hash[8] |= (byte) 0x80; // RFC 4122 variant

            ByteBuffer result = ByteBuffer.wrap(hash, 0, 16);
            return new UUID(result.getLong(), result.getLong());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b.toString());
        } else if (value instanceof Integer || value instanceof Long) {
            out.append(value.toString());
        } else if (value instanceof Double || value instanceof Float) {
            out.append(formatNumber(((Number) value).doubleValue()));
        } else if (value instanceof Map<?, ?> map) {
            writeObject((Map<String, Object>) map, out);
        } else if (value instanceof List<?> list) {
            writeArray(list, out);
        } else {
            throw new IllegalArgumentException("Unsupported canonical JSON value type: " + value.getClass());
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder out) {
        TreeMap<String, Object> sorted = new TreeMap<>(map);
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(entry.getKey(), out);
            out.append(':');
            writeValue(entry.getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder out) {
        out.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(item, out);
        }
        out.append(']');
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return (long) value + ".0";
        }
        String text = String.valueOf(round3(value));
        return text;
    }
}
