package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3VerificationChallenge;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Registration with SMS on: the email code and the SMS code are both required. */
class V3RegistrationContactVerificationTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final Instant USER_CREATED_AT = NOW.minus(Duration.ofDays(1));

    private final V3RegistrationRepository repository = mock(V3RegistrationRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private final V3NotificationService notificationService = mock(V3NotificationService.class);

    private final V3VerificationChallenge emailChallenge = challenge(201L, "email-uuid", "teacher_registration",
            "t***r@example.com", "email-hash");
    private final V3VerificationChallenge smsChallenge = challenge(202L, "sms-uuid", "contact_verification",
            "+63 9** *** 4567", "sms-hash");

    private V3RegistrationPersistenceService service(String smsMode, V3SmsVerificationSender.SmsTransport transport) {
        V3AuthProperties properties = new V3AuthProperties();
        properties.setSmsMode(smsMode);
        properties.setPhilsmsApiToken("token-123");
        V3SmsVerificationSender sender = transport == null
                ? new V3SmsVerificationSender(properties)
                : new V3SmsVerificationSender(properties, transport);
        return new V3RegistrationPersistenceService(repository, passwordEncoder, properties, auditService,
                notificationService, sender);
    }

    private void stubEmailCodeAccepted() {
        when(repository.findChallengeForUpdate("email-uuid")).thenReturn(Optional.of(emailChallenge));
        when(passwordEncoder.matches("123456", "email-hash")).thenReturn(true);
        when(repository.findLatestContactChallengeForUpdate(42L)).thenReturn(Optional.of(smsChallenge));
        when(repository.findStatusId("pending_email_verification")).thenReturn(1);
        when(repository.findStatusId("pending_approval")).thenReturn(2);
        when(repository.markUserEmailVerified(42L, 1, 2, NOW)).thenReturn(1);
    }

    @Test
    void bothCodesRightVerifiesTheContactAndMovesToApproval() {
        stubEmailCodeAccepted();
        when(passwordEncoder.matches("654321", "sms-hash")).thenReturn(true);

        V3VerificationChallenge result = service("log", null).verify("email-uuid", "123456", "654321", null, NOW);

        assertThat(result.contactVerified()).isTrue();
        verify(repository).markChallengeVerified(202L, NOW);
        verify(repository).markContactVerified(42L, NOW);
        verify(repository).markUserEmailVerified(42L, 1, 2, NOW);
    }

    @Test
    void aMissingSmsCodeKeepsTheAccountWaiting() {
        stubEmailCodeAccepted();

        assertThatThrownBy(() -> service("log", null).verify("email-uuid", "123456", null, null, NOW))
                .isInstanceOfSatisfying(V3AuthException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CONTACT_CODE_REQUIRED"));
        verify(repository, never()).markUserEmailVerified(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void aWrongSmsCodeCountsAnAttemptOnTheSmsChallengeOnly() {
        stubEmailCodeAccepted();
        when(passwordEncoder.matches("000000", "sms-hash")).thenReturn(false);

        assertThatThrownBy(() -> service("log", null).verify("email-uuid", "123456", "000000", null, NOW))
                .isInstanceOfSatisfying(V3AuthException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_CONTACT_CODE"));
        verify(repository).recordInvalidAttempt(202L, 1, false, NOW);
        verify(repository, never()).markUserEmailVerified(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void philSmsModeChecksTheCodeHereWithoutCallingPhilSms() {
        stubEmailCodeAccepted();
        when(passwordEncoder.matches("654321", "sms-hash")).thenReturn(true);
        V3SmsVerificationSender.SmsTransport mustNotBeCalled = (url, auth, json) -> {
            throw new AssertionError("verification must not call the SMS provider");
        };

        V3VerificationChallenge result = service("philsms", mustNotBeCalled)
                .verify("email-uuid", "123456", "654321", null, NOW);

        assertThat(result.contactVerified()).isTrue();
        verify(repository).markContactVerified(42L, NOW);
    }

    @Test
    void withSmsOffOnlyTheEmailCodeIsNeeded() {
        stubEmailCodeAccepted();

        V3VerificationChallenge result = service("off", null).verify("email-uuid", "123456", null, null, NOW);

        assertThat(result.contactVerified()).isFalse();
        verify(repository, never()).findLatestContactChallengeForUpdate(anyLong());
        verify(repository).markUserEmailVerified(42L, 1, 2, NOW);
    }

    @Test
    void aLockedSmsCodeCannotBeSkippedWithTheEmailCodeAlone() {
        stubEmailCodeAccepted();
        when(repository.findLatestContactChallengeForUpdate(42L))
                .thenReturn(Optional.of(contactChallenge("locked", "sent", NOW.plusSeconds(600))));

        assertThatThrownBy(() -> service("log", null).verify("email-uuid", "123456", null, null, NOW))
                .isInstanceOfSatisfying(V3AuthException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CONTACT_CHALLENGE_LOCKED"));
        verify(repository, never()).markUserEmailVerified(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void anExpiredSmsCodeNeedsANewSmsCode() {
        stubEmailCodeAccepted();
        when(repository.findLatestContactChallengeForUpdate(42L))
                .thenReturn(Optional.of(contactChallenge("pending", "sent", NOW)));

        assertThatThrownBy(() -> service("log", null).verify("email-uuid", "123456", "654321", null, NOW))
                .isInstanceOfSatisfying(V3AuthException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CONTACT_CODE_EXPIRED"));
        verify(repository).markChallengeExpired(202L, NOW);
        verify(repository, never()).markUserEmailVerified(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void anSmsThatFailedToSendIsNotRequired() {
        stubEmailCodeAccepted();
        when(repository.findLatestContactChallengeForUpdate(42L))
                .thenReturn(Optional.of(contactChallenge("pending", "failed", NOW.plusSeconds(600))));

        V3VerificationChallenge result = service("log", null).verify("email-uuid", "123456", null, null, NOW);

        assertThat(result.contactVerified()).isFalse();
        verify(repository).markUserEmailVerified(42L, 1, 2, NOW);
    }

    @Test
    void anSmsChallengeCannotBeUsedAsTheEmailChallenge() {
        when(repository.findChallengeForUpdate("sms-uuid")).thenReturn(Optional.of(smsChallenge));

        assertThatThrownBy(() -> service("log", null).verify("sms-uuid", "654321", null, null, NOW))
                .isInstanceOfSatisfying(V3AuthException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_VERIFICATION_CHALLENGE"));
        verify(repository, never()).markUserEmailVerified(anyLong(), anyInt(), anyInt(), any());
    }

    private static V3VerificationChallenge challenge(long id, String uuid, String purpose, String masked, String hash) {
        return challenge(id, uuid, purpose, masked, hash, "pending", "sent", NOW.plusSeconds(600));
    }

    private static V3VerificationChallenge contactChallenge(String status, String delivery, Instant expiresAt) {
        return challenge(202L, "sms-uuid", "contact_verification", "+63 9** *** 4567", "sms-hash",
                status, delivery, expiresAt);
    }

    private static V3VerificationChallenge challenge(long id, String uuid, String purpose, String masked, String hash,
                                                     String status, String delivery, Instant expiresAt) {
        return new V3VerificationChallenge(
                id, uuid, 42L, 77L, "SCHOOL-001", "teacher@example.com", "+639171234567", purpose,
                masked, hash, status, delivery, 0, 5, 0, NOW.plusSeconds(60), expiresAt,
                USER_CREATED_AT, "pending_email_verification", false, false);
    }
}
