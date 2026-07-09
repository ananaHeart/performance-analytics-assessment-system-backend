package com.capstone.assessment.assessmentsetup.dto;

import java.util.List;

public record PartSkillMappingEntryDto(
        Long mappingId,
        Long testPartId,
        Long competencyId,
        String competencyName,
        String mappingMode,
        Integer itemCount,
        Integer startItem,
        Integer endItem,
        List<Integer> itemNumbers
) {
}
