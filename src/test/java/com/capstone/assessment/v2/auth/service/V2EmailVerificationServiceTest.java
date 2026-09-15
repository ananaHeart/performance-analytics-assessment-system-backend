package com.capstone.assessment.v2.auth.service;

import com.capstone.assessment.v2.auth.config.V2EmailVerificationProperties;
import com.capstone.assessment.v2.auth.dto.V2EmailVerificationResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2EmailVerificationOtp;
import com.capstone.assessment.v2.auth.model.V2EmailVerificationUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.capstone.assessment.v2.auth.repository.V2EmailVerificationRepository;
import com.capstone.assessment.v2.notification.service.V2NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T08:00:00Z");
    private static final V2RequestMetadata METADATA = new V2RequestMetadata("203.0.113.7", "JUnit", "device-1");

    @Mock private V2EmailVerificationRepository repository;
    @Mock private V2AuthRepository authRepository;
    @Mock private V2NotificationService notificationService;
    @Mock private V2VerificationEmailSender emailSender;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecureRandom secureRandom;

    private V2EmailVerificationService service;

    @BeforeEach
    void setUp() {
        V2EmailVerificationProperties properties = new V2EmailVerificationProperties();
        properties.setOtpTtl(Duration.ofMinutes(10));
        properties.setResendCooldown(Duration.ofMinutes(1));
        properties.setMaxAttempts(5);
        service = new V2EmailVerificationService(
                repository,
                authRepository,
                notificationService,
                emailSender,
                passwordEncoder,
                properties,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                secureRandom
        );
    }

    @Test
    void correctOtpVerifiesEmailAndNotifiesPrincipal() {
        V2EmailVerificationUser user = pendingUser();
        V2EmailVerificationOtp code = activeCode();
        when(repository.findTeacherByEmail("teacher@example.com")).thenReturn(Optional.of(user));
        when(repository.findLatestCodeForUpdate(20L)).thenReturn(Optional.of(code));
        when(passwordEncoder.matches("123456", "otp-hash")).thenReturn(true);
        when(repository.markEmailVerified(20L, NOW)).thenReturn(1);

        V2EmailVerificationResponse response = service.verify("Teacher@Example.com", "123456", METADATA);

        assertEquals(true, response.emailVerified());
        assertEquals("pending", response.accountStatus());
        verify(repository).markUsed(501L, NOW);
        verify(repository).markEmailVerified(20L, NOW);
        verify(notificationService).notifySchoolPrincipals(
                "SCHOOL-001",
                "teacher_registration_pending",
                "Teacher approval pending",
                "Maria Santos verified their email and is ready for approval.",
                "users",
                "20",
                "teacher-registration:20",
                NOW
        );
        verify(authRepository).recordAudit(
                any(), eq(20L), eq("VERIFY_TEACHER_EMAIL"), eq("users"), eq("20"), eq("success"),
                eq("203.0.113.7"), eq("device-1"), eq("JUnit"), any(), eq(NOW)
        );
    }

    @Test
    void incorrectOtpConsumesAnAttemptWithoutVerification() {
        when(repository.findTeacherByEmail("teacher@example.com")).thenReturn(Optional.of(pendingUser()));
        when(repository.findLatestCodeForUpdate(20L)).thenReturn(Optional.of(activeCode()));
        when(passwordEncoder.matches("000000", "otp-hash")).thenReturn(false);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> service.verify("teacher@example.com", "000000", METADATA)
        );

        assertEquals("INVALID_VERIFICATION_CODE", exception.getCode());
        verify(repository).incrementAttempts(501L);
        verify(repository, never()).markEmailVerified(any(Long.class), any());
    }

    @Test
    void expiredOtpCannotVerifyEmail() {
        V2EmailVerificationOtp expired = new V2EmailVerificationOtp(
                501L, 20L, "otp-hash", 0, 5, NOW.minusSeconds(1), NOW.minusSeconds(30), null
        );
        when(repository.findTeacherByEmail("teacher@example.com")).thenReturn(Optional.of(pendingUser()));
        when(repository.findLatestCodeForUpdate(20L)).thenReturn(Optional.of(expired));

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> service.verify("teacher@example.com", "123456", METADATA)
        );

        assertEquals("VERIFICATION_CODE_EXPIRED", exception.getCode());
        verify(repository).markUsed(501L, NOW);
        verify(repository, never()).markEmailVerified(any(Long.class), any());
    }

    @Test
    void resendCooldownIsEnforced() {
        when(repository.findTeacherByEmail("teacher@example.com")).thenReturn(Optional.of(pendingUser()));
        when(repository.findLatestCodeForUpdate(20L)).thenReturn(Optional.of(activeCode()));

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> service.resendCode("teacher@example.com", METADATA)
        );

        assertEquals("VERIFICATION_RESEND_COOLDOWN", exception.getCode());
        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    private V2EmailVerificationUser pendingUser() {
        return new V2EmailVerificationUser(
                20L, "SCHOOL-001", "Maria", "Santos", "teacher@example.com", "pending", false
        );
    }

    private V2EmailVerificationOtp activeCode() {
        return new V2EmailVerificationOtp(
                501L, 20L, "otp-hash", 0, 5, NOW.plusSeconds(600), NOW.plusSeconds(60), null
        );
    }
}
