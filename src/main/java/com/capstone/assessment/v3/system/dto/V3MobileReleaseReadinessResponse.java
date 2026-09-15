package com.capstone.assessment.v3.system.dto;

import java.util.List;

public record V3MobileReleaseReadinessResponse(
        String contractPack,
        String mode,
        String publicBaseUrl,
        boolean backendReady,
        boolean writeApiEnabled,
        boolean databaseReady,
        long sessionTtlSeconds,
        boolean refreshSupported,
        boolean mobileWiringVerified,
        boolean physicalScannerVerified,
        boolean fullyConnected,
        List<V3MobileReleaseCheckResponse> checks
) {
}
