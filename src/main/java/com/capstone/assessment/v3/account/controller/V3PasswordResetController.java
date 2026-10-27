package com.capstone.assessment.v3.account.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.account.dto.V3AccountCodeResponse;
import com.capstone.assessment.v3.account.dto.V3PasswordResetRequest;
import com.capstone.assessment.v3.account.service.V3AccountSettingsService;
import com.capstone.assessment.v3.auth.service.V3PublicRateLimiter;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** TCH-7: forgot password, signed out. The phone app opens the web page for this. */
@Profile("v3")
@RestController
@RequestMapping("/api/v3/auth/password-reset")
public class V3PasswordResetController {

    private static final int CALLS_PER_TEN_MINUTES = 10;

    private final V3AccountSettingsService service;
    private final V3PublicRateLimiter rateLimiter;

    public V3PasswordResetController(V3AccountSettingsService service, V3PublicRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<V3AccountCodeResponse>> request(
            @Valid @RequestBody V3PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        limit(httpRequest);
        return ResponseEntity.ok(ApiResponse.success("If the account exists, a code was sent.",
                service.requestPasswordReset(request, metadata(httpRequest))));
    }

    @PostMapping("/resend")
    public ResponseEntity<ApiResponse<V3AccountCodeResponse>> resend(
            @Valid @RequestBody V3PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        limit(httpRequest);
        return ResponseEntity.ok(ApiResponse.success("If the account exists, a new code was sent.",
                service.resendPasswordReset(request, metadata(httpRequest))));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Map<String, Instant>>> confirm(
            @Valid @RequestBody V3PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        limit(httpRequest);
        Instant changedAt = service.confirmPasswordReset(request, metadata(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Password reset. Sign in with the new password.",
                Map.of("passwordChangedAt", changedAt)));
    }

    private void limit(HttpServletRequest request) {
        rateLimiter.check("password-reset:" + metadata(request).ipAddress(), CALLS_PER_TEN_MINUTES,
                Duration.ofMinutes(10), "PASSWORD_RESET_RATE_LIMITED");
    }

    private V3RequestMetadata metadata(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ipAddress = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        return new V3RequestMetadata(ipAddress, request.getHeader("User-Agent"), null);
    }
}
