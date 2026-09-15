package com.capstone.assessment.v3.auth.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.dto.V3ConfirmMfaEnrollmentRequest;
import com.capstone.assessment.v3.auth.dto.V3LoginResponse;
import com.capstone.assessment.v3.auth.dto.V3MfaEnrollmentRequest;
import com.capstone.assessment.v3.auth.dto.V3MfaEnrollmentResponse;
import com.capstone.assessment.v3.auth.dto.V3MfaRecoveryCodesResponse;
import com.capstone.assessment.v3.auth.dto.V3MfaSensitiveActionRequest;
import com.capstone.assessment.v3.auth.dto.V3MfaStatusResponse;
import com.capstone.assessment.v3.auth.dto.V3VerifyMfaLoginRequest;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuthService;
import com.capstone.assessment.v3.auth.service.V3MfaService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/auth/mfa")
public class V3MfaController {

    private final V3AuthService authService;
    private final V3MfaService mfaService;

    public V3MfaController(V3AuthService authService, V3MfaService mfaService) {
        this.authService = authService;
        this.mfaService = mfaService;
    }

    @PostMapping("/login/verify")
    public ResponseEntity<ApiResponse<V3LoginResponse>> verifyLogin(
            @Valid @RequestBody V3VerifyMfaLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Login successful.",
                authService.verifyMfaLogin(request, requestMetadata(httpRequest, request.deviceIdentifier()))
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<V3MfaStatusResponse>> status(
            @AuthenticationPrincipal V3AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Authenticator security status retrieved successfully.",
                mfaService.status(authenticatedUser)
        ));
    }

    @PostMapping("/enrollment")
    public ResponseEntity<ApiResponse<V3MfaEnrollmentResponse>> beginEnrollment(
            @AuthenticationPrincipal V3AuthenticatedUser authenticatedUser,
            @Valid @RequestBody V3MfaEnrollmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Authenticator enrollment started.",
                mfaService.beginEnrollment(
                        authenticatedUser,
                        request,
                        requestMetadata(httpRequest, null)
                )
        ));
    }

    @PostMapping("/enrollment/confirm")
    public ResponseEntity<ApiResponse<V3MfaRecoveryCodesResponse>> confirmEnrollment(
            @AuthenticationPrincipal V3AuthenticatedUser authenticatedUser,
            @Valid @RequestBody V3ConfirmMfaEnrollmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Authenticator security enabled. Store the recovery codes securely.",
                mfaService.confirmEnrollment(
                        authenticatedUser,
                        request,
                        requestMetadata(httpRequest, null)
                )
        ));
    }

    @PostMapping("/recovery-codes/regenerate")
    public ResponseEntity<ApiResponse<V3MfaRecoveryCodesResponse>> regenerateRecoveryCodes(
            @AuthenticationPrincipal V3AuthenticatedUser authenticatedUser,
            @Valid @RequestBody V3MfaSensitiveActionRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "New recovery codes generated. Previous recovery codes are no longer valid.",
                mfaService.regenerateRecoveryCodes(
                        authenticatedUser,
                        request,
                        requestMetadata(httpRequest, null)
                )
        ));
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            @AuthenticationPrincipal V3AuthenticatedUser authenticatedUser,
            @Valid @RequestBody V3MfaSensitiveActionRequest request,
            HttpServletRequest httpRequest
    ) {
        mfaService.disable(
                authenticatedUser,
                request,
                requestMetadata(httpRequest, null)
        );
        return ResponseEntity.ok(ApiResponse.success("Authenticator security disabled.", null));
    }

    private V3RequestMetadata requestMetadata(HttpServletRequest request, String deviceIdentifier) {
        return new V3RequestMetadata(
                clientIpAddress(request),
                request.getHeader("User-Agent"),
                deviceIdentifier
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
