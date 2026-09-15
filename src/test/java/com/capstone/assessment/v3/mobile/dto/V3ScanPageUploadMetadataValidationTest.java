package com.capstone.assessment.v3.mobile.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3ScanPageUploadMetadataValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validMetadataPassesContractValidation() {
        assertTrue(validator.validate(validMetadata()).isEmpty());
    }

    @Test
    void blankAndMalformedStableIdentifiersAreRejected() {
        V3ScanPageUploadMetadata metadata = new V3ScanPageUploadMetadata(
                "3.1",
                "",
                "not-a-uuid",
                validUuid(3),
                validUuid(4),
                validUuid(5),
                validUuid(6),
                validUuid(7),
                0,
                0,
                0,
                "",
                "UPPERCASE",
                "short",
                null
        );

        Set<String> invalidFields = validator.validate(metadata).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "contractVersion",
                "syncUuid",
                "resultUuid",
                "classListId",
                "pageNumber",
                "captureNumber",
                "scannerVersion",
                "qrPayloadHash",
                "imageHash",
                "capturedAt"
        ), invalidFields);
    }

    private V3ScanPageUploadMetadata validMetadata() {
        return new V3ScanPageUploadMetadata(
                "3.0",
                validUuid(1),
                validUuid(2),
                validUuid(3),
                validUuid(4),
                validUuid(5),
                validUuid(6),
                validUuid(7),
                41001L,
                1,
                1,
                "3.0.0",
                "a".repeat(64),
                "b".repeat(64),
                Instant.parse("2026-08-30T03:35:00Z")
        );
    }

    private String validUuid(int suffix) {
        return "00000000-0000-4000-8000-%012d".formatted(suffix);
    }
}
