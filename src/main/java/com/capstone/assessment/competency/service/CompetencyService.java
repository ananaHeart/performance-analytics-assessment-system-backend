package com.capstone.assessment.competency.service;

import com.capstone.assessment.competency.dto.CompetencyTreeDto;

import java.util.List;

public interface CompetencyService {

    List<CompetencyTreeDto> getCompetencyTree(Long gradeLevelId, Long subjectId);
}
