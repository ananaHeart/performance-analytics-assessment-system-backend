package com.capstone.assessment.v3.auth.model;

public record V3VerifiedMfaLogin(
        long userId,
        long factorId,
        String challengeUuid,
        String verificationMethod
) {
}
