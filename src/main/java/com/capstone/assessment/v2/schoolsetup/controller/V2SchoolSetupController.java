package com.capstone.assessment.v2.schoolsetup.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
import com.capstone.assessment.v2.schoolsetup.dto.V2AvailableClassResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2ClassAssignmentResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2CreateClassAssignmentRequest;
import com.capstone.assessment.v2.schoolsetup.dto.V2ReactivateClassAssignmentRequest;
import com.capstone.assessment.v2.schoolsetup.dto.V2SchoolSetupReferenceResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2UpdateClassAssignmentRequest;
import com.capstone.assessment.v2.schoolsetup.service.V2SchoolSetupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("v2")
@RestController
@RequestMapping("/api/v2/school-setup")
public class V2SchoolSetupController {

    private static final String DEVICE_IDENTIFIER_HEADER = "X-Device-Identifier";

    private final V2SchoolSetupService schoolSetupService;

    public V2SchoolSetupController(V2SchoolSetupService schoolSetupService) {
        this.schoolSetupService = schoolSetupService;
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ApiResponse<V2SchoolSetupReferenceResponse>> getReferenceData(
            @AuthenticationPrincipal V2AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "School setup reference data retrieved successfully.",
                schoolSetupService.getReferenceData(principal)
        ));
    }

    @GetMapping("/available-classes")
    public ResponseEntity<ApiResponse<List<V2AvailableClassResponse>>> getAvailableClasses(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam Integer academicYearId,
            @RequestParam Integer gradeLevelId,
            @RequestParam Integer subjectId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Available classes retrieved successfully.",
                schoolSetupService.getAvailableClasses(principal, academicYearId, gradeLevelId, subjectId)
        ));
    }

    @GetMapping("/class-assignments")
    public ResponseEntity<ApiResponse<List<V2ClassAssignmentResponse>>> listClassAssignments(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam(required = false) Integer academicYearId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Class assignments retrieved successfully.",
                schoolSetupService.listAssignments(principal, academicYearId)
        ));
    }

    @PostMapping("/class-assignments")
    public ResponseEntity<ApiResponse<V2ClassAssignmentResponse>> createClassAssignment(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @Valid @RequestBody V2CreateClassAssignmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher assigned to class successfully.",
                schoolSetupService.createAssignment(principal, request, requestMetadata(httpRequest))
        ));
    }

    @RequestMapping(
            value = "/class-assignments/{classAssignmentId}",
            method = RequestMethod.PATCH
    )
    public ResponseEntity<ApiResponse<V2ClassAssignmentResponse>> updateClassAssignment(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long classAssignmentId,
            @Valid @RequestBody V2UpdateClassAssignmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher class assignment updated successfully.",
                schoolSetupService.updateAssignment(
                        principal,
                        classAssignmentId,
                        request,
                        requestMetadata(httpRequest)
                )
        ));
    }

    @RequestMapping(
            value = "/class-assignments/{classAssignmentId}/deactivate",
            method = {RequestMethod.PATCH, RequestMethod.POST}
    )
    public ResponseEntity<ApiResponse<V2ClassAssignmentResponse>> deactivateClassAssignment(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long classAssignmentId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher class assignment removed successfully.",
                schoolSetupService.deactivateAssignment(
                        principal,
                        classAssignmentId,
                        requestMetadata(httpRequest)
                )
        ));
    }

    @RequestMapping(
            value = "/class-assignments/{classAssignmentId}/reactivate",
            method = {RequestMethod.PATCH, RequestMethod.POST}
    )
    public ResponseEntity<ApiResponse<V2ClassAssignmentResponse>> reactivateClassAssignment(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long classAssignmentId,
            @Valid @RequestBody V2ReactivateClassAssignmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher class assignment restored successfully.",
                schoolSetupService.reactivateAssignment(
                        principal,
                        classAssignmentId,
                        request.reason(),
                        requestMetadata(httpRequest)
                )
        ));
    }

    private V2RequestMetadata requestMetadata(HttpServletRequest request) {
        return new V2RequestMetadata(
                clientIpAddress(request),
                request.getHeader("User-Agent"),
                request.getHeader(DEVICE_IDENTIFIER_HEADER)
        );
    }

    private String clientIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }
        return forwardedFor.split(",", 2)[0].trim();
    }
}
