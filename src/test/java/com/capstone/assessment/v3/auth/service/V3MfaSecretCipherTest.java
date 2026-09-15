package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3MfaProperties;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3MfaSecretCipherTest {

    @Test
    void encryptsAndDecryptsTotpSecretWithConfiguredKey() {
        V3MfaProperties properties = configuredProperties();
        V3MfaSecretCipher cipher = new V3MfaSecretCipher(properties, new SecureRandom());

        byte[] encrypted = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertTrue(cipher.isConfigured());
        assertFalse(new String(encrypted, StandardCharsets.UTF_8).contains("JBSWY3DPEHPK3PXP"));
        assertEquals("JBSWY3DPEHPK3PXP", cipher.decrypt(encrypted));
    }

    @Test
    void rejectsMissingEncryptionKey() {
        V3MfaSecretCipher cipher = new V3MfaSecretCipher(new V3MfaProperties(), new SecureRandom());

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> cipher.encrypt("JBSWY3DPEHPK3PXP")
        );

        assertFalse(cipher.isConfigured());
        assertEquals("MFA_CONFIGURATION_ERROR", exception.getCode());
    }

    private V3MfaProperties configuredProperties() {
        V3MfaProperties properties = new V3MfaProperties();
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(key));
        return properties;
    }
}
