package com.capstone.assessment.analytics.dto;

import java.util.List;

public record LmsAffectedStudentsDto(
        Long competencyId,
        String competencyName,
        Double masteryRate,
        List<InterventionStudentDto> affectedStudents
) {
}
