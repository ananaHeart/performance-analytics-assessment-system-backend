package com.capstone.assessment.v3.account.service;

import com.capstone.assessment.v3.account.dto.V3AccountCodeResponse;
import com.capstone.assessment.v3.account.dto.V3AccountVerificationRequest;
import com.capstone.assessment.v3.account.dto.V3ContactChangeRequest;
import com.capstone.assessment.v3.account.dto.V3EmailChangeRequest;
import com.capstone.assessment.v3.account.dto.V3PasswordChangeRequest;
import com.capstone.assessment.v3.account.repository.V3AccountSettingsRepository;
import com.capstone.assessment.v3.account.repository.V3AccountSettingsRepository.AccountRow;
import com.capstone.assessment.v3.account.repository.V3AccountSettingsRepository.ChallengeRow;
import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.repository.V3AuthRepository;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3SmsVerificationSender;
import com.capstone.assessment.v3.auth.service.V3VerificationEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V3AccountSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T02:00:00Z");
    private static final V3AuthenticatedUser USER = new V3AuthenticatedUser(
            42L, "SCHOOL-001", "ana@example.com", "teacher", "active", "session-1");
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String PASSWORD_HASH = ENCODER.encode("Current#Pass1");

    private final V3AccountSettingsRepository repository = mock(V3AccountSettingsRepository.class);
    private final V3RegistrationRepository registrationRepository = mock(V3RegistrationRepository.class);
    private final V3AuthRepository authRepository = mock(V3AuthRepository.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private final V3VerificationEmailSender emailSender = mock(V3VerificationEmailSender.class);
    private final V3SmsVerificationSender smsSender = mock(V3SmsVerificationSender.class);
    private V3AccountSettingsService service;

    @BeforeEach
    void setUp() {
        service = new V3AccountSettingsService(repository, registrationRepository, authRepository, auditService,
                emailSender, smsSender, ENCODER, new V3AuthProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.lockAccount(42L)).thenReturn(Optional.of(account(NOW, null)));
        when(repository.findLastChange(anyLong(), anyString())).thenReturn(Optional.empty());
        when(emailSender.sendVerificationCode(anyString(), anyString())).thenReturn("local_log");
    }

    @Test
    void set6ChangingThePasswordEndsTheOtherSessionsOnly() {
        assertEquals("CURRENT_PASSWORD_INCORRECT", assertThrows(V3FieldValidationException.class,
                () -> service.changePassword(USER, new V3PasswordChangeRequest("Wrong#Pass1", "New#Passw0rd"), null))
                .getErrors().get("code"));
        assertThrows(V3FieldValidationException.class,
                () -> service.changePassword(USER, new V3PasswordChangeRequest("Current#Pass1", "weakpass"), null));

        service.changePassword(USER, new V3PasswordChangeRequest("Current#Pass1", "New#Passw0rd"), null);

        verify(repository).updatePassword(eq(42L), anyString(), eq(NOW));
        verify(authRepository).revokeOtherSessions(42L, "session-1", NOW);
    }

    @Test
    void set1TheNewEmailIsSavedOnlyWithTheCodeSentToIt() {
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        when(repository.insertChallenge(anyString(), eq(42L), eq("sensitive_action"), eq("email"), anyString(),
                hash.capture(), anyInt(), anyInt(), any(), any(), any())).thenReturn(7L);

        V3AccountCodeResponse sent = service.startEmailChange(USER,
                new V3EmailChangeRequest("Ana.New@Example.com", null, null), null);

        verify(emailSender).sendVerificationCode(eq("ana.new@example.com"), otp.capture());
        when(repository.lockChallenge(sent.challengeUuid(), 42L)).thenReturn(Optional.of(new ChallengeRow(
                7L, sent.challengeUuid(), "sensitive_action", "email", hash.getValue(), "pending", "sent",
                0, 5, 0, NOW, NOW.plusSeconds(600))));

        // The same code does not confirm a different email.
        assertEquals("INVALID_VERIFICATION_CODE", assertThrows(V3AuthException.class, () -> service.confirmEmailChange(
                USER, new V3EmailChangeRequest("other@example.com", sent.challengeUuid(), otp.getValue()), null))
                .getCode());
        service.confirmEmailChange(USER,
                new V3EmailChangeRequest("ana.new@example.com", sent.challengeUuid(), otp.getValue()), null);

        verify(repository).updateEmail(42L, "ana.new@example.com", NOW);
        verify(registrationRepository).markChallengeVerified(7L, NOW);
    }

    @Test
    void set1AfterAChangeTheNextOneWaitsSixMonths() {
        when(repository.findLastChange(42L, "account.email_change"))
                .thenReturn(Optional.of(Instant.parse("2026-09-01T02:00:00Z")));

        V3AuthException exception = assertThrows(V3AuthException.class, () -> service.startEmailChange(USER,
                new V3EmailChangeRequest("ana.new@example.com", null, null), null));

        assertEquals("CHANGE_NOT_ALLOWED_YET", exception.getCode());
        assertEquals(Instant.parse("2027-03-01T02:00:00Z"), exception.getDetails().get("nextAllowedAt"));
        assertEquals(Instant.parse("2027-03-01T02:00:00Z"), service.getAccount(USER).nextEmailChangeAt());
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void set1AnSmsChangeNeedsSmsToBeOnAndANewNumber() {
        assertEquals("SMS_NOT_AVAILABLE", assertThrows(V3AuthException.class, () -> service.startContactChange(USER,
                new V3ContactChangeRequest("09181234567", null, null), null)).getCode());
        assertEquals("This is already your contact number.", assertThrows(V3FieldValidationException.class,
                () -> service.startContactChange(USER, new V3ContactChangeRequest("09171234567", null, null), null))
                .getErrors().get("newContactNumber"));
    }

    @Test
    void ver1OnlyAnUnverifiedChannelCanBeVerified() {
        assertEquals("ALREADY_VERIFIED", assertThrows(V3AuthException.class, () -> service.startVerification(USER,
                new V3AccountVerificationRequest("email", null, null), null)).getCode());

        when(repository.lockAccount(42L)).thenReturn(Optional.of(account(null, NOW)));
        when(repository.insertChallenge(anyString(), eq(42L), eq("email_verification"), eq("email"), anyString(),
                anyString(), anyInt(), anyInt(), any(), any(), any())).thenReturn(8L);
        V3AccountCodeResponse sent = service.startVerification(USER,
                new V3AccountVerificationRequest("email", null, null), null);

        assertEquals("email", sent.channel());
        verify(emailSender).sendVerificationCode(eq("ana@example.com"), anyString());
    }

    @Test
    void ver2AResendCancelsTheOldCodeAndAReplacedCodeSaysSo() {
        when(repository.lockChallenge("old", 42L)).thenReturn(Optional.of(new ChallengeRow(
                5L, "old", "sensitive_action", "email", "hash", "cancelled", "sent", 0, 5, 0, NOW, NOW)));

        assertEquals("VERIFICATION_CODE_REPLACED", assertThrows(V3AuthException.class, () -> service.confirmEmailChange(
                USER, new V3EmailChangeRequest("ana.new@example.com", "old", "123456"), null)).getCode());
        verify(repository, never()).updateEmail(anyLong(), anyString(), any());
    }

    private static AccountRow account(Instant emailVerifiedAt, Instant contactVerifiedAt) {
        return new AccountRow(42L, "teacher", "Ana", null, "Cruz", null, "ana@example.com", emailVerifiedAt,
                "+639171234567", contactVerifiedAt, PASSWORD_HASH, null);
    }
}
