package com.capstone.assessment.competency.dto;

import java.util.List;

public record CompetencyTreeDto(
        Long competencyId,
        Long parentCompetencyId,
        Long gradeLevelId,
        Long subjectId,
        String competencyName,
        List<CompetencyTreeDto> branches
) {
}
