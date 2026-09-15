package com.capstone.assessment.v2.analytics.dto;

import java.time.Instant;

public record V2SyncActivityResponse(
        long syncId,
        String syncUuid,
        long teacherUserId,
        String teacherName,
        Long testId,
        String testName,
        Long classId,
        String gradeLevelName,
        String sectionName,
        String subjectName,
        String syncStatus,
        int successfulResults,
        int failedResults,
        int skippedResults,
        Instant startedAt,
        Instant completedAt,
        Instant lastSyncedAt
) {
}
