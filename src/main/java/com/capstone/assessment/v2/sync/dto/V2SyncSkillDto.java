package com.capstone.assessment.v2.sync.dto;

public record V2SyncSkillDto(
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
