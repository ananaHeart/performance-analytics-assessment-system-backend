package com.capstone.assessment.v3.schedule.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.schedule.dto.V3ClassScheduleRequest;
import com.capstone.assessment.v3.schedule.dto.V3ClassScheduleResponse;
import com.capstone.assessment.v3.schedule.dto.V3ClassScheduleStatusRequest;
import com.capstone.assessment.v3.schedule.service.V3ClassScheduleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RestController
@RequestMapping("/api/v3/teacher/class-assignments/{classAssignmentId}/schedules")
public class V3ClassScheduleController {

    private final V3ClassScheduleService service;

    public V3ClassScheduleController(V3ClassScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<V3ClassScheduleResponse>>> list(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable @Positive long classAssignmentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 class schedules retrieved successfully.",
                service.listSchedules(principal, classAssignmentId)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<V3ClassScheduleResponse>> create(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable @Positive long classAssignmentId,
            @Valid @RequestBody V3ClassScheduleRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "V3 class schedule created successfully.",
                service.createSchedule(principal, classAssignmentId, request, metadata(httpRequest))
        ));
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<V3ClassScheduleResponse>> update(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable @Positive long classAssignmentId,
            @PathVariable @Positive long scheduleId,
            @Valid @RequestBody V3ClassScheduleRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 class schedule updated successfully.",
                service.updateSchedule(
                        principal, classAssignmentId, scheduleId, request, metadata(httpRequest)
                )
        ));
    }

    @PatchMapping("/{scheduleId}/archive")
    public ResponseEntity<ApiResponse<V3ClassScheduleResponse>> archive(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable @Positive long classAssignmentId,
            @PathVariable @Positive long scheduleId,
            @Valid @RequestBody V3ClassScheduleStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 class schedule archived successfully.",
                service.archiveSchedule(
                        principal, classAssignmentId, scheduleId, request, metadata(httpRequest)
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
