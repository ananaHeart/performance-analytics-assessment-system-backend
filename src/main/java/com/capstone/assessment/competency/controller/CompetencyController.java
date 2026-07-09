package com.capstone.assessment.competency.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.competency.dto.CompetencyTreeDto;
import com.capstone.assessment.competency.service.CompetencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/competencies")
public class CompetencyController {

    private final CompetencyService competencyService;

    public CompetencyController(CompetencyService competencyService) {
        this.competencyService = competencyService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<CompetencyTreeDto>> getCompetencyTree(
            @RequestParam(required = false) Long gradeLevelId,
            @RequestParam(required = false) Long subjectId
    ) {
        List<CompetencyTreeDto> response = competencyService.getCompetencyTree(gradeLevelId, subjectId);
        return ApiResponse.success("Competency tree retrieved successfully.", response);
    }
}
