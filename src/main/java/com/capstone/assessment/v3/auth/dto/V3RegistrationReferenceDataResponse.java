package com.capstone.assessment.v3.auth.dto;

import java.util.List;

public record V3RegistrationReferenceDataResponse(
        List<ReferenceOption> genders,
        List<ReferenceOption> suffixes,
        List<ReferenceOption> majors,
        List<ReferenceOption> educationalAttainments,
        List<SchoolOption> schools,
        List<VerificationMethodOption> verificationMethods,
        VerificationPolicy verificationPolicy
) {
    public record ReferenceOption(long id, String name) {
    }

    public record SchoolOption(String schoolId, String schoolName) {
    }

    public record VerificationMethodOption(
            String method,
            String label,
            boolean available,
            String unavailableReason
    ) {
    }

    public record VerificationPolicy(
            long otpTtlSeconds,
            long resendCooldownSeconds,
            int maximumAttempts,
            long registrationRetentionDays
    ) {
    }
}
