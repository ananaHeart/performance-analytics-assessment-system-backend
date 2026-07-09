package com.capstone.assessment.gradingperiod.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.gradingperiod.dto.CreateGradingPeriodRequest;
import com.capstone.assessment.gradingperiod.dto.GradingPeriodDto;
import com.capstone.assessment.gradingperiod.service.GradingPeriodService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/grading-periods")
public class GradingPeriodController {

    private final GradingPeriodService gradingPeriodService;

    public GradingPeriodController(GradingPeriodService gradingPeriodService) {
        this.gradingPeriodService = gradingPeriodService;
    }

    @GetMapping
    public ApiResponse<List<GradingPeriodDto>> getByAcademicYear(@RequestParam Long academicYearId) {
        List<GradingPeriodDto> response = gradingPeriodService.getByAcademicYear(academicYearId);
        return ApiResponse.success("Grading periods retrieved successfully.", response);
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateGradingPeriodRequest request) {
        Long response = gradingPeriodService.create(request);
        return ApiResponse.success("Grading period created successfully.", response);
    }
}
