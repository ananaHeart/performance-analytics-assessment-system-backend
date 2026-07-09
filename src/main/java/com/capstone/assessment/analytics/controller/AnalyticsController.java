package com.capstone.assessment.analytics.controller;

import com.capstone.assessment.analytics.dto.AssessmentTrendDto;
import com.capstone.assessment.analytics.dto.InterventionStudentDto;
import com.capstone.assessment.analytics.dto.ItemAnalysisDto;
import com.capstone.assessment.analytics.dto.LmsAffectedStudentsDto;
import com.capstone.assessment.analytics.dto.LmsDto;
import com.capstone.assessment.analytics.dto.SchoolLmsDto;
import com.capstone.assessment.analytics.dto.StudentSkillMasteryDto;
import com.capstone.assessment.analytics.dto.SyncActivityDto;
import com.capstone.assessment.analytics.dto.TeacherInterventionRecommendationDto;
import com.capstone.assessment.analytics.service.AnalyticsService;
import com.capstone.assessment.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    // Returns item-level correctness rates for a selected test.
    @GetMapping("/item-analysis")
    public ApiResponse<List<ItemAnalysisDto>> getItemAnalysis(@RequestParam Long testId) {
        List<ItemAnalysisDto> response = analyticsService.getItemAnalysis(testId);
        return ApiResponse.success("Item analysis retrieved successfully.", response);
    }

    // Returns least mastered skills computed from synced backend item-level records.
    @GetMapping("/lms")
    public ApiResponse<List<LmsDto>> getLmsByTest(@RequestParam Long testId) {
        List<LmsDto> response = analyticsService.getLmsByTest(testId);
        return ApiResponse.success("Least mastered skills retrieved successfully.", response);
    }

    // This identifies students affected by least mastered competencies so teachers can plan remediation.
    @GetMapping("/lms-affected-students")
    public ApiResponse<List<LmsAffectedStudentsDto>> getLmsAffectedStudents(@RequestParam Long testId) {
        List<LmsAffectedStudentsDto> response = analyticsService.getLmsAffectedStudents(testId);
        return ApiResponse.success("LMS affected students retrieved successfully.", response);
    }

    // Returns students who need intervention for the selected competency and test.
    @GetMapping("/intervention")
    public ApiResponse<List<InterventionStudentDto>> getStudentsForIntervention(
            @RequestParam Long testId,
            @RequestParam Long competencyId
    ) {
        List<InterventionStudentDto> response = analyticsService.getStudentsForIntervention(testId, competencyId);
        return ApiResponse.success("Intervention students retrieved successfully.", response);
    }

    // Returns teacher-facing intervention recommendations generated from synced assessment results.
    @GetMapping("/teacher-interventions")
    public ApiResponse<List<TeacherInterventionRecommendationDto>> getTeacherInterventionRecommendations(
            @RequestParam Long testId
    ) {
        List<TeacherInterventionRecommendationDto> response =
                analyticsService.getTeacherInterventionRecommendations(testId);
        return ApiResponse.success("Teacher intervention recommendations retrieved successfully.", response);
    }

    // Returns school-wide LMS with optional filters for principal-level analysis.
    @GetMapping("/school-lms")
    public ApiResponse<List<SchoolLmsDto>> getSchoolWideLms(
            @RequestParam(required = false) Long gradeLevelId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long teacherId
    ) {
        List<SchoolLmsDto> response = analyticsService.getSchoolWideLms(
                gradeLevelId,
                sectionId,
                subjectId,
                teacherId
        );
        return ApiResponse.success("School-wide LMS retrieved successfully.", response);
    }

    // Returns all assessed competency mastery results for one student in one class.
    @GetMapping("/student-skill-mastery")
    public ApiResponse<List<StudentSkillMasteryDto>> getStudentSkillMastery(
            @RequestParam Long studentId,
            @RequestParam Long classId
    ) {
        List<StudentSkillMasteryDto> response = analyticsService.getStudentSkillMastery(studentId, classId);
        return ApiResponse.success("Student skill mastery retrieved successfully.", response);
    }

    // Returns assessment score trends for a selected class over time.
    @GetMapping("/trends")
    public ApiResponse<List<AssessmentTrendDto>> getAssessmentTrends(@RequestParam Long classId) {
        List<AssessmentTrendDto> response = analyticsService.getAssessmentTrends(classId);
        return ApiResponse.success("Assessment trends retrieved successfully.", response);
    }

    // Returns teacher sync activity to help indicate data freshness.
    @GetMapping("/sync-activity")
    public ApiResponse<List<SyncActivityDto>> getSyncActivity(@RequestParam Long teacherId) {
        List<SyncActivityDto> response = analyticsService.getSyncActivity(teacherId);
        return ApiResponse.success("Teacher sync activity retrieved successfully.", response);
    }
}
