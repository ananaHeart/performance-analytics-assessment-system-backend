package com.capstone.assessment.v3.auth.dto;

public record V3CurrentUserResponse(
        long userId,
        String schoolId,
        String firstName,
        String middleName,
        String lastName,
        String suffix,
        String email,
        String role,
        String status,
        boolean mfaRequired,
        // SES-1: the web shows "Stay signed in?" a minute before this; 0 means no idle limit.
        long sessionIdleTimeoutSeconds
) {
}
