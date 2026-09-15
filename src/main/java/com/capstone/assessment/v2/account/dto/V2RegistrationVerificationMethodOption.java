package com.capstone.assessment.v2.account.dto;

public record V2RegistrationVerificationMethodOption(
        String method,
        String displayName,
        boolean available,
        String unavailableReason
) {
}
