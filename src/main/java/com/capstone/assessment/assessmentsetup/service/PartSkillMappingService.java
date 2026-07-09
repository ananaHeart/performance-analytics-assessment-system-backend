package com.capstone.assessment.assessmentsetup.service;

import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingEntryDto;
import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingPreviewResponse;
import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingRequest;

import java.util.List;

public interface PartSkillMappingService {

    PartSkillMappingPreviewResponse preview(PartSkillMappingRequest request);

    PartSkillMappingPreviewResponse save(PartSkillMappingRequest request);

    List<PartSkillMappingEntryDto> getByTestPart(Long testPartId);
}
