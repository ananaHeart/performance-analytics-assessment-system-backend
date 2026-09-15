package com.capstone.assessment.v3.school.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.school.dto.V3AcademicYearRequest;
import com.capstone.assessment.v3.school.dto.V3AcademicYearResponse;
import com.capstone.assessment.v3.school.dto.V3LifecycleReasonRequest;
import com.capstone.assessment.v3.school.service.V3AcademicCalendarService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/school-setup/academic-years")
public class V3AcademicCalendarController {

    private final V3AcademicCalendarService service;

    public V3AcademicCalendarController(V3AcademicCalendarService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<V3AcademicYearResponse>>> list(
            @AuthenticationPrincipal V3AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 academic years retrieved successfully.",
                service.listAcademicYears(principal)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<V3AcademicYearResponse>> create(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @Valid @RequestBody V3AcademicYearRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "V3 academic year and four term periods created successfully.",
                service.createAcademicYear(principal, request, metadata(httpRequest))
        ));
    }

    @GetMapping("/{academicYearId}")
    public ResponseEntity<ApiResponse<V3AcademicYearResponse>> get(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable int academicYearId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 academic year retrieved successfully.",
                service.getAcademicYear(principal, academicYearId)
        ));
    }

    @PutMapping("/{academicYearId}")
    public ResponseEntity<ApiResponse<V3AcademicYearResponse>> update(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable int academicYearId,
            @Valid @RequestBody V3AcademicYearRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 planned academic year updated successfully.",
                service.updateAcademicYear(principal, academicYearId, request, metadata(httpRequest))
        ));
    }

    @PatchMapping("/{academicYearId}/activate")
    public ResponseEntity<ApiResponse<V3AcademicYearResponse>> activateYear(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable int academicYearId,
            @Valid @RequestBody V3LifecycleReasonRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 academic year activated successfully.",
                service.activateAcademicYear(principal, academicYearId, request, metadata(httpRequest))
        ));
    }

    @PatchMapping("/{academicYearId}/complete")
    public ResponseEntity<ApiResponse<V3AcademicYearResponse>> completeYear(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable int academicYearId,
            @Valid @RequestBody V3LifecycleReasonRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 academic year completed successfully.",
                service.completeAcademicYear(principal, academicYearId, request, metadata(httpRequest))
        ));
    }

    @PatchMapping("/{academicYearId}/term-periods/{termPeriodId}/activate")
    public ResponseEntity<ApiResponse<V3AcademicYearResponse>> activateTerm(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable int academicYearId,
            @PathVariable int termPeriodId,
            @Valid @RequestBody V3LifecycleReasonRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 term period activated successfully.",
                service.activateTermPeriod(
                        principal, academicYearId, termPeriodId, request, metadata(httpRequest)
                )
        ));
    }

    @PatchMapping("/{academicYearId}/term-periods/{termPeriodId}/complete")
    public ResponseEntity<ApiResponse<V3AcademicYearResponse>> completeTerm(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable int academicYearId,
            @PathVariable int termPeriodId,
            @Valid @RequestBody V3LifecycleReasonRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 term period completed successfully.",
                service.completeTermPeriod(
                        principal, academicYearId, termPeriodId, request, metadata(httpRequest)
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
