package com.capstone.assessment.v3.auth.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.dto.V3CurrentUserResponse;
import com.capstone.assessment.v3.auth.dto.V3EmailVerificationResponse;
import com.capstone.assessment.v3.auth.dto.V3LoginRequest;
import com.capstone.assessment.v3.auth.dto.V3LoginResponse;
import com.capstone.assessment.v3.auth.dto.V3RegistrationReferenceDataResponse;
import com.capstone.assessment.v3.auth.dto.V3RegistrationResponse;
import com.capstone.assessment.v3.auth.dto.V3ResendVerificationRequest;
import com.capstone.assessment.v3.auth.dto.V3TeacherRegistrationRequest;
import com.capstone.assessment.v3.auth.dto.V3VerifyEmailRequest;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuthService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.auth.service.V3TeacherRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/auth")
public class V3AuthController {

    private final V3AuthService authService;
    private final V3TeacherRegistrationService registrationService;

    public V3AuthController(
            V3AuthService authService,
            V3TeacherRegistrationService registrationService
    ) {
        this.authService = authService;
        this.registrationService = registrationService;
    }

    @GetMapping("/teacher-registration/reference-data")
    public ResponseEntity<ApiResponse<V3RegistrationReferenceDataResponse>> registrationReferenceData() {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher registration reference data retrieved successfully.",
                registrationService.getReferenceData()
        ));
    }

    @PostMapping("/register-teacher")
    public ResponseEntity<ApiResponse<V3RegistrationResponse>> registerTeacher(
            @Valid @RequestBody V3TeacherRegistrationRequest request,
            HttpServletRequest httpRequest
    ) {
        V3RegistrationResponse response = registrationService.register(
                request,
                requestMetadata(httpRequest, null)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Teacher registration saved. Enter the code sent to the registered email.",
                response
        ));
    }

    @PostMapping("/verify-teacher-email")
    public ResponseEntity<ApiResponse<V3EmailVerificationResponse>> verifyTeacherEmail(
            @Valid @RequestBody V3VerifyEmailRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Email verified successfully. The account is pending principal approval.",
                registrationService.verify(request, requestMetadata(httpRequest, null))
        ));
    }

    @PostMapping("/resend-teacher-verification")
    public ResponseEntity<ApiResponse<V3EmailVerificationResponse>> resendTeacherVerification(
            @Valid @RequestBody V3ResendVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "A new email verification code was issued.",
                registrationService.resend(request, requestMetadata(httpRequest, null))
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<V3LoginResponse>> login(
            @Valid @RequestBody V3LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        V3LoginResponse response = authService.login(
                request,
                requestMetadata(httpRequest, request.deviceIdentifier())
        );
        String message = response.mfaRequired()
                ? "Authenticator verification required."
                : "Login successful.";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<V3CurrentUserResponse>> me(
            @AuthenticationPrincipal V3AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Authenticated user retrieved successfully.",
                authService.getCurrentUser(authenticatedUser)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal V3AuthenticatedUser authenticatedUser,
            HttpServletRequest httpRequest
    ) {
        authService.logout(authenticatedUser, requestMetadata(httpRequest, null));
        return ResponseEntity.ok(ApiResponse.success("Logout successful.", null));
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
