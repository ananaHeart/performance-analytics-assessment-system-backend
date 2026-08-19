package com.capstone.assessment.v2.sync.dto;

import java.time.Instant;
import java.util.List;

public record V2SyncDownloadResponse(
        String contractVersion,
        Instant generatedAt,
        V2SyncUserDto user,
        List<V2SyncClassAssignmentDto> classAssignments,
        List<V2SyncClassDto> classes,
        List<V2SyncClassListDto> classLists,
        List<V2SyncStudentDto> students,
        List<V2SyncTestDto> tests,
        List<V2SyncTestPartDto> testParts,
        List<V2SyncQuestionDto> questions,
        List<V2SyncAnswerKeyDto> answerKeys,
        List<V2SyncSkillDto> skills,
        List<V2SyncQuestionMappingDto> questionMappings
) {
}
