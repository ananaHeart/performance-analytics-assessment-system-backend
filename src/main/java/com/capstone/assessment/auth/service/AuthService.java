package com.capstone.assessment.auth.service;

import com.capstone.assessment.auth.dto.CurrentUserResponse;
import com.capstone.assessment.auth.dto.LoginRequest;
import com.capstone.assessment.auth.dto.LoginResponse;
import com.capstone.assessment.auth.dto.TeacherAccountResponse;
import com.capstone.assessment.auth.dto.TeacherApprovalRequest;
import com.capstone.assessment.auth.dto.TeacherRegistrationRequest;

import java.util.List;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    CurrentUserResponse getCurrentUser(Long userId);

    TeacherAccountResponse registerTeacher(TeacherRegistrationRequest request);

    List<TeacherAccountResponse> getTeacherAccounts();

    TeacherAccountResponse updateTeacherApprovalStatus(Long userId, TeacherApprovalRequest request);
}
