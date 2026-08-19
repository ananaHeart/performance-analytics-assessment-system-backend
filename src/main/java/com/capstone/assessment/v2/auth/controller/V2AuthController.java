package com.capstone.assessment.v2.auth.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherAccountResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherRegistrationReferenceDataResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherRegistrationRequest;
import com.capstone.assessment.v2.account.service.V2TeacherAccountService;
import com.capstone.assessment.v2.auth.dto.V2CurrentUserResponse;
import com.capstone.assessment.v2.auth.dto.V2LoginRequest;
import com.capstone.assessment.v2.auth.dto.V2LoginResponse;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.service.V2AuthService;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
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

@Profile("v2")
@RestController
@RequestMapping("/api/v2/auth")
public class V2AuthController {

    private static final String DEVICE_IDENTIFIER_HEADER = "X-Device-Identifier";

    private final V2AuthService authService;
    private final V2TeacherAccountService accountService;

    public V2AuthController(V2AuthService authService, V2TeacherAccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<V2LoginResponse>> login(
            @Valid @RequestBody V2LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        V2LoginResponse response = authService.login(
                request,
                requestMetadata(httpRequest, request.deviceIdentifier())
        );
        return ResponseEntity.ok(ApiResponse.success("Login successful.", response));
    }

    @GetMapping("/teacher-registration/reference-data")
    public ResponseEntity<ApiResponse<V2TeacherRegistrationReferenceDataResponse>> teacherRegistrationReferenceData() {
        return ResponseEntity.ok(ApiResponse.success(
                "Teacher registration reference data retrieved successfully.",
                accountService.getPublicRegistrationReferenceData()
        ));
    }

    @PostMapping("/register-teacher")
    public ResponseEntity<ApiResponse<V2TeacherAccountResponse>> registerTeacher(
            @Valid @RequestBody V2TeacherRegistrationRequest request,
            HttpServletRequest httpRequest
    ) {
        V2TeacherAccountResponse response = accountService.registerTeacher(
                request,
                requestMetadata(httpRequest, httpRequest.getHeader(DEVICE_IDENTIFIER_HEADER))
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Teacher registration submitted for principal approval.", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<V2CurrentUserResponse>> me(
            @AuthenticationPrincipal V2AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Authenticated user retrieved successfully.",
                authService.getCurrentUser(authenticatedUser)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal V2AuthenticatedUser authenticatedUser,
            HttpServletRequest httpRequest
    ) {
        authService.logout(
                authenticatedUser,
                requestMetadata(httpRequest, httpRequest.getHeader(DEVICE_IDENTIFIER_HEADER))
        );
        return ResponseEntity.ok(ApiResponse.success("Logout successful.", null));
    }

    private V2RequestMetadata requestMetadata(HttpServletRequest request, String deviceIdentifier) {
        return new V2RequestMetadata(
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
