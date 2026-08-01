package com.capstone.assessment.analytics.dto;

import java.time.Instant;

public record SyncActivityDto(
        Long testId,
        String testName,
        Instant syncTimestamp,
        String syncStatus
) {
}
