package com.capstone.assessment.v2.auth.service;

import com.capstone.assessment.v2.auth.config.V2AuthProperties;
import com.capstone.assessment.v2.auth.dto.V2CurrentUserResponse;
import com.capstone.assessment.v2.auth.dto.V2LoginRequest;
import com.capstone.assessment.v2.auth.dto.V2LoginResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.model.V2LoginUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Profile("v2")
@Service
public class V2AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final V2AuthRepository authRepository;
    private final V2TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final V2AuthProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public V2AuthService(
            V2AuthRepository authRepository,
            V2TokenService tokenService,
            PasswordEncoder passwordEncoder,
            V2AuthProperties properties,
            ObjectMapper objectMapper
    ) {
        this(authRepository, tokenService, passwordEncoder, properties, objectMapper, Clock.systemUTC());
    }

    V2AuthService(
            V2AuthRepository authRepository,
            V2TokenService tokenService,
            PasswordEncoder passwordEncoder,
            V2AuthProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.authRepository = authRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = V2AuthException.class)
    public V2LoginResponse login(V2LoginRequest request, V2RequestMetadata metadata) {
        Instant now = clock.instant();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String deviceIdentifier = normalizeNullable(request.deviceIdentifier());
        V2RequestMetadata normalizedMetadata = new V2RequestMetadata(
                normalizeNullable(metadata.ipAddress()),
                normalizeNullable(metadata.userAgent()),
                deviceIdentifier
        );

        enforceRateLimit(email, normalizedMetadata, now);

        Optional<V2LoginUser> optionalUser = authRepository.findLoginUserByEmail(email);
        if (optionalUser.isEmpty()) {
            recordFailedAttempt(null, email, "invalid_credentials", normalizedMetadata, now);
            throw unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }

        V2LoginUser user = optionalUser.get();
        validateAccountEligibility(user, email, normalizedMetadata, now);

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            int nextFailureCount = user.failedLoginCount() + 1;
            Instant lockedUntil = nextFailureCount >= properties.maxFailedAttempts()
                    ? now.plus(properties.lockDuration())
                    : null;
            authRepository.recordFailedLogin(user.userId(), nextFailureCount, lockedUntil);
            recordFailedAttempt(user.userId(), email, "invalid_credentials", normalizedMetadata, now);
            throw unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }

        String rawToken = tokenService.generateToken();
        String sessionUuid = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(properties.sessionTtl());

        authRepository.recordSuccessfulLogin(user.userId(), now);
        authRepository.createSession(
                sessionUuid,
                user.userId(),
                tokenService.hashToken(rawToken),
                normalizedMetadata.deviceIdentifier(),
                normalizedMetadata.ipAddress(),
                normalizedMetadata.userAgent(),
                now,
                expiresAt
        );
        authRepository.recordLoginAttempt(
                user.userId(),
                email,
                normalizedMetadata.ipAddress(),
                normalizedMetadata.deviceIdentifier(),
                normalizedMetadata.userAgent(),
                true,
                null,
                now
        );
        recordAudit(user.userId(), "LOGIN", "users", user.userId().toString(), "success", normalizedMetadata,
                Map.of("sessionUuid", sessionUuid), now);

        return new V2LoginResponse(
                "Bearer",
                rawToken,
                expiresAt,
                toCurrentUser(user)
        );
    }

    public Optional<V2AuthenticatedUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        Optional<V2AuthenticatedUser> user = authRepository.findAuthenticatedUserByTokenHash(
                tokenService.hashToken(rawToken),
                now
        );
        user.ifPresent(value -> authRepository.touchSession(value.sessionUuid(), now));
        return user;
    }

    public V2CurrentUserResponse getCurrentUser(V2AuthenticatedUser authenticatedUser) {
        V2LoginUser user = authRepository.findLoginUserById(authenticatedUser.userId())
                .orElseThrow(() -> unauthorized("INVALID_SESSION", "The authenticated session is no longer valid."));
        return toCurrentUser(user);
    }

    @Transactional
    public void logout(V2AuthenticatedUser authenticatedUser, V2RequestMetadata metadata) {
        Instant now = clock.instant();
        authRepository.revokeSession(authenticatedUser.sessionUuid(), authenticatedUser.userId(), now);
        recordAudit(
                authenticatedUser.userId(),
                "LOGOUT",
                "auth_sessions",
                authenticatedUser.sessionUuid(),
                "success",
                metadata,
                Map.of(),
                now
        );
    }

    private void enforceRateLimit(String email, V2RequestMetadata metadata, Instant now) {
        int attempts = authRepository.countRecentFailedAttempts(
                email,
                metadata.ipAddress(),
                now.minus(properties.lockDuration())
        );
        if (attempts < properties.maxFailedAttempts() * 2) {
            return;
        }

        recordFailedAttempt(null, email, "rate_limited", metadata, now);
        throw new V2AuthException(
                "LOGIN_RATE_LIMITED",
                "Too many login attempts. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS
        );
    }

    private void validateAccountEligibility(
            V2LoginUser user,
            String email,
            V2RequestMetadata metadata,
            Instant now
    ) {
        if (user.lockedUntilAt() != null && user.lockedUntilAt().isAfter(now)) {
            recordFailedAttempt(user.userId(), email, "locked_account", metadata, now);
            throw new V2AuthException(
                    "ACCOUNT_LOCKED",
                    "The account is temporarily locked. Please try again later.",
                    HttpStatus.LOCKED
            );
        }

        String normalizedStatus = user.status().toLowerCase(Locale.ROOT);
        if (!"active".equals(normalizedStatus)) {
            String reason = switch (normalizedStatus) {
                case "pending" -> "pending_approval";
                case "rejected" -> "rejected_account";
                case "locked" -> "locked_account";
                default -> "inactive_account";
            };
            recordFailedAttempt(user.userId(), email, reason, metadata, now);
            throw new V2AuthException(
                    "ACCOUNT_NOT_ACTIVE",
                    "The account is not active.",
                    HttpStatus.FORBIDDEN
            );
        }

        if (user.emailVerifiedAt() == null) {
            recordFailedAttempt(user.userId(), email, "email_not_verified", metadata, now);
            throw new V2AuthException(
                    "EMAIL_NOT_VERIFIED",
                    "The email address must be verified before login.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void recordFailedAttempt(
            Long userId,
            String email,
            String reason,
            V2RequestMetadata metadata,
            Instant now
    ) {
        authRepository.recordLoginAttempt(
                userId,
                email,
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                false,
                reason,
                now
        );
        recordAudit(userId, "LOGIN", "users", userId == null ? null : userId.toString(), "failed", metadata,
                Map.of("reason", reason), now);
    }

    private void recordAudit(
            Long userId,
            String action,
            String entityType,
            String entityId,
            String outcome,
            V2RequestMetadata metadata,
            Map<String, Object> details,
            Instant now
    ) {
        authRepository.recordAudit(
                UUID.randomUUID().toString(),
                userId,
                action,
                entityType,
                entityId,
                outcome,
                normalizeNullable(metadata.ipAddress()),
                normalizeNullable(metadata.deviceIdentifier()),
                normalizeNullable(metadata.userAgent()),
                serializeDetails(details),
                now
        );
    }

    private String serializeDetails(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit details.", exception);
        }
    }

    private V2CurrentUserResponse toCurrentUser(V2LoginUser user) {
        return new V2CurrentUserResponse(
                user.userId(),
                user.schoolId(),
                user.firstName(),
                user.middleName(),
                user.lastName(),
                user.suffix(),
                user.email(),
                user.role(),
                user.status()
        );
    }

    private V2AuthException unauthorized(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.UNAUTHORIZED);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
