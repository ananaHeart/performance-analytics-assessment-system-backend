package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.config.V3MfaProperties;
import com.capstone.assessment.v3.auth.dto.V3ConfirmMfaEnrollmentRequest;
import com.capstone.assessment.v3.auth.dto.V3MfaEnrollmentRequest;
import com.capstone.assessment.v3.auth.dto.V3MfaEnrollmentResponse;
import com.capstone.assessment.v3.auth.dto.V3MfaLoginChallengeResponse;
import com.capstone.assessment.v3.auth.dto.V3MfaRecoveryCodesResponse;
import com.capstone.assessment.v3.auth.dto.V3MfaSensitiveActionRequest;
import com.capstone.assessment.v3.auth.dto.V3MfaStatusResponse;
import com.capstone.assessment.v3.auth.dto.V3VerifyMfaLoginRequest;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3MfaAttemptException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.model.V3MfaAuthenticationChallenge;
import com.capstone.assessment.v3.auth.model.V3MfaFactor;
import com.capstone.assessment.v3.auth.model.V3UserAccount;
import com.capstone.assessment.v3.auth.model.V3VerifiedMfaLogin;
import com.capstone.assessment.v3.auth.repository.V3AuthRepository;
import com.capstone.assessment.v3.auth.repository.V3MfaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

@Profile("v3")
@Service
public class V3MfaService {

    private static final String AUTHENTICATOR = "authenticator";
    private static final String RECOVERY_CODE = "recovery_code";
    private static final String DEFAULT_FACTOR_NAME = "Authenticator app";
    private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final V3MfaRepository mfaRepository;
    private final V3AuthRepository authRepository;
    private final V3TotpService totpService;
    private final V3MfaSecretCipher secretCipher;
    private final V3MfaQrCodeService qrCodeService;
    private final V3TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final V3AuthProperties authProperties;
    private final V3MfaProperties mfaProperties;
    private final V3AuditService auditService;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public V3MfaService(
            V3MfaRepository mfaRepository,
            V3AuthRepository authRepository,
            V3TotpService totpService,
            V3MfaSecretCipher secretCipher,
            V3MfaQrCodeService qrCodeService,
            V3TokenService tokenService,
            PasswordEncoder passwordEncoder,
            V3AuthProperties authProperties,
            V3MfaProperties mfaProperties,
            V3AuditService auditService
    ) {
        this(
                mfaRepository,
                authRepository,
                totpService,
                secretCipher,
                qrCodeService,
                tokenService,
                passwordEncoder,
                authProperties,
                mfaProperties,
                auditService,
                Clock.systemUTC(),
                new SecureRandom()
        );
    }

    V3MfaService(
            V3MfaRepository mfaRepository,
            V3AuthRepository authRepository,
            V3TotpService totpService,
            V3MfaSecretCipher secretCipher,
            V3MfaQrCodeService qrCodeService,
            V3TokenService tokenService,
            PasswordEncoder passwordEncoder,
            V3AuthProperties authProperties,
            V3MfaProperties mfaProperties,
            V3AuditService auditService,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.mfaRepository = mfaRepository;
        this.authRepository = authRepository;
        this.totpService = totpService;
        this.secretCipher = secretCipher;
        this.qrCodeService = qrCodeService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
        this.mfaProperties = mfaProperties;
        this.auditService = auditService;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Transactional(readOnly = true)
    public V3MfaStatusResponse status(V3AuthenticatedUser authenticatedUser) {
        var factor = mfaRepository.findActiveFactorByUserId(authenticatedUser.userId());
        if (factor.isEmpty()) {
            return new V3MfaStatusResponse(
                    isAvailable(),
                    false,
                    null,
                    null,
                    null,
                    null,
                    0
            );
        }
        V3MfaFactor activeFactor = factor.get();
        return new V3MfaStatusResponse(
                isAvailable(),
                true,
                activeFactor.factorUuid(),
                activeFactor.factorName(),
                activeFactor.verifiedAt(),
                activeFactor.lastUsedAt(),
                mfaRepository.countUnusedRecoveryCodes(activeFactor.factorId())
        );
    }

    @Transactional(noRollbackFor = V3MfaAttemptException.class)
    public V3MfaEnrollmentResponse beginEnrollment(
            V3AuthenticatedUser authenticatedUser,
            V3MfaEnrollmentRequest request,
            V3RequestMetadata metadata
    ) {
        requireAvailable();
        Instant now = clock.instant();
        V3UserAccount user = requireActiveUser(authenticatedUser.userId());
        verifyPassword(user, request.password(), metadata, now);
        if (user.mfaRequired() || mfaRepository.findActiveFactorByUserId(user.userId()).isPresent()) {
            throw new V3AuthException(
                    "MFA_ALREADY_ENABLED",
                    "Authenticator security is already enabled for this account.",
                    HttpStatus.CONFLICT
            );
        }

        mfaRepository.revokePendingFactors(user.userId(), now);
        String secret = totpService.generateSecret();
        String factorUuid = UUID.randomUUID().toString();
        String factorName = normalizeFactorName(request.factorName());
        mfaRepository.createPendingFactor(
                factorUuid,
                user.userId(),
                factorName,
                secretCipher.encrypt(secret),
                mfaProperties.getSecretKeyVersion(),
                now
        );
        String otpauthUri = createOtpAuthUri(user.email(), secret);
        auditService.record(
                user.userId(),
                "MFA_ENROLLMENT_STARTED",
                "user_mfa_factors",
                factorUuid,
                "success",
                metadata,
                Map.of("factorType", "totp"),
                now
        );
        return new V3MfaEnrollmentResponse(
                factorUuid,
                factorName,
                "pending",
                secret,
                otpauthUri,
                qrCodeService.createDataUrl(otpauthUri, mfaProperties.getQrCodeSize()),
                now
        );
    }

    @Transactional(noRollbackFor = V3MfaAttemptException.class)
    public V3MfaRecoveryCodesResponse confirmEnrollment(
            V3AuthenticatedUser authenticatedUser,
            V3ConfirmMfaEnrollmentRequest request,
            V3RequestMetadata metadata
    ) {
        requireAvailable();
        Instant now = clock.instant();
        V3UserAccount user = requireActiveUser(authenticatedUser.userId());
        enforceMfaRateLimit(user, metadata, now);
        V3MfaFactor factor = mfaRepository.findFactorByUuidAndUserId(
                        request.factorUuid(),
                        user.userId()
                )
                .filter(value -> "pending".equals(value.status()))
                .orElseThrow(() -> new V3AuthException(
                        "MFA_ENROLLMENT_NOT_FOUND",
                        "The pending authenticator enrollment was not found.",
                        HttpStatus.NOT_FOUND
                ));
        requireCurrentKeyVersion(factor);
        OptionalLong matchingCounter = matchingCounter(factor, request.code(), now);
        if (matchingCounter.isEmpty()) {
            recordInvalidMfaCode(user, factor.factorUuid(), "enrollment", metadata, now, false);
        }
        if (mfaRepository.findActiveFactorByUserId(user.userId()).isPresent()) {
            throw new V3AuthException(
                    "MFA_ALREADY_ENABLED",
                    "Authenticator security is already enabled for this account.",
                    HttpStatus.CONFLICT
            );
        }
        if (mfaRepository.activateFactor(factor.factorId(), user.userId(), now) != 1) {
            throw new V3AuthException(
                    "MFA_ENROLLMENT_CONFLICT",
                    "The authenticator enrollment changed. Start setup again.",
                    HttpStatus.CONFLICT
            );
        }
        mfaRepository.setUserMfaRequired(user.userId(), true);
        Instant acceptedCounterStart = counterStart(matchingCounter.getAsLong(), factor.periodSeconds());
        if (mfaRepository.markFactorUsed(factor.factorId(), acceptedCounterStart) != 1) {
            throw new V3AuthException(
                    "MFA_CODE_REPLAYED",
                    "This authenticator code was already used. Wait for a new code.",
                    HttpStatus.CONFLICT
            );
        }
        if (authRepository.upgradeSessionToMfa(
                authenticatedUser.sessionUuid(),
                user.userId(),
                factor.factorId(),
                now
        ) != 1) {
            throw new IllegalStateException(
                    "The authenticated session could not be upgraded after MFA enrollment."
            );
        }
        List<String> recoveryCodes = issueRecoveryCodes(factor.factorId(), now);
        auditService.record(
                user.userId(),
                "MFA_ENABLED",
                "user_mfa_factors",
                factor.factorUuid(),
                "success",
                metadata,
                Map.of("recoveryCodeCount", recoveryCodes.size()),
                now
        );
        return new V3MfaRecoveryCodesResponse(true, factor.factorUuid(), recoveryCodes);
    }

    @Transactional
    public V3MfaLoginChallengeResponse startLoginChallenge(
            V3UserAccount user,
            V3RequestMetadata metadata,
            Instant now
    ) {
        requireAvailable();
        V3MfaFactor factor = mfaRepository.findActiveFactorByUserId(user.userId())
                .orElseThrow(() -> new V3AuthException(
                        "MFA_NOT_ENROLLED",
                        "The account requires an authenticator, but no active factor is available.",
                        HttpStatus.SERVICE_UNAVAILABLE
                ));
        requireCurrentKeyVersion(factor);
        mfaRepository.cancelPendingChallenges(user.userId(), now);
        String challengeUuid = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(mfaProperties.getChallengeTtl());
        mfaRepository.createAuthenticationChallenge(
                challengeUuid,
                user.userId(),
                factor.factorId(),
                mfaProperties.getMaxAttempts(),
                expiresAt,
                now
        );
        auditService.record(
                user.userId(),
                "MFA_LOGIN_CHALLENGE_CREATED",
                "mfa_authentication_challenges",
                challengeUuid,
                "success",
                metadata,
                Map.of("expiresAt", expiresAt),
                now
        );
        return new V3MfaLoginChallengeResponse(
                challengeUuid,
                expiresAt,
                mfaProperties.getMaxAttempts(),
                List.of(AUTHENTICATOR, RECOVERY_CODE),
                maskEmail(user.email())
        );
    }

    @Transactional(noRollbackFor = V3MfaAttemptException.class)
    public V3VerifiedMfaLogin verifyLoginChallenge(
            V3VerifyMfaLoginRequest request,
            V3RequestMetadata metadata
    ) {
        requireAvailable();
        Instant now = clock.instant();
        V3MfaAuthenticationChallenge challenge = mfaRepository.findChallengeByUuidForUpdate(
                        request.challengeUuid()
                )
                .orElseThrow(this::invalidChallenge);
        V3UserAccount user = requireActiveUser(challenge.userId());
        enforceMfaRateLimit(user, metadata, now);
        validatePendingChallenge(challenge, now);
        V3MfaFactor factor = mfaRepository.findFactorById(challenge.factorId())
                .filter(value -> value.userId() == user.userId() && "active".equals(value.status()))
                .orElseThrow(this::invalidChallenge);
        requireCurrentKeyVersion(factor);

        String method = normalizeVerificationMethod(request.verificationMethod());
        boolean verified = verifyAndConsumeCode(factor, request.code(), method, now);
        if (!verified) {
            int nextAttemptCount = challenge.attemptCount() + 1;
            boolean locked = nextAttemptCount >= challenge.maximumAttemptCount();
            mfaRepository.recordFailedChallengeAttempt(
                    challenge.challengeId(),
                    challenge.attemptCount(),
                    nextAttemptCount,
                    locked,
                    now
            );
            authRepository.recordLoginAttempt(
                    user.userId(),
                    user.email(),
                    metadata.ipAddress(),
                    metadata.deviceIdentifier(),
                    metadata.userAgent(),
                    false,
                    locked ? "mfa_locked" : "invalid_mfa_code",
                    now
            );
            auditService.record(
                    user.userId(),
                    "MFA_LOGIN_CHALLENGE_FAILED",
                    "mfa_authentication_challenges",
                    challenge.challengeUuid(),
                    "failure",
                    metadata,
                    Map.of("attemptCount", nextAttemptCount, "locked", locked),
                    now
            );
            if (locked) {
                throw new V3MfaAttemptException(
                        "MFA_CHALLENGE_LOCKED",
                        "Too many invalid authenticator attempts. Start login again later.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
            throw new V3MfaAttemptException(
                    "MFA_CODE_INVALID",
                    "The authenticator or recovery code is invalid.",
                    HttpStatus.UNAUTHORIZED,
                    Map.of("attemptsRemaining", challenge.maximumAttemptCount() - nextAttemptCount)
            );
        }

        if (mfaRepository.verifyChallenge(challenge.challengeId(), now) != 1) {
            throw invalidChallenge();
        }
        auditService.record(
                user.userId(),
                "MFA_LOGIN_CHALLENGE_VERIFIED",
                "mfa_authentication_challenges",
                challenge.challengeUuid(),
                "success",
                metadata,
                Map.of("verificationMethod", method),
                now
        );
        return new V3VerifiedMfaLogin(
                user.userId(),
                factor.factorId(),
                challenge.challengeUuid(),
                method
        );
    }

    @Transactional(noRollbackFor = V3MfaAttemptException.class)
    public V3MfaRecoveryCodesResponse regenerateRecoveryCodes(
            V3AuthenticatedUser authenticatedUser,
            V3MfaSensitiveActionRequest request,
            V3RequestMetadata metadata
    ) {
        requireAvailable();
        Instant now = clock.instant();
        V3UserAccount user = requireActiveUser(authenticatedUser.userId());
        verifyPassword(user, request.password(), metadata, now);
        enforceMfaRateLimit(user, metadata, now);
        V3MfaFactor factor = requireActiveFactor(user.userId());
        if (!verifyAndConsumeCode(factor, request.code(), normalizeVerificationMethod(request.verificationMethod()), now)) {
            recordInvalidMfaCode(user, factor.factorUuid(), "recovery_regeneration", metadata, now, false);
        }
        List<String> recoveryCodes = issueRecoveryCodes(factor.factorId(), now);
        auditService.record(
                user.userId(),
                "MFA_RECOVERY_CODES_REGENERATED",
                "user_mfa_factors",
                factor.factorUuid(),
                "success",
                metadata,
                Map.of("recoveryCodeCount", recoveryCodes.size()),
                now
        );
        return new V3MfaRecoveryCodesResponse(true, factor.factorUuid(), recoveryCodes);
    }

    @Transactional(noRollbackFor = V3MfaAttemptException.class)
    public void disable(
            V3AuthenticatedUser authenticatedUser,
            V3MfaSensitiveActionRequest request,
            V3RequestMetadata metadata
    ) {
        requireAvailable();
        Instant now = clock.instant();
        V3UserAccount user = requireActiveUser(authenticatedUser.userId());
        verifyPassword(user, request.password(), metadata, now);
        enforceMfaRateLimit(user, metadata, now);
        V3MfaFactor factor = requireActiveFactor(user.userId());
        if (!verifyAndConsumeCode(factor, request.code(), normalizeVerificationMethod(request.verificationMethod()), now)) {
            recordInvalidMfaCode(user, factor.factorUuid(), "disable", metadata, now, false);
        }
        if (mfaRepository.disableFactor(factor.factorId(), user.userId(), now) != 1) {
            throw new V3AuthException(
                    "MFA_STATE_CONFLICT",
                    "Authenticator security changed. Refresh the security settings.",
                    HttpStatus.CONFLICT
            );
        }
        mfaRepository.setUserMfaRequired(user.userId(), false);
        mfaRepository.deleteRecoveryCodes(factor.factorId());
        mfaRepository.cancelPendingChallenges(user.userId(), now);
        authRepository.revokeOtherSessions(user.userId(), authenticatedUser.sessionUuid(), now);
        auditService.record(
                user.userId(),
                "MFA_DISABLED",
                "user_mfa_factors",
                factor.factorUuid(),
                "success",
                metadata,
                Map.of("otherSessionsRevoked", true),
                now
        );
    }

    private boolean verifyAndConsumeCode(
            V3MfaFactor factor,
            String submittedCode,
            String method,
            Instant now
    ) {
        if (RECOVERY_CODE.equals(method)) {
            return mfaRepository.consumeRecoveryCode(
                    factor.factorId(),
                    tokenService.hashToken(normalizeRecoveryCode(submittedCode)),
                    now
            ) == 1;
        }
        OptionalLong matchingCounter = matchingCounter(factor, submittedCode, now);
        return matchingCounter.isPresent()
                && mfaRepository.markFactorUsed(
                        factor.factorId(),
                        counterStart(matchingCounter.getAsLong(), factor.periodSeconds())
                ) == 1;
    }

    private OptionalLong matchingCounter(V3MfaFactor factor, String submittedCode, Instant now) {
        return totpService.findMatchingCounter(
                secretCipher.decrypt(factor.secretCiphertext()),
                submittedCode,
                now,
                factor.algorithm(),
                factor.digits(),
                factor.periodSeconds(),
                mfaProperties.getVerificationWindow()
        );
    }

    private void validatePendingChallenge(V3MfaAuthenticationChallenge challenge, Instant now) {
        if ("locked".equals(challenge.status()) || challenge.attemptCount() >= challenge.maximumAttemptCount()) {
            throw new V3AuthException(
                    "MFA_CHALLENGE_LOCKED",
                    "Too many invalid authenticator attempts. Start login again later.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        if (!"pending".equals(challenge.status())) {
            throw invalidChallenge();
        }
        if (!now.isBefore(challenge.expiresAt())) {
            mfaRepository.expireChallenge(challenge.challengeId(), now);
            throw new V3MfaAttemptException(
                    "MFA_CHALLENGE_EXPIRED",
                    "The authenticator challenge expired. Start login again.",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    private void verifyPassword(
            V3UserAccount user,
            String password,
            V3RequestMetadata metadata,
            Instant now
    ) {
        int failures = authRepository.countRecentCredentialFailures(
                user.email(),
                metadata.ipAddress(),
                now.minus(authProperties.getLockDuration())
        );
        if (failures >= authProperties.getMaxFailedAttempts() * 2) {
            throw new V3AuthException(
                    "SENSITIVE_ACTION_RATE_LIMITED",
                    "Too many security verification attempts. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        if (passwordEncoder.matches(password, user.passwordHash())) {
            return;
        }
        authRepository.recordLoginAttempt(
                user.userId(),
                user.email(),
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                false,
                "invalid_credentials",
                now
        );
        throw new V3MfaAttemptException(
                "PASSWORD_CONFIRMATION_FAILED",
                "The current password is incorrect.",
                HttpStatus.BAD_REQUEST
        );
    }

    private void enforceMfaRateLimit(V3UserAccount user, V3RequestMetadata metadata, Instant now) {
        if (authRepository.countRecentMfaFailures(
                user.userId(),
                now.minus(authProperties.getLockDuration())
        ) < mfaProperties.getMaxAttempts()) {
            return;
        }
        authRepository.recordLoginAttempt(
                user.userId(),
                user.email(),
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                false,
                "mfa_locked",
                now
        );
        throw new V3MfaAttemptException(
                "MFA_RATE_LIMITED",
                "Too many invalid authenticator attempts. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS
        );
    }

    private void recordInvalidMfaCode(
            V3UserAccount user,
            String factorUuid,
            String operation,
            V3RequestMetadata metadata,
            Instant now,
            boolean login
    ) {
        authRepository.recordLoginAttempt(
                user.userId(),
                user.email(),
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                false,
                "invalid_mfa_code",
                now
        );
        auditService.record(
                user.userId(),
                "MFA_CODE_REJECTED",
                "user_mfa_factors",
                factorUuid,
                "failure",
                metadata,
                Map.of("operation", operation),
                now
        );
        throw new V3MfaAttemptException(
                "MFA_CODE_INVALID",
                "The authenticator code is invalid.",
                login ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST
        );
    }

    private List<String> issueRecoveryCodes(long factorId, Instant now) {
        int requiredCount = Math.max(1, mfaProperties.getRecoveryCodeCount());
        Set<String> uniqueCodes = new LinkedHashSet<>();
        while (uniqueCodes.size() < requiredCount) {
            uniqueCodes.add(generateRecoveryCode());
        }
        List<String> codes = new ArrayList<>(uniqueCodes);
        List<String> hashes = codes.stream()
                .map(this::normalizeRecoveryCode)
                .map(tokenService::hashToken)
                .toList();
        mfaRepository.replaceRecoveryCodes(
                factorId,
                UUID.randomUUID().toString(),
                hashes,
                now
        );
        return List.copyOf(codes);
    }

    private String generateRecoveryCode() {
        StringBuilder result = new StringBuilder(14);
        for (int index = 0; index < 12; index++) {
            if (index > 0 && index % 4 == 0) {
                result.append('-');
            }
            result.append(RECOVERY_ALPHABET[secureRandom.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return result.toString();
    }

    private V3UserAccount requireActiveUser(long userId) {
        return authRepository.findUserById(userId)
                .filter(user -> "active".equalsIgnoreCase(user.status()))
                .filter(user -> user.emailVerifiedAt() != null)
                .orElseThrow(() -> new V3AuthException(
                        "ACCOUNT_NOT_ACTIVE",
                        "An active, verified account is required.",
                        HttpStatus.FORBIDDEN
                ));
    }

    private V3MfaFactor requireActiveFactor(long userId) {
        V3MfaFactor factor = mfaRepository.findActiveFactorByUserId(userId)
                .orElseThrow(() -> new V3AuthException(
                        "MFA_NOT_ENABLED",
                        "Authenticator security is not enabled for this account.",
                        HttpStatus.CONFLICT
                ));
        requireCurrentKeyVersion(factor);
        return factor;
    }

    private void requireCurrentKeyVersion(V3MfaFactor factor) {
        if (factor.secretKeyVersion() != mfaProperties.getSecretKeyVersion()) {
            throw new V3AuthException(
                    "MFA_KEY_VERSION_UNAVAILABLE",
                    "Authenticator security is temporarily unavailable.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new V3AuthException(
                    "MFA_CONFIGURATION_ERROR",
                    "Authenticator security is temporarily unavailable.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private boolean isAvailable() {
        return mfaProperties.isEnabled() && secretCipher.isConfigured();
    }

    private String createOtpAuthUri(String email, String secret) {
        String issuer = normalizeIssuer(mfaProperties.getIssuer());
        String label = urlEncode(issuer + ":" + email);
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(secret)
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private String normalizeIssuer(String issuer) {
        return issuer == null || issuer.isBlank() ? "SMART Assessment" : issuer.trim();
    }

    private String normalizeFactorName(String factorName) {
        return factorName == null || factorName.isBlank() ? DEFAULT_FACTOR_NAME : factorName.trim();
    }

    private String normalizeVerificationMethod(String method) {
        return method == null || method.isBlank() ? AUTHENTICATOR : method.toLowerCase(Locale.ROOT);
    }

    private String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private Instant counterStart(long counter, int periodSeconds) {
        return Instant.ofEpochSecond(counter * periodSeconds);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private V3AuthException invalidChallenge() {
        return new V3AuthException(
                "MFA_CHALLENGE_INVALID",
                "The authenticator challenge is invalid. Start login again.",
                HttpStatus.UNAUTHORIZED
        );
    }
}
