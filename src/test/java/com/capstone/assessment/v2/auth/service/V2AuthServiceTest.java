package com.capstone.assessment.v2.auth.service;

import com.capstone.assessment.v2.auth.config.V2AuthProperties;
import com.capstone.assessment.v2.auth.dto.V2LoginRequest;
import com.capstone.assessment.v2.auth.dto.V2LoginResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.model.V2LoginUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T02:00:00Z");
    private static final V2RequestMetadata METADATA = new V2RequestMetadata(
            "203.0.113.10",
            "JUnit",
            "test-device"
    );

    @Mock
    private V2AuthRepository authRepository;

    @Mock
    private V2TokenService tokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private V2AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new V2AuthService(
                authRepository,
                tokenService,
                passwordEncoder,
                new V2AuthProperties(Duration.ofHours(8), 5, Duration.ofMinutes(15)),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void loginCreatesHashedServerSessionForEligibleUser() {
        V2LoginUser user = eligibleUser(0, null);
        when(authRepository.countRecentFailedAttempts(any(), any(), any())).thenReturn(0);
        when(authRepository.findLoginUserByEmail("teacher@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
        when(tokenService.generateToken()).thenReturn("raw-session-token");
        when(tokenService.hashToken("raw-session-token")).thenReturn("hashed-session-token");

        V2LoginResponse response = authService.login(
                new V2LoginRequest(" Teacher@Example.com ", "correct-password", " test-device "),
                METADATA
        );

        assertEquals("Bearer", response.tokenType());
        assertEquals("raw-session-token", response.accessToken());
        assertEquals(NOW.plus(Duration.ofHours(8)), response.expiresAt());
        assertEquals(7L, response.user().userId());
        assertEquals("teacher", response.user().role());

        verify(authRepository).recordSuccessfulLogin(7L, NOW);
        verify(authRepository).createSession(
                any(String.class),
                eq(7L),
                eq("hashed-session-token"),
                eq("test-device"),
                eq("203.0.113.10"),
                eq("JUnit"),
                eq(NOW),
                eq(NOW.plus(Duration.ofHours(8)))
        );
        verify(authRepository).recordLoginAttempt(
                7L,
                "teacher@example.com",
                "203.0.113.10",
                "test-device",
                "JUnit",
                true,
                null,
                NOW
        );
    }

    @Test
    void wrongPasswordLocksAccountAtConfiguredFailureThreshold() {
        V2LoginUser user = eligibleUser(4, null);
        when(authRepository.countRecentFailedAttempts(any(), any(), any())).thenReturn(0);
        when(authRepository.findLoginUserByEmail("teacher@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> authService.login(
                        new V2LoginRequest("teacher@example.com", "wrong-password", "test-device"),
                        METADATA
                )
        );

        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        verify(authRepository).recordFailedLogin(7L, 5, NOW.plus(Duration.ofMinutes(15)));
        verify(authRepository, never()).createSession(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void inactiveAccountIsRejectedBeforePasswordVerification() {
        V2LoginUser user = new V2LoginUser(
                7L,
                "SCHOOL-001",
                "Maria",
                null,
                "Santos",
                null,
                "teacher@example.com",
                "stored-hash",
                "teacher",
                "pending",
                0,
                null,
                NOW.minusSeconds(60)
        );
        when(authRepository.countRecentFailedAttempts(any(), any(), any())).thenReturn(0);
        when(authRepository.findLoginUserByEmail("teacher@example.com")).thenReturn(Optional.of(user));

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> authService.login(
                        new V2LoginRequest("teacher@example.com", "password", "test-device"),
                        METADATA
                )
        );

        assertEquals("ACCOUNT_NOT_ACTIVE", exception.getCode());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void unverifiedEmailIsRejectedBeforeSessionCreation() {
        V2LoginUser user = eligibleUser(0, null, null);
        when(authRepository.countRecentFailedAttempts(any(), any(), any())).thenReturn(0);
        when(authRepository.findLoginUserByEmail("teacher@example.com")).thenReturn(Optional.of(user));

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> authService.login(
                        new V2LoginRequest("teacher@example.com", "password", "test-device"),
                        METADATA
                )
        );

        assertEquals("EMAIL_NOT_VERIFIED", exception.getCode());
        verify(authRepository, never()).createSession(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void authenticateUsesTokenHashAndTouchesActiveSession() {
        V2AuthenticatedUser user = new V2AuthenticatedUser(
                7L,
                "SCHOOL-001",
                "teacher@example.com",
                "teacher",
                "active",
                "session-uuid"
        );
        when(tokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(authRepository.findAuthenticatedUserByTokenHash("hashed-token", NOW))
                .thenReturn(Optional.of(user));

        Optional<V2AuthenticatedUser> result = authService.authenticate("raw-token");

        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        verify(authRepository).touchSession("session-uuid", NOW);
    }

    @Test
    void logoutRevokesOnlyTheAuthenticatedUsersSession() {
        V2AuthenticatedUser user = new V2AuthenticatedUser(
                7L,
                "SCHOOL-001",
                "teacher@example.com",
                "teacher",
                "active",
                "session-uuid"
        );

        authService.logout(user, METADATA);

        verify(authRepository).revokeSession("session-uuid", 7L, NOW);
        verify(authRepository).recordAudit(
                any(String.class),
                eq(7L),
                eq("LOGOUT"),
                eq("auth_sessions"),
                eq("session-uuid"),
                eq("success"),
                eq("203.0.113.10"),
                eq("test-device"),
                eq("JUnit"),
                eq("{}"),
                eq(NOW)
        );
    }

    private V2LoginUser eligibleUser(int failedLoginCount, Instant lockedUntilAt) {
        return eligibleUser(failedLoginCount, lockedUntilAt, NOW.minusSeconds(60));
    }

    private V2LoginUser eligibleUser(
            int failedLoginCount,
            Instant lockedUntilAt,
            Instant emailVerifiedAt
    ) {
        return new V2LoginUser(
                7L,
                "SCHOOL-001",
                "Maria",
                null,
                "Santos",
                null,
                "teacher@example.com",
                "stored-hash",
                "teacher",
                "active",
                failedLoginCount,
                lockedUntilAt,
                emailVerifiedAt
        );
    }
}
