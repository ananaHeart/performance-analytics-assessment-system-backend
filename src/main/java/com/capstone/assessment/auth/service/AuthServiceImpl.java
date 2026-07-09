package com.capstone.assessment.auth.service;

import com.capstone.assessment.auth.dto.CurrentUserResponse;
import com.capstone.assessment.auth.dto.LoginRequest;
import com.capstone.assessment.auth.dto.LoginResponse;
import com.capstone.assessment.auth.dto.TeacherAccountResponse;
import com.capstone.assessment.auth.dto.TeacherApprovalRequest;
import com.capstone.assessment.auth.dto.TeacherRegistrationRequest;
import com.capstone.assessment.auth.repository.AuthRepository;
import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_LOGIN_MESSAGE = "Invalid email or password.";
    private static final String INACTIVE_USER_MESSAGE = "User account is not active.";
    private static final String TEACHER_NOT_FOUND_MESSAGE = "Teacher account not found.";

    private final AuthRepository authRepository;

    public AuthServiceImpl(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        validateLoginRequest(request);

        String normalizedEmail = request.email().trim();
        CurrentUserResponse user = authRepository.findUserByEmail(normalizedEmail)
                .orElseThrow(() -> new BadRequestException(INVALID_LOGIN_MESSAGE));

        String storedPassword = authRepository.findPasswordByEmail(normalizedEmail)
                .orElseThrow(() -> new BadRequestException(INVALID_LOGIN_MESSAGE));

        if (!storedPassword.equals(request.password())) {
            throw new BadRequestException(INVALID_LOGIN_MESSAGE);
        }

        validateUserEligibility(user);

        return new LoginResponse(
                user.userId(),
                user.firstName(),
                user.lastName(),
                user.email(),
                user.role(),
                user.status(),
                buildLocalToken(user.userId(), user.role())
        );
    }

    @Override
    public CurrentUserResponse getCurrentUser(Long userId) {
        if (userId == null) {
            throw new BadRequestException("User ID is required.");
        }

        return authRepository.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Override
    public TeacherAccountResponse registerTeacher(TeacherRegistrationRequest request) {
        validateTeacherRegistrationRequest(request);

        String normalizedEmail = request.email().trim();
        if (authRepository.emailExists(normalizedEmail)) {
            throw new BadRequestException("Email already exists.");
        }

        TeacherRegistrationRequest normalizedRequest = new TeacherRegistrationRequest(
                request.firstName().trim(),
                request.lastName().trim(),
                request.gender().trim().toLowerCase(Locale.ROOT),
                request.dateBirth().trim(),
                normalizedEmail,
                request.password()
        );

        Long userId = authRepository.createPendingTeacher(normalizedRequest);
        return authRepository.findTeacherById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(TEACHER_NOT_FOUND_MESSAGE));
    }

    @Override
    public List<TeacherAccountResponse> getTeacherAccounts() {
        return authRepository.findTeachers();
    }

    @Override
    public TeacherAccountResponse updateTeacherApprovalStatus(Long userId, TeacherApprovalRequest request) {
        if (userId == null) {
            throw new BadRequestException("User ID is required.");
        }

        validateTeacherApprovalRequest(request);

        TeacherAccountResponse teacher = authRepository.findTeacherById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(TEACHER_NOT_FOUND_MESSAGE));

        String normalizedStatus = request.status().trim().toLowerCase(Locale.ROOT);
        authRepository.updateTeacherStatus(teacher.userId(), normalizedStatus);

        return authRepository.findTeacherById(teacher.userId())
                .orElseThrow(() -> new ResourceNotFoundException(TEACHER_NOT_FOUND_MESSAGE));
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new BadRequestException("Login request must not be null.");
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Email is required.");
        }

        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("Password is required.");
        }
    }

    private void validateTeacherRegistrationRequest(TeacherRegistrationRequest request) {
        if (request == null) {
            throw new BadRequestException("Teacher registration request must not be null.");
        }

        if (request.firstName() == null || request.firstName().isBlank()) {
            throw new BadRequestException("First name is required.");
        }

        if (request.lastName() == null || request.lastName().isBlank()) {
            throw new BadRequestException("Last name is required.");
        }

        if (request.gender() == null || request.gender().isBlank()) {
            throw new BadRequestException("Gender is required.");
        }

        String normalizedGender = request.gender().trim().toLowerCase(Locale.ROOT);
        if (!"male".equals(normalizedGender) && !"female".equals(normalizedGender)) {
            throw new BadRequestException("Gender must be either male or female.");
        }

        if (request.dateBirth() == null || request.dateBirth().isBlank()) {
            throw new BadRequestException("Date of birth is required.");
        }

        try {
            LocalDate.parse(request.dateBirth().trim());
        } catch (Exception exception) {
            throw new BadRequestException("Date of birth must be in ISO format yyyy-MM-dd.");
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Email is required.");
        }

        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("Password is required.");
        }
    }

    private void validateTeacherApprovalRequest(TeacherApprovalRequest request) {
        if (request == null) {
            throw new BadRequestException("Teacher approval request must not be null.");
        }

        if (request.status() == null || request.status().isBlank()) {
            throw new BadRequestException("Status is required.");
        }

        String normalizedStatus = request.status().trim().toLowerCase(Locale.ROOT);
        if (!"active".equals(normalizedStatus) && !"rejected".equals(normalizedStatus)) {
            throw new BadRequestException("Status must be either active or rejected.");
        }
    }

    private void validateUserEligibility(CurrentUserResponse user) {
        if (!"active".equalsIgnoreCase(user.status())) {
            throw new BadRequestException(INACTIVE_USER_MESSAGE);
        }

        String normalizedRole = user.role() == null ? "" : user.role().toLowerCase(Locale.ROOT);
        if (!"principal".equals(normalizedRole) && !"teacher".equals(normalizedRole)) {
            throw new BadRequestException(INVALID_LOGIN_MESSAGE);
        }
    }

    private String buildLocalToken(Long userId, String role) {
        return "LOCAL-TOKEN-" + userId + "-" + role;
    }
}
