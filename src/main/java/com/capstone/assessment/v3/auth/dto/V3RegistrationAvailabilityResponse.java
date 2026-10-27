package com.capstone.assessment.v3.auth.dto;

/** REG-7: format and "already used" for each field that was asked; null when not asked. */
public record V3RegistrationAvailabilityResponse(Field email, Field contactNumber) {
    public record Field(boolean valid, boolean available) {
    }
}
