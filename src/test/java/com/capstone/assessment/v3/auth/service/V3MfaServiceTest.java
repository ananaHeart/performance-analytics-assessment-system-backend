package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.config.V3MfaProperties;
import com.capstone.assessment.v3.auth.dto.V3ConfirmMfaEnrollmentRequest;
import com.capstone.assessment.v3.auth.dto.V3MfaRecoveryCodesResponse;
import com.capstone.assessment.v3.auth.dto.V3MfaEnrollmentRequest;
import com.capstone.assessment.v3.auth.dto.V3MfaSensitiveActionRequest;
import com.capstone.assessment.v3.auth.dto.V3VerifyMfaLoginRequest;
import com.capstone.assessment.v3.auth.exception.V3MfaAttemptException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.model.V3MfaAuthenticationChallenge;
import com.capstone.assessment.v3.auth.model.V3MfaFactor;
import com.capstone.assessment.v3.auth.model.V3UserAccount;
import com.capstone.assessment.v3.auth.model.V3VerifiedMfaLogin;
import com.capstone.assessment.v3.auth.repository.V3AuthRepository;
import com.capstone.assessment.v3.auth.repository.V3MfaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V3MfaServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final String FACTOR_UUID = "11e6c035-b0ca-42c9-8fc4-a2858c67b216";
    private static final String CHALLENGE_UUID = "a98ece17-b5bf-43ef-9393-18f8c779a571";
    private static final long USER_ID = 42L;
    private static final long FACTOR_ID = 77L;

    private final V3MfaRepository mfaRepository = mock(V3MfaRepository.class);
    private final V3AuthRepository authRepository = mock(V3AuthRepository.class);
    private final V3TotpService totpService = mock(V3TotpService.class);
    private final V3MfaSecretCipher secretCipher = mock(V3MfaSecretCipher.class);
    private final V3MfaQrCodeService qrCodeService = mock(V3MfaQrCodeService.class);
    private final V3TokenService tokenService = mock(V3TokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private final V3AuthProperties authProperties = new V3AuthProperties();
    private final V3MfaProperties mfaProperties = new V3MfaProperties();
    private V3MfaService service;

    @BeforeEach
    void setUp() {
        service = new V3MfaService(
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
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom()
        );
        when(secretCipher.isConfigured()).thenReturn(true);
        when(authRepository.findUserById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(authRepository.countRecentMfaFailures(eq(USER_ID), any())).thenReturn(0);
    }

    @Test
    void confirmingEnrollmentEnablesMfaAndUpgradesCurrentSession() {
        V3MfaFactor factor = factor("pending");
        long matchingCounter = NOW.getEpochSecond() / factor.periodSeconds();
        when(mfaRepository.findFactorByUuidAndUserId(FACTOR_UUID, USER_ID))
                .thenReturn(Optional.of(factor));
        when(mfaRepository.findActiveFactorByUserId(USER_ID)).thenReturn(Optional.empty());
        when(secretCipher.decrypt(factor.secretCiphertext())).thenReturn("BASE32SECRET");
        when(totpService.findMatchingCounter(
                eq("BASE32SECRET"), eq("123456"), eq(NOW), eq("SHA1"), eq(6), eq(30), eq(1)
        )).thenReturn(OptionalLong.of(matchingCounter));
        when(mfaRepository.activateFactor(FACTOR_ID, USER_ID, NOW)).thenReturn(1);
        when(mfaRepository.markFactorUsed(FACTOR_ID, Instant.ofEpochSecond(matchingCounter * 30)))
                .thenReturn(1);
        when(authRepository.upgradeSessionToMfa("session-uuid", USER_ID, FACTOR_ID, NOW))
                .thenReturn(1);
        when(tokenService.hashToken(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));

        V3MfaRecoveryCodesResponse response = service.confirmEnrollment(
                authenticatedUser(),
                new V3ConfirmMfaEnrollmentRequest(FACTOR_UUID, "123456"),
                metadata()
        );

        assertTrue(response.enabled());
        assertEquals(FACTOR_UUID, response.factorUuid());
        assertEquals(10, response.recoveryCodes().size());
        assertEquals(10, new HashSet<>(response.recoveryCodes()).size());
        verify(mfaRepository).setUserMfaRequired(USER_ID, true);
        verify(authRepository).upgradeSessionToMfa("session-uuid", USER_ID, FACTOR_ID, NOW);
        verify(mfaRepository).replaceRecoveryCodes(eq(FACTOR_ID), anyString(), anyList(), eq(NOW));
    }

    @Test
    void enrollmentStopsBeforeRecoveryCodesWhenSessionCannotBeUpgraded() {
        V3MfaFactor factor = factor("pending");
        long matchingCounter = NOW.getEpochSecond() / factor.periodSeconds();
        when(mfaRepository.findFactorByUuidAndUserId(FACTOR_UUID, USER_ID))
                .thenReturn(Optional.of(factor));
        when(mfaRepository.findActiveFactorByUserId(USER_ID)).thenReturn(Optional.empty());
        when(secretCipher.decrypt(factor.secretCiphertext())).thenReturn("BASE32SECRET");
        when(totpService.findMatchingCounter(anyString(), anyString(), any(), anyString(), eq(6), eq(30), eq(1)))
                .thenReturn(OptionalLong.of(matchingCounter));
        when(mfaRepository.activateFactor(FACTOR_ID, USER_ID, NOW)).thenReturn(1);
        when(mfaRepository.markFactorUsed(FACTOR_ID, Instant.ofEpochSecond(matchingCounter * 30)))
                .thenReturn(1);
        when(authRepository.upgradeSessionToMfa("session-uuid", USER_ID, FACTOR_ID, NOW))
                .thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.confirmEnrollment(
                        authenticatedUser(),
                        new V3ConfirmMfaEnrollmentRequest(FACTOR_UUID, "123456"),
                        metadata()
                )
        );

        verify(mfaRepository, never()).replaceRecoveryCodes(eq(FACTOR_ID), anyString(), anyList(), eq(NOW));
    }

    @Test
    void invalidLoginCodePersistsChallengeAttemptWithoutVerifyingChallenge() {
        V3MfaAuthenticationChallenge challenge = new V3MfaAuthenticationChallenge(
                91L,
                CHALLENGE_UUID,
                USER_ID,
                FACTOR_ID,
                "pending",
                1,
                5,
                NOW.plusSeconds(300),
                null,
                NOW.minusSeconds(5)
        );
        V3MfaFactor factor = factor("active");
        when(mfaRepository.findChallengeByUuidForUpdate(CHALLENGE_UUID))
                .thenReturn(Optional.of(challenge));
        when(mfaRepository.findFactorById(FACTOR_ID)).thenReturn(Optional.of(factor));
        when(secretCipher.decrypt(factor.secretCiphertext())).thenReturn("BASE32SECRET");
        when(totpService.findMatchingCounter(anyString(), eq("000000"), any(), anyString(), eq(6), eq(30), eq(1)))
                .thenReturn(OptionalLong.empty());

        V3MfaAttemptException exception = assertThrows(
                V3MfaAttemptException.class,
                () -> service.verifyLoginChallenge(
                        new V3VerifyMfaLoginRequest(
                                CHALLENGE_UUID,
                                "000000",
                                "authenticator",
                                "browser-1"
                        ),
                        metadata()
                )
        );

        assertEquals("MFA_CODE_INVALID", exception.getCode());
        assertEquals(3, exception.getDetails().get("attemptsRemaining"));
        verify(mfaRepository).recordFailedChallengeAttempt(91L, 1, 2, false, NOW);
        verify(mfaRepository, never()).verifyChallenge(91L, NOW);
    }

    @Test
    void expiredLoginChallengeIsPersistedAsExpiredAndCannotBeVerified() {
        V3MfaAuthenticationChallenge challenge = new V3MfaAuthenticationChallenge(
                91L,
                CHALLENGE_UUID,
                USER_ID,
                FACTOR_ID,
                "pending",
                0,
                5,
                NOW,
                null,
                NOW.minusSeconds(300)
        );
        when(mfaRepository.findChallengeByUuidForUpdate(CHALLENGE_UUID))
                .thenReturn(Optional.of(challenge));

        V3MfaAttemptException exception = assertThrows(
                V3MfaAttemptException.class,
                () -> service.verifyLoginChallenge(
                        new V3VerifyMfaLoginRequest(
                                CHALLENGE_UUID,
                                "123456",
                                "authenticator",
                                "browser-1"
                        ),
                        metadata()
                )
        );

        assertEquals("MFA_CHALLENGE_EXPIRED", exception.getCode());
        verify(mfaRepository).expireChallenge(91L, NOW);
        verify(mfaRepository, never()).verifyChallenge(91L, NOW);
    }

    @Test
    void recoveryCodeLoginConsumesCodeOnceAndVerifiesChallenge() {
        V3MfaAuthenticationChallenge challenge = new V3MfaAuthenticationChallenge(
                91L,
                CHALLENGE_UUID,
                USER_ID,
                FACTOR_ID,
                "pending",
                0,
                5,
                NOW.plusSeconds(300),
                null,
                NOW.minusSeconds(5)
        );
        V3MfaFactor factor = factor("active");
        when(mfaRepository.findChallengeByUuidForUpdate(CHALLENGE_UUID))
                .thenReturn(Optional.of(challenge));
        when(mfaRepository.findFactorById(FACTOR_ID)).thenReturn(Optional.of(factor));
        when(tokenService.hashToken("ABCD2345EFGH")).thenReturn("recovery-code-hash");
        when(mfaRepository.consumeRecoveryCode(FACTOR_ID, "recovery-code-hash", NOW))
                .thenReturn(1);
        when(mfaRepository.verifyChallenge(91L, NOW)).thenReturn(1);

        V3VerifiedMfaLogin result = service.verifyLoginChallenge(
                new V3VerifyMfaLoginRequest(
                        CHALLENGE_UUID,
                        "ABCD-2345-EFGH",
                        "recovery_code",
                        "browser-1"
                ),
                metadata()
        );

        assertEquals(USER_ID, result.userId());
        assertEquals(FACTOR_ID, result.factorId());
        assertEquals("recovery_code", result.verificationMethod());
        verify(mfaRepository).consumeRecoveryCode(FACTOR_ID, "recovery-code-hash", NOW);
        verify(mfaRepository).verifyChallenge(91L, NOW);
    }

    @Test
    void disablingMfaRequiresBothFactorsAndRevokesOtherSessions() {
        V3MfaFactor factor = factor("active");
        when(passwordEncoder.matches("CurrentPassword1!", "password-hash")).thenReturn(true);
        when(mfaRepository.findActiveFactorByUserId(USER_ID)).thenReturn(Optional.of(factor));
        when(tokenService.hashToken("ABCD2345EFGH")).thenReturn("recovery-code-hash");
        when(mfaRepository.consumeRecoveryCode(FACTOR_ID, "recovery-code-hash", NOW))
                .thenReturn(1);
        when(mfaRepository.disableFactor(FACTOR_ID, USER_ID, NOW)).thenReturn(1);

        service.disable(
                authenticatedUser(),
                new V3MfaSensitiveActionRequest(
                        "CurrentPassword1!",
                        "ABCD-2345-EFGH",
                        "recovery_code"
                ),
                metadata()
        );

        verify(mfaRepository).setUserMfaRequired(USER_ID, false);
        verify(mfaRepository).deleteRecoveryCodes(FACTOR_ID);
        verify(mfaRepository).cancelPendingChallenges(USER_ID, NOW);
        verify(authRepository).revokeOtherSessions(USER_ID, "session-uuid", NOW);
        verify(auditService).record(
                eq(USER_ID),
                eq("MFA_DISABLED"),
                eq("user_mfa_factors"),
                eq(FACTOR_UUID),
                eq("success"),
                eq(metadata()),
                any(),
                eq(NOW)
        );
    }

    @Test
    void enrollmentWithWrongPasswordDoesNotCreateFactor() {
        when(passwordEncoder.matches("WrongPassword1!", "password-hash")).thenReturn(false);

        V3MfaAttemptException exception = assertThrows(
                V3MfaAttemptException.class,
                () -> service.beginEnrollment(
                        authenticatedUser(),
                        new V3MfaEnrollmentRequest("WrongPassword1!", "My phone"),
                        metadata()
                )
        );

        assertEquals("PASSWORD_CONFIRMATION_FAILED", exception.getCode());
        verify(authRepository).recordLoginAttempt(
                eq(USER_ID),
                eq("teacher@example.com"),
                eq("127.0.0.1"),
                eq("browser-1"),
                eq("JUnit"),
                eq(false),
                eq("invalid_credentials"),
                eq(NOW)
        );
        verify(mfaRepository, never()).createPendingFactor(
                anyString(),
                eq(USER_ID),
                anyString(),
                any(),
                eq(1),
                eq(NOW)
        );
    }

    private V3AuthenticatedUser authenticatedUser() {
        return new V3AuthenticatedUser(
                USER_ID,
                "SCHOOL-001",
                "teacher@example.com",
                "teacher",
                "active",
                "session-uuid"
        );
    }

    private V3UserAccount activeUser() {
        return new V3UserAccount(
                USER_ID,
                "SCHOOL-001",
                12L,
                "Test",
                null,
                "Teacher",
                null,
                "teacher@example.com",
                "password-hash",
                "teacher",
                "active",
                0,
                null,
                NOW.minusSeconds(60),
                false,
                NOW.minusSeconds(600)
        );
    }

    private V3MfaFactor factor(String status) {
        return new V3MfaFactor(
                FACTOR_ID,
                FACTOR_UUID,
                USER_ID,
                "Authenticator app",
                new byte[]{1, 2, 3},
                1,
                "SHA1",
                6,
                30,
                status,
                NOW.minusSeconds(60),
                "active".equals(status) ? NOW.minusSeconds(30) : null,
                null
        );
    }

    private V3RequestMetadata metadata() {
        return new V3RequestMetadata("127.0.0.1", "JUnit", "browser-1");
    }
}
