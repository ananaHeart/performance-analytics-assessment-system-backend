package com.capstone.assessment.importexport.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.importexport.dto.ManualStudentRequest;
import com.capstone.assessment.importexport.dto.ManualStudentResponse;
import com.capstone.assessment.importexport.dto.ManualStudentUpdateRequest;
import com.capstone.assessment.importexport.service.ManualStudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/import/manual-students")
public class ManualStudentController {

    private final ManualStudentService manualStudentService;

    public ManualStudentController(ManualStudentService manualStudentService) {
        this.manualStudentService = manualStudentService;
    }

    // Lists student records encoded manually as a fallback to Smart Upload.
    @GetMapping
    public ApiResponse<List<ManualStudentResponse>> getStudents() {
        List<ManualStudentResponse> response = manualStudentService.getStudents();
        return ApiResponse.success("Students retrieved successfully.", response);
    }

    // Creates a student record and enrollment entry for manual input fallback.
    @PostMapping
    public ApiResponse<ManualStudentResponse> createStudent(@RequestBody ManualStudentRequest request) {
        ManualStudentResponse response = manualStudentService.createStudent(request);
        return ApiResponse.success("Student created successfully.", response);
    }

    // Updates the student record and latest enrollment details for manual maintenance.
    @PutMapping("/{studentId}")
    public ApiResponse<ManualStudentResponse> updateStudent(
            @PathVariable Long studentId,
            @RequestBody ManualStudentUpdateRequest request
    ) {
        ManualStudentResponse response = manualStudentService.updateStudent(studentId, request);
        return ApiResponse.success("Student updated successfully.", response);
    }
}
