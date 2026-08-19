package com.capstone.assessment.v2.assessment.dto;

public record V2AssessmentSkillOption(
        Long skillId,
        Long competencyId,
        String competencyName,
        Integer rootTagId,
        String rootTagName,
        Integer termPeriodId,
        Integer gradeLevelId,
        Integer subjectId
) {
}
