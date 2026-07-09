package com.capstone.assessment.assessmentsetup.dto;

import java.util.List;

public record PartSkillMappingEntryRequest(
        Long competencyId,
        String mappingMode,
        Integer itemCount,
        Integer startItem,
        Integer endItem,
        List<Integer> itemNumbers
) {
}
