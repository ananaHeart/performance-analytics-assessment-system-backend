package com.capstone.assessment.v3.importexport.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.importexport.dto.V3Sf1ImportResponse;
import com.capstone.assessment.v3.importexport.dto.V3Sf1PreviewResponse;
import com.capstone.assessment.v3.importexport.service.V3Sf1ImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/import/sf1")
public class V3Sf1ImportController {

    private final V3Sf1ImportService service;

    public V3Sf1ImportController(V3Sf1ImportService service) {
        this.service = service;
    }

    @PostMapping(path = "/preview", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<V3Sf1PreviewResponse>> preview(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam int academicYearId,
            @RequestParam int gradeLevelId,
            @RequestParam(required = false) String sectionName
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 SF1 preview completed. No learner data was changed.",
                service.preview(principal, file, academicYearId, gradeLevelId, sectionName)
        ));
    }

    @PostMapping(path = "/confirm", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<V3Sf1ImportResponse>> confirm(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam String importUuid,
            @RequestParam int academicYearId,
            @RequestParam int gradeLevelId,
            @RequestParam(required = false) String sectionName,
            @RequestParam String expectedFileHash,
            @RequestParam(defaultValue = "false") boolean acceptContextMismatch,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 SF1 import processed transactionally.",
                service.confirm(
                        principal, file, importUuid, academicYearId, gradeLevelId, sectionName,
                        expectedFileHash, acceptContextMismatch, metadata(request)
                )
        ));
    }

    private V3RequestMetadata metadata(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ipAddress = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        return new V3RequestMetadata(ipAddress, request.getHeader("User-Agent"), null);
    }
}
