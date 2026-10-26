package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The request's own checks run before the service, so "sms" must pass them too. */
class V3TeacherRegistrationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emailAndSmsArePassedOnToTheServiceAnythingElseIsNot() {
        assertTrue(invalidFields(request("email")).isEmpty());
        assertTrue(invalidFields(request("SMS")).isEmpty(), "SMS is decided by the service, not blocked here");
        assertEquals(Set.of("verificationMethod"), invalidFields(request("fax")));
    }

    @Test
    void reg4TeachersMustGiveAFourDigitPostalCode() {
        assertEquals(Set.of("address.postalCode"), invalidFields(withPostalCode("")));
        assertEquals(Set.of("address.postalCode"), invalidFields(withPostalCode("95A3")));
        assertTrue(invalidFields(withPostalCode("9503")).isEmpty());
    }

    private static V3TeacherRegistrationRequest withPostalCode(String postalCode) {
        V3TeacherRegistrationRequest base = request("email");
        var a = base.address();
        return new V3TeacherRegistrationRequest(base.schoolCode(), base.verificationMethod(), base.firstName(),
                base.middleName(), base.lastName(), base.suffixId(), base.birthDate(), base.teachingStartMonth(),
                base.teachingStartYear(), base.email(), base.contactNumber(), base.password(), base.genderId(),
                base.majorId(), base.educationalAttainmentId(),
                new V3TeacherRegistrationRequest.Address(a.countryCode(), a.regionCode(), a.regionName(),
                        a.provinceCode(), a.provinceName(), a.cityMunicipalityCode(), a.cityMunicipalityName(),
                        a.barangayCode(), a.barangayName(), a.addressLine(), postalCode));
    }

    private Set<String> invalidFields(V3TeacherRegistrationRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static V3TeacherRegistrationRequest request(String verificationMethod) {
        return new V3TeacherRegistrationRequest(
                "SCHOOL-001", verificationMethod, "Test", null, "Teacher", null,
                LocalDate.of(1990, 1, 1), 6, 2015, "teacher@example.com", "09123456789", "Strong@1234",
                1, 1, 1,
                new V3TeacherRegistrationRequest.Address("PH", "12", "SOCCSKSARGEN", "1280", "Sarangani",
                        "128006", "Malungon", "128006001", "Poblacion", "Unit 1, Main Street", "9503"));
    }
}
