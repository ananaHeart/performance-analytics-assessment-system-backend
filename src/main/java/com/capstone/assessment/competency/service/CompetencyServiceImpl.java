package com.capstone.assessment.competency.service;

import com.capstone.assessment.competency.dto.CompetencyTreeDto;
import com.capstone.assessment.competency.repository.CompetencyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompetencyServiceImpl implements CompetencyService {

    private final CompetencyRepository competencyRepository;

    public CompetencyServiceImpl(CompetencyRepository competencyRepository) {
        this.competencyRepository = competencyRepository;
    }

    @Override
    public List<CompetencyTreeDto> getCompetencyTree(Long gradeLevelId, Long subjectId) {
        return competencyRepository.findParentCompetencies(gradeLevelId, subjectId).stream()
                .map(parent -> new CompetencyTreeDto(
                        parent.competencyId(),
                        parent.parentCompetencyId(),
                        parent.gradeLevelId(),
                        parent.subjectId(),
                        parent.competencyName(),
                        competencyRepository.findBranchesByParentId(parent.competencyId())
                ))
                .toList();
    }
}
