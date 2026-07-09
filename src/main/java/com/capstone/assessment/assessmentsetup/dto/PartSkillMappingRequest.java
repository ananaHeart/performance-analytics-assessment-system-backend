package com.capstone.assessment.assessmentsetup.dto;

import java.util.List;

public record PartSkillMappingRequest(
        Long testPartId,
        List<PartSkillMappingEntryRequest> mappings
) {
}
