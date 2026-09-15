package com.capstone.assessment.v3.system.dto;

import java.util.List;

public record V3DatabaseReadinessResponse(
        String profile,
        String databaseName,
        boolean ready,
        int tableCount,
        int foreignKeyCount,
        int checkConstraintCount,
        int uniqueConstraintCount,
        int activeQuestionTypeCount,
        int activeOmrTemplateCount,
        int activePerformanceRuleSetCount,
        List<V3DatabaseCheckResponse> checks
) {
}
