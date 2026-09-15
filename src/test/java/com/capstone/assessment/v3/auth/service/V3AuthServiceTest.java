package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.dto.V3LoginRequest;
import com.capstone.assessment.v3.auth.dto.V3LoginResponse;
import com.capstone.assessment.v3.auth.dto.V3MfaLoginChallengeResponse;
import com.capstone.assessment.v3.auth.dto.V3VerifyMfaLoginRequest;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3UserAccount;
import com.capstone.assessment.v3.auth.model.V3VerificationChallenge;
import com.capstone.assessment.v3.auth.model.V3VerifiedMfaLogin;
import com.capstone.assessment.v3.auth.repository.V3AuthRepository;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V3AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final String EMAIL = "teacher@example.com";
    private static final String PASSWORD = "Strong@1234";
    private static final String PASSWORD_HASH = "stored-password-hash";

    private final V3AuthRepository authRepository = mock(V3AuthRepository.class);
    private final V3RegistrationRepository registrationRepository = mock(V3RegistrationRepository.class);
    private final V3RegistrationPersistenceService persistenceService =
            mock(V3RegistrationPersistenceService.class);
    private final V3TokenService tokenService = mock(V3TokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private final V3MfaService mfaService = mock(V3MfaService.class);
    private final V3AuthProperties properties = new V3AuthProperties();
    private V3AuthService service;

    @BeforeEach
    void setUp() {
        service = new V3AuthService(
                authRepository,
                registrationRepository,
                persistenceService,
                tokenService,
                passwordEncoder,
                properties,
                auditService,
                mfaService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(authRepository.countRecentCredentialFailures(eq(EMAIL), any(), any())).thenReturn(0);
    }

    @Test
    void validPasswordForPendingRegistrationReturnsResumeStateWithoutSession() {
        V3UserAccount user = user("pending_email_verification", false, NOW.minus(Duration.ofDays(1)));
        when(authRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(registrationRepository.findLatestChallenge(user.userId()))
                .thenReturn(Optional.of(challenge(user, NOW.plusSeconds(600), NOW.minusSeconds(1))));
        when(registrationRepository.countVerificationMessagesSince(eq(user.userId()), any())).thenReturn(1);

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.login(loginRequest(), metadata())
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("EMAIL_VERIFICATION_REQUIRED", exception.getCode());
        assertEquals(true, exception.getDetails().get("canResend"));
        assertEquals(NOW.minus(Duration.ofDays(1)).plus(Duration.ofDays(30)),
                exception.getDetails().get("registrationExpiresAt"));
        verify(authRepository, never()).createSession(
                anyString(), eq(user.userId()), anyString(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void wrongPasswordNeverRevealsPendingRegistrationState() {
        V3UserAccount user = user("pending_email_verification", false, NOW.minus(Duration.ofDays(1)));
        when(authRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.login(loginRequest(), metadata())
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        verify(registrationRepository, never()).findLatestChallenge(user.userId());
        verify(persistenceService, never()).purgeExpiredProvisionalUser(any(Long.class), any(), any());
    }

    @Test
    void expiredPendingRegistrationIsPurgedAndReportedAsInvalidCredentials() {
        Instant createdAt = NOW.minus(Duration.ofDays(31));
        V3UserAccount user = user("pending_email_verification", false, createdAt);
        when(authRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(persistenceService.purgeExpiredProvisionalUser(
                user.userId(), NOW.minus(Duration.ofDays(30)), NOW)).thenReturn(true);

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.login(loginRequest(), metadata())
        );

        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        verify(persistenceService).purgeExpiredProvisionalUser(
                user.userId(), NOW.minus(Duration.ofDays(30)), NOW
        );
        verify(registrationRepository, never()).findLatestChallenge(user.userId());
    }

    @Test
    void activeMfaRequiredAccountReturnsChallengeWithoutSession() {
        V3UserAccount user = user("active", true, NOW.minus(Duration.ofDays(10)));
        V3MfaLoginChallengeResponse challenge = new V3MfaLoginChallengeResponse(
                "challenge-uuid",
                NOW.plusSeconds(300),
                5,
                List.of("authenticator", "recovery_code"),
                "t***@example.com"
        );
        when(authRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(mfaService.startLoginChallenge(eq(user), any(V3RequestMetadata.class), eq(NOW)))
                .thenReturn(challenge);

        V3LoginResponse response = service.login(loginRequest(), metadata());

        assertTrue(response.mfaRequired());
        assertEquals(challenge, response.mfaChallenge());
        assertNull(response.accessToken());
        verify(authRepository, never()).createSession(
                anyString(), eq(user.userId()), anyString(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void verifiedMfaChallengeCreatesMfaLevelSession() {
        V3UserAccount user = user("active", true, NOW.minus(Duration.ofDays(10)));
        V3VerifyMfaLoginRequest request = new V3VerifyMfaLoginRequest(
                "challenge-uuid",
                "123456",
                "authenticator",
                "browser-2"
        );
        when(mfaService.verifyLoginChallenge(eq(request), any(V3RequestMetadata.class)))
                .thenReturn(new V3VerifiedMfaLogin(42L, 91L, "challenge-uuid", "authenticator"));
        when(authRepository.findUserById(42L)).thenReturn(Optional.of(user));
        when(tokenService.generateToken()).thenReturn("mfa-access-token");
        when(tokenService.hashToken("mfa-access-token")).thenReturn("mfa-token-hash");

        V3LoginResponse response = service.verifyMfaLogin(request, metadata());

        assertFalse(response.mfaRequired());
        assertEquals("mfa-access-token", response.accessToken());
        assertEquals(NOW.plus(Duration.ofHours(8)), response.expiresAt());
        verify(authRepository).createMfaSession(
                anyString(),
                eq(42L),
                eq(91L),
                eq("mfa-token-hash"),
                eq("browser-2"),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq(NOW),
                eq(NOW.plus(Duration.ofHours(8)))
        );
    }

    @Test
    void activeVerifiedAccountCreatesOneHashedSession() {
        V3UserAccount user = user("active", false, NOW.minus(Duration.ofDays(10)));
        when(authRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(tokenService.generateToken()).thenReturn("raw-access-token");
        when(tokenService.hashToken("raw-access-token")).thenReturn("token-hash");

        V3LoginResponse response = service.login(loginRequest(), metadata());

        assertEquals("Bearer", response.tokenType());
        assertEquals("raw-access-token", response.accessToken());
        assertEquals(NOW.plus(Duration.ofHours(8)), response.expiresAt());
        assertNotNull(response.user());
        verify(authRepository).createSession(
                anyString(),
                eq(user.userId()),
                eq("token-hash"),
                eq("browser-1"),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq(NOW),
                eq(NOW.plus(Duration.ofHours(8)))
        );
        verify(auditService).record(
                eq(user.userId()),
                eq("LOGIN"),
                eq("auth_sessions"),
                anyString(),
                eq("success"),
                any(V3RequestMetadata.class),
                anyMap(),
                eq(NOW)
        );
    }

    private V3UserAccount user(String status, boolean mfaRequired, Instant createdAt) {
        return new V3UserAccount(
                42L,
                "SCHOOL-001",
                77L,
                "Test",
                null,
                "Teacher",
                null,
                EMAIL,
                PASSWORD_HASH,
                "teacher",
                status,
                0,
                null,
                "active".equals(status) ? NOW.minusSeconds(60) : null,
                mfaRequired,
                createdAt
        );
    }

    private V3VerificationChallenge challenge(
            V3UserAccount user,
            Instant otpExpiresAt,
            Instant resendAvailableAt
    ) {
        return new V3VerificationChallenge(
                100L,
                "challenge-uuid",
                user.userId(),
                user.addressId(),
                user.schoolId(),
                user.email(),
                "t***r@example.com",
                "otp-hash",
                "pending",
                "sent",
                0,
                5,
                0,
                resendAvailableAt,
                otpExpiresAt,
                user.createdAt(),
                user.status(),
                false
        );
    }

    private V3LoginRequest loginRequest() {
        return new V3LoginRequest(EMAIL, PASSWORD, "browser-1");
    }

    private V3RequestMetadata metadata() {
        return new V3RequestMetadata("127.0.0.1", "JUnit", "browser-1");
    }
}
