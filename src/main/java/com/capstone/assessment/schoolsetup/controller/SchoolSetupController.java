package com.capstone.assessment.schoolsetup.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.schoolsetup.dto.ClassAssignmentDto;
import com.capstone.assessment.schoolsetup.dto.CreateClassAssignmentRequest;
import com.capstone.assessment.schoolsetup.dto.CreateSectionRequest;
import com.capstone.assessment.schoolsetup.dto.GradeLevelDto;
import com.capstone.assessment.schoolsetup.dto.SectionDto;
import com.capstone.assessment.schoolsetup.dto.StudentDto;
import com.capstone.assessment.schoolsetup.dto.SubjectDto;
import com.capstone.assessment.schoolsetup.dto.TeacherDto;
import com.capstone.assessment.schoolsetup.service.SchoolSetupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/school-setup")
public class SchoolSetupController {

    private final SchoolSetupService schoolSetupService;

    public SchoolSetupController(SchoolSetupService schoolSetupService) {
        this.schoolSetupService = schoolSetupService;
    }

    // Returns all available grade levels for school setup management.
    @GetMapping("/grade-levels")
    public ApiResponse<List<GradeLevelDto>> getGradeLevels() {
        List<GradeLevelDto> response = schoolSetupService.getGradeLevels();
        return ApiResponse.success("Grade levels retrieved successfully.", response);
    }

    // Returns all configured subjects.
    @GetMapping("/subjects")
    public ApiResponse<List<SubjectDto>> getSubjects() {
        List<SubjectDto> response = schoolSetupService.getSubjects();
        return ApiResponse.success("Subjects retrieved successfully.", response);
    }

    // Returns all sections with their grade levels.
    @GetMapping("/sections")
    public ApiResponse<List<SectionDto>> getSections() {
        List<SectionDto> response = schoolSetupService.getSections();
        return ApiResponse.success("Sections retrieved successfully.", response);
    }

    // Returns all teacher accounts used for class assignment.
    @GetMapping("/teachers")
    public ApiResponse<List<TeacherDto>> getTeachers() {
        List<TeacherDto> response = schoolSetupService.getTeachers();
        return ApiResponse.success("Teachers retrieved successfully.", response);
    }

    // Returns student records used for class lists and analytics.
    @GetMapping("/students")
    public ApiResponse<List<StudentDto>> getStudents() {
        List<StudentDto> response = schoolSetupService.getStudents();
        return ApiResponse.success("Students retrieved successfully.", response);
    }

    // Returns all class assignments across teachers, sections, and subjects.
    @GetMapping("/class-assignments")
    public ApiResponse<List<ClassAssignmentDto>> getClassAssignments() {
        List<ClassAssignmentDto> response = schoolSetupService.getClassAssignments();
        return ApiResponse.success("Class assignments retrieved successfully.", response);
    }

    // Creates a new section for a selected grade level.
    @PostMapping("/sections")
    public ApiResponse<Long> createSection(@RequestBody CreateSectionRequest request) {
        Long response = schoolSetupService.createSection(request);
        return ApiResponse.success("Section created successfully.", response);
    }

    // Creates a new teacher-subject-section class assignment for an academic year.
    @PostMapping("/class-assignments")
    public ApiResponse<Long> createClassAssignment(@RequestBody CreateClassAssignmentRequest request) {
        Long response = schoolSetupService.createClassAssignment(request);
        return ApiResponse.success("Class assignment created successfully.", response);
    }
}
