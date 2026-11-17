package com.capstone.assessment.v3.auth.controller;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.service.V3PublicRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3PostalCodeCheckControllerTest {

    private final V3PostalCodeCheckController controller = new V3PostalCodeCheckController(new V3PublicRateLimiter());

    @Test
    void r2_3TheAddressStepGetsTheSameAnswerAsRegistration() {
        // Malungon, Sarangani (PSGC 128007000): its ZIP code is 9503.
        var valid = controller.check("9503", "128007000", "Malungon", new MockHttpServletRequest()).getBody().data();
        assertEquals(true, valid.valid());
        assertNull(valid.message());
        assertEquals(List.of("9503"), valid.cityPostalCodes());

        var unknown = controller.check("3444", "128007000", "Malungon", new MockHttpServletRequest()).getBody().data();
        assertEquals(false, unknown.valid());
        assertEquals("ZIP code 3444 is not a Philippine ZIP code.", unknown.message());

        var otherCity = controller.check("1101", "128007000", "Malungon", new MockHttpServletRequest())
                .getBody().data();
        assertEquals(false, otherCity.valid());
        assertEquals("ZIP code 1101 belongs to Quezon City, not Malungon. ZIP code(s) for Malungon: 9503.",
                otherCity.message());
    }

    @Test
    void r2_3BadInputIsAFieldErrorAndTooManyChecksAreRefused() {
        V3FieldValidationException bad = assertThrows(V3FieldValidationException.class,
                () -> controller.check("95A3", " ", null, new MockHttpServletRequest()));
        assertEquals("Postal code must be 4 digits.", bad.getErrors().get("postalCode"));
        assertTrue(bad.getErrors().containsKey("cityMunicipalityCode"));

        V3PostalCodeCheckController limited = new V3PostalCodeCheckController(new V3PublicRateLimiter());
        for (int i = 0; i < 30; i++) {
            limited.check("9503", "128007000", null, new MockHttpServletRequest());
        }
        assertEquals("POSTAL_CODE_CHECK_RATE_LIMITED", assertThrows(V3AuthException.class,
                () -> limited.check("9503", "128007000", null, new MockHttpServletRequest())).getCode());
    }
}
