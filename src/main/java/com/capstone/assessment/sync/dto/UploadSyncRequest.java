package com.capstone.assessment.sync.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record UploadSyncRequest(
        Long teacherId,
        Long testId,
        OffsetDateTime uploadedAt,
        List<TestResultUploadDto> testResults,
        List<ItemResponseUploadDto> itemResponses
) {
}
