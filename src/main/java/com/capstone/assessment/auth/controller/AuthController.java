package com.capstone.assessment.auth.controller;

import com.capstone.assessment.auth.dto.CurrentUserResponse;
import com.capstone.assessment.auth.dto.LoginRequest;
import com.capstone.assessment.auth.dto.LoginResponse;
import com.capstone.assessment.auth.dto.TeacherAccountResponse;
import com.capstone.assessment.auth.dto.TeacherApprovalRequest;
import com.capstone.assessment.auth.dto.TeacherRegistrationRequest;
import com.capstone.assessment.auth.service.AuthAccessValidator;
import com.capstone.assessment.auth.service.AuthService;
import com.capstone.assessment.common.response.ApiResponse;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthAccessValidator authAccessValidator;
    private final AuthService authService;

    public AuthController(AuthAccessValidator authAccessValidator, AuthService authService) {
        this.authAccessValidator = authAccessValidator;
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success("Login successful.", response);
    }

    @PostMapping("/register-teacher")
    public ApiResponse<TeacherAccountResponse> registerTeacher(@RequestBody TeacherRegistrationRequest request) {
        TeacherAccountResponse response = authService.registerTeacher(request);
        return ApiResponse.success("Teacher registration submitted successfully.", response);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser(@RequestParam Long userId) {
        CurrentUserResponse response = authService.getCurrentUser(userId);
        return ApiResponse.success("Current user retrieved successfully.", response);
    }

    @GetMapping("/teachers")
    public ApiResponse<List<TeacherAccountResponse>> getTeacherAccounts(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authAccessValidator.validatePrincipalAccess(authorizationHeader);
        List<TeacherAccountResponse> response = authService.getTeacherAccounts();
        return ApiResponse.success("Teacher accounts retrieved successfully.", response);
    }

    @PutMapping("/teachers/{userId}/status")
    public ApiResponse<TeacherAccountResponse> updateTeacherStatus(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long userId,
            @RequestBody TeacherApprovalRequest request
    ) {
        authAccessValidator.validatePrincipalAccess(authorizationHeader);
        TeacherAccountResponse response = authService.updateTeacherApprovalStatus(userId, request);
        return ApiResponse.success("Teacher account status updated successfully.", response);
    }
}
