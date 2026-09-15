package com.capstone.assessment.v3.auth.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3TotpServiceTest {

    private static final String RFC_SHA1_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final V3TotpService service = new V3TotpService(new SecureRandom());

    @Test
    void generatesRfc6238Sha1Code() {
        assertEquals("94287082", service.generateCode(RFC_SHA1_SECRET, 1L, "SHA1", 8));
    }

    @Test
    void acceptsOnlyMatchingCodeInsideConfiguredWindow() {
        Instant now = Instant.ofEpochSecond(59L);

        assertTrue(service.findMatchingCounter(
                RFC_SHA1_SECRET, "94287082", now, "SHA1", 8, 30, 0
        ).isPresent());
        assertFalse(service.findMatchingCounter(
                RFC_SHA1_SECRET, "94287081", now, "SHA1", 8, 30, 0
        ).isPresent());
        assertFalse(service.findMatchingCounter(
                RFC_SHA1_SECRET, "not-a-code", now, "SHA1", 8, 30, 1
        ).isPresent());
    }
}
