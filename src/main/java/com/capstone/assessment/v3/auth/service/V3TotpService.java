package com.capstone.assessment.v3.auth.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

@Profile("v3")
@Service
public class V3TotpService {

    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int SECRET_BYTES = 20;

    private final SecureRandom secureRandom;

    public V3TotpService() {
        this(new SecureRandom());
    }

    V3TotpService(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return encodeBase32(secret);
    }

    public OptionalLong findMatchingCounter(
            String base32Secret,
            String submittedCode,
            Instant now,
            String algorithm,
            int digits,
            int periodSeconds,
            int verificationWindow
    ) {
        if (submittedCode == null || !submittedCode.matches("\\d{" + digits + "}")) {
            return OptionalLong.empty();
        }
        long currentCounter = Math.floorDiv(now.getEpochSecond(), periodSeconds);
        for (int offset = -verificationWindow; offset <= verificationWindow; offset++) {
            long candidateCounter = currentCounter + offset;
            String expected = generateCode(base32Secret, candidateCounter, algorithm, digits);
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    submittedCode.getBytes(StandardCharsets.US_ASCII)
            )) {
                return OptionalLong.of(candidateCounter);
            }
        }
        return OptionalLong.empty();
    }

    String generateCode(String base32Secret, long counter, String algorithm, int digits) {
        try {
            String hmacAlgorithm = switch (algorithm.toUpperCase(Locale.ROOT)) {
                case "SHA1" -> "HmacSHA1";
                case "SHA256" -> "HmacSHA256";
                case "SHA512" -> "HmacSHA512";
                default -> throw new IllegalArgumentException("Unsupported TOTP algorithm.");
            };
            Mac mac = Mac.getInstance(hmacAlgorithm);
            mac.init(new SecretKeySpec(decodeBase32(base32Secret), hmacAlgorithm));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            int modulus = (int) Math.pow(10, digits);
            return String.format(Locale.ROOT, "%0" + digits + "d", binary % modulus);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP calculation is unavailable.", exception);
        }
    }

    private String encodeBase32(byte[] input) {
        StringBuilder output = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                output.append(BASE32[(buffer >> (bitsLeft - 5)) & 0x1f]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            output.append(BASE32[(buffer << (5 - bitsLeft)) & 0x1f]);
        }
        return output.toString();
    }

    private byte[] decodeBase32(String input) {
        String normalized = input.replace("=", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
        ByteBuffer output = ByteBuffer.allocate(normalized.length() * 5 / 8 + 1);
        int buffer = 0;
        int bitsLeft = 0;
        for (char character : normalized.toCharArray()) {
            int value = base32Value(character);
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.put((byte) ((buffer >> (bitsLeft - 8)) & 0xff));
                bitsLeft -= 8;
            }
        }
        byte[] result = new byte[output.position()];
        output.flip();
        output.get(result);
        return result;
    }

    private int base32Value(char value) {
        if (value >= 'A' && value <= 'Z') {
            return value - 'A';
        }
        if (value >= '2' && value <= '7') {
            return value - '2' + 26;
        }
        throw new IllegalArgumentException("Invalid Base32 secret.");
    }
}
