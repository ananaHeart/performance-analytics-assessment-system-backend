package com.capstone.assessment.v3.account.dto;

import java.time.Instant;

/**
 * SET-1, SET-3, VER-1: the signed-in user's own account. The next-change dates are null when a
 * change is allowed now.
 */
public record V3AccountResponse(
        long userId,
        String role,
        String firstName,
        String middleName,
        String lastName,
        Integer suffixId,
        String email,
        boolean emailVerified,
        String contactNumber,
        boolean contactVerified,
        Instant nextEmailChangeAt,
        Instant nextContactChangeAt,
        Instant passwordChangedAt
) {
}
