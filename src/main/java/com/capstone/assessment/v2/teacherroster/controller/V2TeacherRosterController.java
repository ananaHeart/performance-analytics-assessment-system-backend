package com.capstone.assessment.v2.teacherroster.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.importexport.dto.V2StudentRecordResponse;
import com.capstone.assessment.v2.teacherroster.service.V2TeacherRosterService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("v2")
@RestController
@RequestMapping("/api/v2/teacher/classes")
public class V2TeacherRosterController {

    private final V2TeacherRosterService rosterService;

    public V2TeacherRosterController(V2TeacherRosterService rosterService) {
        this.rosterService = rosterService;
    }

    @GetMapping("/{classId}/students")
    public ResponseEntity<ApiResponse<List<V2StudentRecordResponse>>> listStudents(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long classId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher class roster retrieved successfully.",
                rosterService.listStudents(principal, classId)
        ));
    }
}
