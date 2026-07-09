package com.capstone.assessment.assessmentsetup.controller;

import com.capstone.assessment.assessmentsetup.dto.AssessmentDetailDto;
import com.capstone.assessment.assessmentsetup.dto.AssessmentDto;
import com.capstone.assessment.assessmentsetup.dto.CompetencyOptionDto;
import com.capstone.assessment.assessmentsetup.dto.CreateAssessmentRequest;
import com.capstone.assessment.assessmentsetup.dto.CreateTestPartRequest;
import com.capstone.assessment.assessmentsetup.service.AssessmentSetupService;
import com.capstone.assessment.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assessments")
public class AssessmentSetupController {

    private final AssessmentSetupService assessmentSetupService;

    public AssessmentSetupController(AssessmentSetupService assessmentSetupService) {
        this.assessmentSetupService = assessmentSetupService;
    }

    // Returns all assessments created under classes assigned to a teacher.
    @GetMapping("/teacher/{teacherId}")
    public ApiResponse<List<AssessmentDto>> getAssessmentsByTeacher(@PathVariable Long teacherId) {
        List<AssessmentDto> response = assessmentSetupService.getAssessmentsByTeacher(teacherId);
        return ApiResponse.success("Assessments retrieved successfully.", response);
    }

    // Returns one assessment with its competency-linked test parts and answer keys.
    @GetMapping("/{testId}")
    public ApiResponse<AssessmentDetailDto> getAssessmentDetail(@PathVariable Long testId) {
        AssessmentDetailDto response = assessmentSetupService.getAssessmentDetail(testId);
        return ApiResponse.success("Assessment details retrieved successfully.", response);
    }

    // Creates a new assessment header that stores metadata only, not actual test questions.
    @PostMapping
    public ApiResponse<Long> createAssessment(@RequestBody CreateAssessmentRequest request) {
        Long response = assessmentSetupService.createAssessment(request);
        return ApiResponse.success("Assessment created successfully.", response);
    }

    // Creates a competency-linked test part with answer key information for an assessment.
    @PostMapping("/{testId}/parts")
    public ApiResponse<Long> createTestPart(
            @PathVariable Long testId,
            @RequestBody CreateTestPartRequest request
    ) {
        Long response = assessmentSetupService.createTestPart(testId, request);
        return ApiResponse.success("Test part created successfully.", response);
    }

    // Returns competency options for a class when building assessment parts.
    @GetMapping("/classes/{classId}/competencies")
    public ApiResponse<List<CompetencyOptionDto>> getCompetenciesForClass(@PathVariable Long classId) {
        List<CompetencyOptionDto> response = assessmentSetupService.getCompetenciesForClass(classId);
        return ApiResponse.success("Competency options retrieved successfully.", response);
    }
}
