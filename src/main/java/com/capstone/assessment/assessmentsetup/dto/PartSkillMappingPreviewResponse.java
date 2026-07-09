package com.capstone.assessment.assessmentsetup.dto;

import java.util.List;

public record PartSkillMappingPreviewResponse(
        Long testPartId,
        Long parentCompetencyId,
        String parentCompetencyName,
        Integer numberOfItems,
        Integer mappedItemCount,
        Boolean fullCoverage,
        List<PartSkillMappingEntryDto> mappings
) {
}
