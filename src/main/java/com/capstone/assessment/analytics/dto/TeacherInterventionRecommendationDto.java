package com.capstone.assessment.analytics.dto;

import java.util.List;

public record TeacherInterventionRecommendationDto(
        Long competencyId,
        String competencyName,
        Double masteryRate,
        String status,
        Integer affectedLearnersCount,
        String recommendedAction,
        String targetGroup,
        String followUpActivity,
        String recommendation,
        List<InterventionStudentDto> affectedStudents
) {
}
