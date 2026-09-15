package com.capstone.assessment.v3.account.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.account.dto.V3RejectTeacherRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountDetailResponse;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountSummaryResponse;
import com.capstone.assessment.v3.account.service.V3TeacherAccountService;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
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

@Profile("v3")
@RestController
@RequestMapping("/api/v3/users/teachers")
public class V3TeacherAccountController {

    private final V3TeacherAccountService teacherAccountService;

    public V3TeacherAccountController(V3TeacherAccountService teacherAccountService) {
        this.teacherAccountService = teacherAccountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<V3TeacherAccountSummaryResponse>>> list(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 teacher accounts retrieved successfully.",
                teacherAccountService.listTeachers(principal, status)
        ));
    }

    @GetMapping("/{teacherUserId}")
    public ResponseEntity<ApiResponse<V3TeacherAccountDetailResponse>> get(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long teacherUserId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 teacher account retrieved successfully.",
                teacherAccountService.getTeacher(principal, teacherUserId)
        ));
    }

    @PostMapping("/{teacherUserId}/approve")
    public ResponseEntity<ApiResponse<V3TeacherAccountDetailResponse>> approve(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long teacherUserId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher account approved successfully.",
                teacherAccountService.approveTeacher(
                        principal,
                        teacherUserId,
                        requestMetadata(httpRequest)
                )
        ));
    }

    @PostMapping("/{teacherUserId}/reject")
    public ResponseEntity<ApiResponse<V3TeacherAccountDetailResponse>> reject(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long teacherUserId,
            @Valid @RequestBody V3RejectTeacherRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher account rejected successfully.",
                teacherAccountService.rejectTeacher(
                        principal,
                        teacherUserId,
                        request,
                        requestMetadata(httpRequest)
                )
        ));
    }

    private V3RequestMetadata requestMetadata(HttpServletRequest request) {
        return new V3RequestMetadata(
                clientIpAddress(request),
                request.getHeader("User-Agent"),
                null
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
