package com.capstone.assessment.v2.account.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v2.account.dto.V2CreateTeacherRequest;
import com.capstone.assessment.v2.account.dto.V2TeacherAccountResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherReferenceDataResponse;
import com.capstone.assessment.v2.account.service.V2TeacherAccountService;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("v2")
@RestController
@RequestMapping("/api/v2/users/teachers")
public class V2TeacherAccountController {

    private static final String DEVICE_IDENTIFIER_HEADER = "X-Device-Identifier";

    private final V2TeacherAccountService accountService;

    public V2TeacherAccountController(V2TeacherAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<V2TeacherAccountResponse>> createTeacher(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @Valid @RequestBody V2CreateTeacherRequest request,
            HttpServletRequest httpRequest
    ) {
        V2TeacherAccountResponse response = accountService.createTeacher(
                principal,
                request,
                requestMetadata(httpRequest)
        );
        return ResponseEntity.ok(ApiResponse.success("Teacher account created and is pending approval.", response));
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ApiResponse<V2TeacherReferenceDataResponse>> getReferenceData(
            @AuthenticationPrincipal V2AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher reference data retrieved successfully.",
                accountService.getReferenceData(principal)
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<V2TeacherAccountResponse>>> listTeachers(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher accounts retrieved successfully.",
                accountService.listTeachers(principal, status)
        ));
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<ApiResponse<V2TeacherAccountResponse>> approveTeacher(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long userId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher account approved successfully.",
                accountService.approveTeacher(principal, userId, requestMetadata(httpRequest))
        ));
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<V2TeacherAccountResponse>> rejectTeacher(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long userId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher account rejected successfully.",
                accountService.rejectTeacher(principal, userId, requestMetadata(httpRequest))
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
