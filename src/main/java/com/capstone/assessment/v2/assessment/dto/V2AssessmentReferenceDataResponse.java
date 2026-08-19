package com.capstone.assessment.v2.assessment.dto;

import java.util.List;

public record V2AssessmentReferenceDataResponse(
        List<V2AssessmentAssignmentOption> classAssignments,
        List<V2AssessmentTermPeriodOption> termPeriods,
        List<V2AssessmentSkillOption> skills
) {
}
