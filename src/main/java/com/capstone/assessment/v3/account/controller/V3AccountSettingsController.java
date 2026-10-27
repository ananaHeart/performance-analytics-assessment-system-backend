package com.capstone.assessment.v3.account.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.account.dto.V3AccountCodeResponse;
import com.capstone.assessment.v3.account.dto.V3AccountProfileRequest;
import com.capstone.assessment.v3.account.dto.V3AccountResponse;
import com.capstone.assessment.v3.account.dto.V3AccountVerificationRequest;
import com.capstone.assessment.v3.account.dto.V3ContactChangeRequest;
import com.capstone.assessment.v3.account.dto.V3EmailChangeRequest;
import com.capstone.assessment.v3.account.dto.V3PasswordChangeRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherSubjectsRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherSubjectsResponse;
import com.capstone.assessment.v3.account.service.V3AccountSettingsService;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** The signed-in user's own settings: SET-1, SET-3, SET-6 and VER-1 (principal or teacher). */
@Profile("v3")
@RestController
@RequestMapping("/api/v3/account")
public class V3AccountSettingsController {

    private final V3AccountSettingsService service;

    public V3AccountSettingsController(V3AccountSettingsService service) {
        this.service = service;
    }

    @GetMapping({"", "/contact-details", "/verification"})
    public ResponseEntity<ApiResponse<V3AccountResponse>> account(@AuthenticationPrincipal V3AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success("Account retrieved successfully.", service.getAccount(user)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<V3AccountResponse>> updateProfile(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3AccountProfileRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully.",
                service.updateProfile(user, request, metadata(httpRequest))));
    }

    /** REG-3: the teacher's own additional majors and subjects taught before. */
    @GetMapping("/subjects")
    public ResponseEntity<ApiResponse<V3TeacherSubjectsResponse>> subjects(
            @AuthenticationPrincipal V3AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success("Subjects retrieved successfully.", service.getSubjects(user)));
    }

    @PutMapping("/subjects")
    public ResponseEntity<ApiResponse<V3TeacherSubjectsResponse>> updateSubjects(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3TeacherSubjectsRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("Subjects updated successfully.",
                service.updateSubjects(user, request, metadata(httpRequest))));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Map<String, Instant>>> changePassword(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3PasswordChangeRequest request,
            HttpServletRequest httpRequest
    ) {
        Instant changedAt = service.changePassword(user, request, metadata(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully.",
                Map.of("passwordChangedAt", changedAt)));
    }

    @PostMapping("/email-change/start")
    public ResponseEntity<ApiResponse<V3AccountCodeResponse>> startEmailChange(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3EmailChangeRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("A code was sent to the new email.",
                service.startEmailChange(user, request, metadata(httpRequest))));
    }

    @PostMapping("/email-change/resend")
    public ResponseEntity<ApiResponse<V3AccountCodeResponse>> resendEmailChange(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3EmailChangeRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("A new code was sent to the new email.",
                service.resendEmailChange(user, request, metadata(httpRequest))));
    }

    @PostMapping("/email-change/confirm")
    public ResponseEntity<ApiResponse<V3AccountResponse>> confirmEmailChange(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3EmailChangeRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("Email changed successfully.",
                service.confirmEmailChange(user, request, metadata(httpRequest))));
    }

    @PostMapping("/contact-change/start")
    public ResponseEntity<ApiResponse<V3AccountCodeResponse>> startContactChange(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3ContactChangeRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("A code was sent to the new number.",
                service.startContactChange(user, request, metadata(httpRequest))));
    }

    @PostMapping("/contact-change/resend")
    public ResponseEntity<ApiResponse<V3AccountCodeResponse>> resendContactChange(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3ContactChangeRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("A new code was sent to the new number.",
                service.resendContactChange(user, request, metadata(httpRequest))));
    }

    @PostMapping("/contact-change/confirm")
    public ResponseEntity<ApiResponse<V3AccountResponse>> confirmContactChange(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3ContactChangeRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("Contact number changed successfully.",
                service.confirmContactChange(user, request, metadata(httpRequest))));
    }

    @PostMapping("/verification/start")
    public ResponseEntity<ApiResponse<V3AccountCodeResponse>> startVerification(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3AccountVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("A verification code was sent.",
                service.startVerification(user, request, metadata(httpRequest))));
    }

    @PostMapping("/verification/resend")
    public ResponseEntity<ApiResponse<V3AccountCodeResponse>> resendVerification(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3AccountVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("A new verification code was sent.",
                service.resendVerification(user, request, metadata(httpRequest))));
    }

    @PostMapping("/verification/confirm")
    public ResponseEntity<ApiResponse<V3AccountResponse>> confirmVerification(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3AccountVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("Verified successfully.",
                service.confirmVerification(user, request, metadata(httpRequest))));
    }

    private V3RequestMetadata metadata(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ipAddress = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        return new V3RequestMetadata(ipAddress, request.getHeader("User-Agent"), null);
    }
}
