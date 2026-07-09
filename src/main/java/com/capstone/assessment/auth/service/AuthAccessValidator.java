package com.capstone.assessment.auth.service;

import com.capstone.assessment.auth.dto.CurrentUserResponse;
import com.capstone.assessment.auth.repository.AuthRepository;
import com.capstone.assessment.common.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AuthAccessValidator {

    private static final String TOKEN_REQUIRED_MESSAGE = "Authorization token is required.";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid authorization token.";
    private static final String PRINCIPAL_ACCESS_REQUIRED_MESSAGE = "Principal access is required.";
    private static final Pattern LOCAL_TOKEN_PATTERN = Pattern.compile("^Bearer LOCAL-TOKEN-(\\d+)-([A-Za-z]+)$");

    private final AuthRepository authRepository;

    public AuthAccessValidator(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    // Development-only local token guard. This will be replaced by JWT/Spring Security before deployment.
    public void validatePrincipalAccess(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new BadRequestException(TOKEN_REQUIRED_MESSAGE);
        }

        Matcher matcher = LOCAL_TOKEN_PATTERN.matcher(authorizationHeader.trim());
        if (!matcher.matches()) {
            throw new BadRequestException(INVALID_TOKEN_MESSAGE);
        }

        Long userId;
        try {
            userId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new BadRequestException(INVALID_TOKEN_MESSAGE);
        }

        String role = matcher.group(2).toLowerCase(Locale.ROOT);
        if (!"principal".equals(role)) {
            throw new BadRequestException(PRINCIPAL_ACCESS_REQUIRED_MESSAGE);
        }

        CurrentUserResponse user = authRepository.findUserById(userId)
                .orElseThrow(() -> new BadRequestException(PRINCIPAL_ACCESS_REQUIRED_MESSAGE));

        if (!"active".equalsIgnoreCase(user.status())
                || !"principal".equalsIgnoreCase(user.role())) {
            throw new BadRequestException(PRINCIPAL_ACCESS_REQUIRED_MESSAGE);
        }
    }
}
