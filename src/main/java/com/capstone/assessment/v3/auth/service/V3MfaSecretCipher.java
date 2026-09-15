package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3MfaProperties;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Profile("v3")
@Service
public class V3MfaSecretCipher {

    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] ASSOCIATED_DATA = "SMART-V3-MFA".getBytes(StandardCharsets.UTF_8);

    private final V3MfaProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public V3MfaSecretCipher(V3MfaProperties properties) {
        this(properties, new SecureRandom());
    }

    V3MfaSecretCipher(V3MfaProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public boolean isConfigured() {
        try {
            return decodeKey().length == KEY_BYTES;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(decodeKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(ASSOCIATED_DATA);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce)
                    .put(encrypted)
                    .array();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw configurationError();
        }
    }

    public String decrypt(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length <= NONCE_BYTES) {
            throw configurationError();
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decodeKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(ASSOCIATED_DATA);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw configurationError();
        }
    }

    private byte[] decodeKey() {
        String configuredKey = properties.getEncryptionKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            throw configurationError();
        }
        byte[] decoded = Base64.getDecoder().decode(configuredKey.trim());
        if (decoded.length != KEY_BYTES) {
            throw configurationError();
        }
        return decoded;
    }

    private V3AuthException configurationError() {
        return new V3AuthException(
                "MFA_CONFIGURATION_ERROR",
                "Authenticator security is temporarily unavailable.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
