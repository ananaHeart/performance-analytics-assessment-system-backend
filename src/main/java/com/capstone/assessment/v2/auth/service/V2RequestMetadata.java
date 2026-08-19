package com.capstone.assessment.v2.auth.service;

public record V2RequestMetadata(
        String ipAddress,
        String userAgent,
        String deviceIdentifier
) {
}
