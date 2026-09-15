package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3AnswerSheetManifestResponse;
import com.capstone.assessment.v3.mobile.dto.V3MobileDownloadResponse;
import com.capstone.assessment.v3.mobile.dto.V3MobileReferenceDataResponse;
import com.capstone.assessment.v3.mobile.service.V3MobileService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/mobile")
public class V3MobileController {

    private final V3MobileService mobileService;

    public V3MobileController(V3MobileService mobileService) {
        this.mobileService = mobileService;
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ApiResponse<V3MobileReferenceDataResponse>> referenceData(
            @AuthenticationPrincipal V3AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 Mobile reference data retrieved successfully.",
                mobileService.getReferenceData(user)
        ));
    }

    @GetMapping("/download")
    public ResponseEntity<ApiResponse<V3MobileDownloadResponse>> download(
            @AuthenticationPrincipal V3AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher-owned V3 Mobile snapshot retrieved successfully.",
                mobileService.download(user)
        ));
    }

    @GetMapping("/test-assignments/{assignmentUuid}/answer-sheets/{answerSheetUuid}/manifest")
    public ResponseEntity<ApiResponse<V3AnswerSheetManifestResponse>> manifest(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable String assignmentUuid,
            @PathVariable String answerSheetUuid
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 answer-sheet manifest retrieved successfully.",
                mobileService.getManifest(user, assignmentUuid, answerSheetUuid)
        ));
    }
}
