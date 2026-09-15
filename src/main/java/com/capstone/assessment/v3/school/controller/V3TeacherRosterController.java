package com.capstone.assessment.v3.school.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.school.dto.V3StudentRosterEntryResponse;
import com.capstone.assessment.v3.school.service.V3SchoolSetupService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/teacher/classes")
public class V3TeacherRosterController {

    private final V3SchoolSetupService service;

    public V3TeacherRosterController(V3SchoolSetupService service) {
        this.service = service;
    }

    @GetMapping("/{classId}/students")
    public ResponseEntity<ApiResponse<List<V3StudentRosterEntryResponse>>> roster(
            @AuthenticationPrincipal V3AuthenticatedUser teacher,
            @PathVariable long classId,
            @RequestParam(required = false) String enrollmentStatus
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 teacher class roster retrieved successfully.",
                service.getTeacherRoster(teacher, classId, enrollmentStatus)
        ));
    }
}
