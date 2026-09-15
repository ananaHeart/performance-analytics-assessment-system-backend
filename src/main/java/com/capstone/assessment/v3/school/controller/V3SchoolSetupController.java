package com.capstone.assessment.v3.school.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.school.dto.V3ClassAssignmentRequest;
import com.capstone.assessment.v3.school.dto.V3ClassAssignmentResponse;
import com.capstone.assessment.v3.school.dto.V3ClassAssignmentStatusRequest;
import com.capstone.assessment.v3.school.dto.V3ClassRequest;
import com.capstone.assessment.v3.school.dto.V3ClassResponse;
import com.capstone.assessment.v3.school.dto.V3ManualStudentEnrollmentRequest;
import com.capstone.assessment.v3.school.dto.V3ManualStudentEnrollmentResponse;
import com.capstone.assessment.v3.school.dto.V3SchoolProfileResponse;
import com.capstone.assessment.v3.school.dto.V3SchoolProfileUpdateRequest;
import com.capstone.assessment.v3.school.dto.V3SchoolSetupReferenceDataResponse;
import com.capstone.assessment.v3.school.dto.V3StudentEnrollmentStatusRequest;
import com.capstone.assessment.v3.school.dto.V3StudentProfileUpdateRequest;
import com.capstone.assessment.v3.school.dto.V3StudentRosterEntryResponse;
import com.capstone.assessment.v3.school.service.V3SchoolSetupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/school-setup")
public class V3SchoolSetupController {

    private final V3SchoolSetupService service;

    public V3SchoolSetupController(V3SchoolSetupService service) {
        this.service = service;
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ApiResponse<V3SchoolSetupReferenceDataResponse>> referenceData(
            @AuthenticationPrincipal V3AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 school-setup reference data retrieved successfully.",
                service.getReferenceData(principal)
        ));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<V3SchoolProfileResponse>> profile(
            @AuthenticationPrincipal V3AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 school profile retrieved successfully.",
                service.getSchoolProfile(principal)
        ));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<V3SchoolProfileResponse>> updateProfile(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @Valid @RequestBody V3SchoolProfileUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 school profile updated successfully.",
                service.updateSchoolProfile(principal, request, metadata(httpRequest))
        ));
    }

    @GetMapping("/classes")
    public ResponseEntity<ApiResponse<List<V3ClassResponse>>> classes(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) Integer gradeLevelId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 classes retrieved successfully.",
                service.listClasses(principal, academicYearId, gradeLevelId)
        ));
    }

    @PostMapping("/classes")
    public ResponseEntity<ApiResponse<V3ClassResponse>> createClass(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @Valid @RequestBody V3ClassRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 class is ready.",
                service.createClass(principal, request, metadata(httpRequest))
        ));
    }

    @GetMapping("/classes/{classId}/students")
    public ResponseEntity<ApiResponse<List<V3StudentRosterEntryResponse>>> classRoster(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long classId,
            @RequestParam(required = false) String enrollmentStatus
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 class roster retrieved successfully.",
                service.getPrincipalRoster(principal, classId, enrollmentStatus)
        ));
    }

    @PostMapping("/classes/{classId}/students")
    public ResponseEntity<ApiResponse<V3ManualStudentEnrollmentResponse>> enrollStudentManually(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long classId,
            @Valid @RequestBody V3ManualStudentEnrollmentRequest request,
            HttpServletRequest httpRequest
    ) {
        V3ManualStudentEnrollmentResponse response = service.enrollStudentManually(
                principal, classId, request, metadata(httpRequest)
        );
        HttpStatus status = response.enrollmentCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(
                response.enrollmentCreated()
                        ? "V3 student enrolled successfully."
                        : "V3 student enrollment is ready.",
                response
        ));
    }

    @PutMapping("/classes/{classId}/students/{studentId}")
    public ResponseEntity<ApiResponse<V3StudentRosterEntryResponse>> updateStudentProfile(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long classId,
            @PathVariable long studentId,
            @Valid @RequestBody V3StudentProfileUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 student profile corrected successfully.",
                service.updateStudentProfile(
                        principal, classId, studentId, request, metadata(httpRequest)
                )
        ));
    }

    @PatchMapping("/class-lists/{classListId}/status")
    public ResponseEntity<ApiResponse<V3StudentRosterEntryResponse>> updateEnrollmentStatus(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long classListId,
            @Valid @RequestBody V3StudentEnrollmentStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 enrollment status updated successfully.",
                service.updateEnrollmentStatus(
                        principal, classListId, request, metadata(httpRequest)
                )
        ));
    }

    @GetMapping("/class-assignments")
    public ResponseEntity<ApiResponse<List<V3ClassAssignmentResponse>>> assignments(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @RequestParam(required = false) Integer academicYearId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 class assignments retrieved successfully.",
                service.listAssignments(principal, academicYearId)
        ));
    }

    @PostMapping("/class-assignments")
    public ResponseEntity<ApiResponse<V3ClassAssignmentResponse>> assignTeacher(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @Valid @RequestBody V3ClassAssignmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 teacher class assignment is ready.",
                service.assignTeacher(principal, request, metadata(httpRequest))
        ));
    }

    @PatchMapping("/class-assignments/{classAssignmentId}/archive")
    public ResponseEntity<ApiResponse<V3ClassAssignmentResponse>> archiveAssignment(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long classAssignmentId,
            @Valid @RequestBody V3ClassAssignmentStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 class assignment archived successfully.",
                service.archiveAssignment(principal, classAssignmentId, request, metadata(httpRequest))
        ));
    }

    @PatchMapping("/class-assignments/{classAssignmentId}/reactivate")
    public ResponseEntity<ApiResponse<V3ClassAssignmentResponse>> reactivateAssignment(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long classAssignmentId,
            @Valid @RequestBody V3ClassAssignmentStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 class assignment reactivated successfully.",
                service.reactivateAssignment(principal, classAssignmentId, request, metadata(httpRequest))
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
