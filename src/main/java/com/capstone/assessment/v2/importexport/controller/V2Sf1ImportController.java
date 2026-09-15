package com.capstone.assessment.v2.importexport.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.importexport.dto.Sf1ImportPreviewResponse;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.importexport.dto.V2Sf1ImportSummaryResponse;
import com.capstone.assessment.v2.importexport.dto.V2StudentRecordResponse;
import com.capstone.assessment.v2.importexport.service.V2Sf1ImportService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Profile("v2")
@RestController
@RequestMapping("/api/v2/import")
public class V2Sf1ImportController {

    private final V2Sf1ImportService sf1ImportService;

    public V2Sf1ImportController(V2Sf1ImportService sf1ImportService) {
        this.sf1ImportService = sf1ImportService;
    }

    @GetMapping("/students")
    public ResponseEntity<ApiResponse<List<V2StudentRecordResponse>>> listStudents(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam(required = false) Integer academicYearId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Student records retrieved successfully.",
                sf1ImportService.listStudents(principal, academicYearId)
        ));
    }

    @PostMapping("/sf1/preview")
    public ResponseEntity<ApiResponse<Sf1ImportPreviewResponse>> preview(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "SF1 preview generated successfully.",
                sf1ImportService.preview(file, principal)
        ));
    }

    @PostMapping("/sf1/confirm")
    public ResponseEntity<ApiResponse<V2Sf1ImportSummaryResponse>> confirm(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam("gradeLevelId") Integer gradeLevelId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "SF1 import completed successfully.",
                sf1ImportService.confirm(file, principal, gradeLevelId)
        ));
    }
}
