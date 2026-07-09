package com.capstone.assessment.sync.dto;

import java.util.List;

public record UploadSyncRequest(
        Long teacherId,
        Long testId,
        List<TestResultUploadDto> testResults,
        List<ItemResponseUploadDto> itemResponses
) {
}
