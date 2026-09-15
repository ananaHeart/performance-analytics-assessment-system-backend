package com.capstone.assessment.v3.answersheet.dto;

import java.util.List;
import java.util.Map;

public record V3AnswerSheetEligibilityResponse(
        long testAssignmentId,
        String assignmentUuid,
        String paperSizeCode,
        int totalQuestions,
        Map<String, Integer> questionTypeCounts,
        boolean eligible,
        String templateCode,
        String templateVersion,
        List<Blocker> blockers
) {
    public record Blocker(String code, String message) {
    }
}
