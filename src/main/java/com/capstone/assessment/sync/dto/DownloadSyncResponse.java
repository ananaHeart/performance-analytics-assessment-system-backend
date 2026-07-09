package com.capstone.assessment.sync.dto;

import java.util.List;

public record DownloadSyncResponse(
        List<ClassSyncDto> classes,
        List<StudentSyncDto> students,
        List<TestSyncDto> tests,
        List<TestPartSyncDto> testParts,
        List<CompetencySyncDto> competencies,
        List<TestResultSyncDto> testResults,
        List<ItemResponseSyncDto> itemResponses
) {
}
