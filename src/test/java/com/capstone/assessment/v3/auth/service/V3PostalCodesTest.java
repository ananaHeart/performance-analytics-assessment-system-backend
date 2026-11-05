package com.capstone.assessment.v3.auth.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3PostalCodesTest {

    @Test
    void r2_3AZipCodeMustBelongToTheSelectedCity() {
        // City of Marikina (PSGC 137402000) and Quezon City (137404000).
        assertEquals(Optional.empty(), V3PostalCodes.problem("1800", "137402000", "City of Marikina"));
        String wrongCity = V3PostalCodes.problem("1101", "137402000", "City of Marikina").orElseThrow();
        assertTrue(wrongCity.startsWith("ZIP code 1101 belongs to Quezon City, not City of Marikina."), wrongCity);
        assertTrue(wrongCity.contains("1800"), wrongCity);

        // Malungon, Sarangani (PSGC 128007000).
        assertEquals(Optional.empty(), V3PostalCodes.problem("9503", "128007000", "Malungon"));
        // A ZIP shared by two cities fits either.
        assertEquals(Optional.empty(), V3PostalCodes.problem("1412", "137503000", "City of Navotas"));
    }

    @Test
    void r2_3AnUnknownZipIsRefusedButAMissingCityOnlyNeedsARealZip() {
        assertEquals(Optional.of("ZIP code 0999 is not a Philippine ZIP code."),
                V3PostalCodes.problem("0999", "137402000", "City of Marikina"));
        assertEquals(Optional.of("Postal code must be 4 digits."), V3PostalCodes.problem("95A3", null, null));
        assertEquals(Optional.empty(), V3PostalCodes.problem("1101", null, "Quezon City"));
    }
}
