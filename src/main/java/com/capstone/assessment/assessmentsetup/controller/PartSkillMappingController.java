package com.capstone.assessment.assessmentsetup.controller;

import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingEntryDto;
import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingPreviewResponse;
import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingRequest;
import com.capstone.assessment.assessmentsetup.service.PartSkillMappingService;
import com.capstone.assessment.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/part-skill-mappings")
public class PartSkillMappingController {

    private final PartSkillMappingService partSkillMappingService;

    public PartSkillMappingController(PartSkillMappingService partSkillMappingService) {
        this.partSkillMappingService = partSkillMappingService;
    }

    @PostMapping("/preview")
    public ApiResponse<PartSkillMappingPreviewResponse> preview(@RequestBody PartSkillMappingRequest request) {
        PartSkillMappingPreviewResponse response = partSkillMappingService.preview(request);
        return ApiResponse.success("Part skill mapping preview generated successfully.", response);
    }

    @PostMapping("/save")
    public ApiResponse<PartSkillMappingPreviewResponse> save(@RequestBody PartSkillMappingRequest request) {
        PartSkillMappingPreviewResponse response = partSkillMappingService.save(request);
        return ApiResponse.success("Part skill mapping saved successfully.", response);
    }

    @GetMapping("/test-parts/{testPartId}")
    public ApiResponse<List<PartSkillMappingEntryDto>> getByTestPart(@PathVariable Long testPartId) {
        List<PartSkillMappingEntryDto> response = partSkillMappingService.getByTestPart(testPartId);
        return ApiResponse.success("Part skill mappings retrieved successfully.", response);
    }
}
