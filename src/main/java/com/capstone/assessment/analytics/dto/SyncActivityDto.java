package com.capstone.assessment.analytics.dto;

import java.time.LocalDateTime;

public record SyncActivityDto(
        Long testId,
        String testName,
        LocalDateTime syncTimestamp,
        String syncStatus
) {
}
