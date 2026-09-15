package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.dto.V3CurrentUserResponse;
import com.capstone.assessment.v3.auth.dto.V3LoginRequest;
import com.capstone.assessment.v3.auth.dto.V3LoginResponse;
import com.capstone.assessment.v3.auth.dto.V3MfaLoginChallengeResponse;
import com.capstone.assessment.v3.auth.dto.V3VerifyMfaLoginRequest;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.model.V3UserAccount;
import com.capstone.assessment.v3.auth.model.V3VerificationChallenge;
import com.capstone.assessment.v3.auth.model.V3VerifiedMfaLogin;
import com.capstone.assessment.v3.auth.repository.V3AuthRepository;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Profile("v3")
@Service
public class V3AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final V3AuthRepository authRepository;
    private final V3RegistrationRepository registrationRepository;
    private final V3RegistrationPersistenceService registrationPersistenceService;
    private final V3TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final V3AuthProperties properties;
    private final V3AuditService auditService;
    private final V3MfaService mfaService;
    private final Clock clock;

    @Autowired
    public V3AuthService(
            V3AuthRepository authRepository,
            V3RegistrationRepository registrationRepository,
            V3RegistrationPersistenceService registrationPersistenceService,
            V3TokenService tokenService,
            PasswordEncoder passwordEncoder,
            V3AuthProperties properties,
            V3AuditService auditService,
            V3MfaService mfaService
    ) {
        this(
                authRepository,
                registrationRepository,
                registrationPersistenceService,
                tokenService,
                passwordEncoder,
                properties,
                auditService,
                mfaService,
                Clock.systemUTC()
        );
    }

    V3AuthService(
            V3AuthRepository authRepository,
            V3RegistrationRepository registrationRepository,
            V3RegistrationPersistenceService registrationPersistenceService,
            V3TokenService tokenService,
            PasswordEncoder passwordEncoder,
            V3AuthProperties properties,
            V3AuditService auditService,
            V3MfaService mfaService,
            Clock clock
    ) {
        this.authRepository = authRepository;
        this.registrationRepository = registrationRepository;
        this.registrationPersistenceService = registrationPersistenceService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditService = auditService;
        this.mfaService = mfaService;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3LoginResponse login(V3LoginRequest request, V3RequestMetadata metadata) {
        Instant now = clock.instant();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        V3RequestMetadata normalizedMetadata = new V3RequestMetadata(
                normalizeNullable(metadata.ipAddress()),
                normalizeNullable(metadata.userAgent()),
                normalizeNullable(request.deviceIdentifier())
        );

        enforceRateLimit(email, normalizedMetadata, now);
        Optional<V3UserAccount> optionalUser = authRepository.findUserByEmail(email);
        if (optionalUser.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            recordFailedAttempt(null, email, "invalid_credentials", normalizedMetadata, now);
            throw unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }

        V3UserAccount user = optionalUser.get();
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            int nextFailureCount = user.failedLoginCount() + 1;
            Instant lockedUntil = nextFailureCount >= properties.getMaxFailedAttempts()
                    ? now.plus(properties.getLockDuration())
                    : null;
            authRepository.recordFailedLogin(user.userId(), nextFailureCount, lockedUntil);
            recordFailedAttempt(user.userId(), email, "invalid_credentials", normalizedMetadata, now);
            throw unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }

        if (isExpiredUnverifiedRegistration(user, now)) {
            registrationPersistenceService.purgeExpiredProvisionalUser(
                    user.userId(),
                    now.minus(properties.getRegistrationRetention()),
                    now
            );
            throw unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }

        if (user.lockedUntilAt() != null && user.lockedUntilAt().isAfter(now)) {
            recordFailedAttempt(user.userId(), email, "locked_account", normalizedMetadata, now);
            throw new V3AuthException(
                    "ACCOUNT_LOCKED",
                    "The account is temporarily locked. Please try again later.",
                    HttpStatus.LOCKED,
                    Map.of("lockedUntilAt", user.lockedUntilAt())
            );
        }

        switch (user.status().toLowerCase(Locale.ROOT)) {
            case V3RegistrationPersistenceService.PENDING_EMAIL_STATUS ->
                    throw pendingEmailVerification(user, email, normalizedMetadata, now);
            case V3RegistrationPersistenceService.PENDING_APPROVAL_STATUS -> {
                recordFailedAttempt(user.userId(), email, "pending_approval", normalizedMetadata, now);
                throw new V3AuthException(
                        "ACCOUNT_PENDING_APPROVAL",
                        "Email is verified, but the school principal has not approved the account yet.",
                        HttpStatus.FORBIDDEN
                );
            }
            case "rejected" -> {
                recordFailedAttempt(user.userId(), email, "rejected_account", normalizedMetadata, now);
                throw new V3AuthException(
                        "ACCOUNT_REJECTED",
                        "The teacher registration was rejected. Contact the school administrator.",
                        HttpStatus.FORBIDDEN
                );
            }
            case "inactive" -> {
                recordFailedAttempt(user.userId(), email, "inactive_account", normalizedMetadata, now);
                throw new V3AuthException(
                        "ACCOUNT_INACTIVE",
                        "The account is inactive. Contact the school administrator.",
                        HttpStatus.FORBIDDEN
                );
            }
            case "locked" -> {
                recordFailedAttempt(user.userId(), email, "locked_account", normalizedMetadata, now);
                throw new V3AuthException(
                        "ACCOUNT_LOCKED",
                        "The account is locked. Contact the school administrator.",
                        HttpStatus.LOCKED
                );
            }
            case "active" -> {
                // Continue below.
            }
            default -> {
                recordFailedAttempt(user.userId(), email, "other", normalizedMetadata, now);
                throw new V3AuthException(
                        "ACCOUNT_NOT_ACTIVE",
                        "The account is not active.",
                        HttpStatus.FORBIDDEN
                );
            }
        }

        if (user.emailVerifiedAt() == null) {
            recordFailedAttempt(user.userId(), email, "email_not_verified", normalizedMetadata, now);
            throw new V3AuthException(
                    "EMAIL_NOT_VERIFIED",
                    "The email address must be verified before login.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (user.mfaRequired()) {
            recordFailedAttempt(user.userId(), email, "mfa_required", normalizedMetadata, now);
            V3MfaLoginChallengeResponse challenge = mfaService.startLoginChallenge(
                    user,
                    normalizedMetadata,
                    now
            );
            return V3LoginResponse.mfaRequired(challenge);
        }

        return createSession(user, normalizedMetadata, now, null);
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3LoginResponse verifyMfaLogin(
            V3VerifyMfaLoginRequest request,
            V3RequestMetadata metadata
    ) {
        Instant now = clock.instant();
        V3RequestMetadata normalizedMetadata = new V3RequestMetadata(
                normalizeNullable(metadata.ipAddress()),
                normalizeNullable(metadata.userAgent()),
                normalizeNullable(request.deviceIdentifier())
        );
        V3VerifiedMfaLogin verifiedLogin = mfaService.verifyLoginChallenge(request, normalizedMetadata);
        V3UserAccount user = authRepository.findUserById(verifiedLogin.userId())
                .filter(value -> "active".equalsIgnoreCase(value.status()))
                .filter(value -> value.emailVerifiedAt() != null)
                .filter(V3UserAccount::mfaRequired)
                .orElseThrow(() -> unauthorized(
                        "MFA_CHALLENGE_INVALID",
                        "The authenticator challenge is invalid. Start login again."
                ));
        return createSession(user, normalizedMetadata, now, verifiedLogin);
    }

    private V3LoginResponse createSession(
            V3UserAccount user,
            V3RequestMetadata metadata,
            Instant now,
            V3VerifiedMfaLogin verifiedLogin
    ) {
        String rawToken = tokenService.generateToken();
        String sessionUuid = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(properties.getSessionTtl());
        authRepository.recordSuccessfulLogin(user.userId(), now);
        String tokenHash = tokenService.hashToken(rawToken);
        if (verifiedLogin == null) {
            authRepository.createSession(
                    sessionUuid,
                    user.userId(),
                    tokenHash,
                    metadata.deviceIdentifier(),
                    metadata.ipAddress(),
                    metadata.userAgent(),
                    now,
                    expiresAt
            );
        } else {
            authRepository.createMfaSession(
                    sessionUuid,
                    user.userId(),
                    verifiedLogin.factorId(),
                    tokenHash,
                    metadata.deviceIdentifier(),
                    metadata.ipAddress(),
                    metadata.userAgent(),
                    now,
                    expiresAt
            );
        }
        authRepository.recordLoginAttempt(
                user.userId(),
                user.email(),
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                true,
                null,
                now
        );
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("authenticationLevel", verifiedLogin == null ? "password" : "mfa");
        if (verifiedLogin != null) {
            auditDetails.put("challengeUuid", verifiedLogin.challengeUuid());
            auditDetails.put("verificationMethod", verifiedLogin.verificationMethod());
        }
        auditService.record(
                user.userId(),
                "LOGIN",
                "auth_sessions",
                sessionUuid,
                "success",
                metadata,
                auditDetails,
                now
        );
        return new V3LoginResponse("Bearer", rawToken, expiresAt, toCurrentUser(user));
    }

    public Optional<V3AuthenticatedUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<V3AuthenticatedUser> user = authRepository.findAuthenticatedUserByTokenHash(
                tokenService.hashToken(rawToken),
                now
        );
        user.ifPresent(value -> authRepository.touchSession(value.sessionUuid(), now));
        return user;
    }

    public V3CurrentUserResponse getCurrentUser(V3AuthenticatedUser authenticatedUser) {
        V3UserAccount user = authRepository.findUserById(authenticatedUser.userId())
                .orElseThrow(() -> unauthorized(
                        "INVALID_SESSION",
                        "The authenticated session is no longer valid."
                ));
        return toCurrentUser(user);
    }

    @Transactional
    public void logout(V3AuthenticatedUser authenticatedUser, V3RequestMetadata metadata) {
        Instant now = clock.instant();
        authRepository.revokeSession(authenticatedUser.sessionUuid(), authenticatedUser.userId(), now);
        auditService.record(
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

    private V3AuthException pendingEmailVerification(
            V3UserAccount user,
            String email,
            V3RequestMetadata metadata,
            Instant now
    ) {
        recordFailedAttempt(user.userId(), email, "pending_email_verification", metadata, now);
        Optional<V3VerificationChallenge> optionalChallenge = registrationRepository.findLatestChallenge(user.userId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("accountStatus", V3RegistrationPersistenceService.PENDING_EMAIL_STATUS);
        details.put("verificationMethod", "email");
        details.put("emailMasked", V3VerificationEmailSender.maskEmail(user.email()));
        details.put("registrationExpiresAt", user.createdAt().plus(properties.getRegistrationRetention()));
        if (optionalChallenge.isPresent()) {
            V3VerificationChallenge challenge = optionalChallenge.get();
            int messagesToday = registrationRepository.countVerificationMessagesSince(
                    user.userId(),
                    now.minusSeconds(24 * 60 * 60)
            );
            boolean reusableForResend = !"verified".equals(challenge.challengeStatus())
                    && !"cancelled".equals(challenge.challengeStatus());
            boolean cooldownPassed = challenge.nextResendAt() == null || !now.isBefore(challenge.nextResendAt());
            details.put("challengeUuid", challenge.challengeUuid());
            details.put("deliveryStatus", challenge.deliveryStatus());
            details.put("otpExpiresAt", challenge.expiresAt());
            details.put("resendAvailableAt", challenge.nextResendAt());
            details.put(
                    "canResend",
                    reusableForResend
                            && cooldownPassed
                            && messagesToday < properties.getMaxVerificationMessagesPerDay()
            );
        } else {
            details.put("canResend", false);
            details.put("resumeReason", "verification_challenge_missing");
        }
        return new V3AuthException(
                "EMAIL_VERIFICATION_REQUIRED",
                "Verify the registered email address before login.",
                HttpStatus.FORBIDDEN,
                details
        );
    }

    private boolean isExpiredUnverifiedRegistration(V3UserAccount user, Instant now) {
        return V3RegistrationPersistenceService.PENDING_EMAIL_STATUS.equals(user.status())
                && user.emailVerifiedAt() == null
                && !now.isBefore(user.createdAt().plus(properties.getRegistrationRetention()));
    }

    private void enforceRateLimit(String email, V3RequestMetadata metadata, Instant now) {
        int attempts = authRepository.countRecentCredentialFailures(
                email,
                metadata.ipAddress(),
                now.minus(properties.getLockDuration())
        );
        if (attempts < properties.getMaxFailedAttempts() * 2) {
            return;
        }
        recordFailedAttempt(null, email, "rate_limited", metadata, now);
        throw new V3AuthException(
                "LOGIN_RATE_LIMITED",
                "Too many login attempts. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS
        );
    }

    private void recordFailedAttempt(
            Long userId,
            String email,
            String reason,
            V3RequestMetadata metadata,
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
        auditService.record(
                userId,
                "LOGIN",
                "users",
                userId == null ? null : userId.toString(),
                "failed",
                metadata,
                Map.of("reason", reason),
                now
        );
    }

    private V3CurrentUserResponse toCurrentUser(V3UserAccount user) {
        return new V3CurrentUserResponse(
                user.userId(),
                user.schoolId(),
                user.firstName(),
                user.middleName(),
                user.lastName(),
                user.suffix(),
                user.email(),
                user.role(),
                user.status(),
                user.mfaRequired()
        );
    }

    private V3AuthException unauthorized(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.UNAUTHORIZED);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
