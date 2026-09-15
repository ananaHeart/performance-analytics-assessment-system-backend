package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3PendingEmailChallenge;
import com.capstone.assessment.v3.auth.model.V3ProvisionalUser;
import com.capstone.assessment.v3.auth.model.V3VerificationChallenge;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V3RegistrationPersistenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final Instant USER_CREATED_AT = NOW.minus(Duration.ofDays(4));

    private final V3RegistrationRepository repository = mock(V3RegistrationRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final V3AuthProperties properties = new V3AuthProperties();
    private final V3AuditService auditService = mock(V3AuditService.class);
    private final V3NotificationService notificationService = mock(V3NotificationService.class);
    private final V3RegistrationPersistenceService service = new V3RegistrationPersistenceService(
            repository,
            passwordEncoder,
            properties,
            auditService,
            notificationService
    );

    @Test
    void fifthInvalidOtpLocksChallengeWithoutChangingAccountStatus() {
        V3VerificationChallenge challenge = challenge(4, "pending", NOW.plusSeconds(300), null);
        when(repository.findChallengeForUpdate("challenge-uuid")).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("000000", "otp-hash")).thenReturn(false);

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.verify("challenge-uuid", "000000", metadata(), NOW)
        );

        assertEquals(HttpStatus.LOCKED, exception.getStatus());
        assertEquals("VERIFICATION_CHALLENGE_LOCKED", exception.getCode());
        assertEquals(0, exception.getDetails().get("remainingAttempts"));
        verify(repository).recordInvalidAttempt(challenge.challengeId(), 5, true, NOW);
    }

    @Test
    void correctOtpMovesUserToPendingApprovalAndInvalidatesOtherCodes() {
        V3VerificationChallenge challenge = challenge(0, "pending", NOW.plusSeconds(300), null);
        when(repository.findChallengeForUpdate("challenge-uuid")).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "otp-hash")).thenReturn(true);
        when(repository.findStatusId("pending_email_verification")).thenReturn(1);
        when(repository.findStatusId("pending_approval")).thenReturn(2);
        when(repository.markUserEmailVerified(challenge.userId(), 1, 2, NOW)).thenReturn(1);

        V3VerificationChallenge result = service.verify("challenge-uuid", "123456", metadata(), NOW);

        assertEquals(challenge, result);
        verify(repository).markChallengeVerified(challenge.challengeId(), NOW);
        verify(repository).cancelOtherChallenges(challenge.userId(), challenge.challengeId(), NOW);
        verify(auditService).record(
                eq(challenge.userId()),
                eq("VERIFY_TEACHER_EMAIL"),
                eq("verification_challenges"),
                eq(challenge.challengeUuid()),
                eq("success"),
                eq(metadata()),
                anyMap(),
                eq(NOW)
        );
        verify(notificationService).notifyTeacherPendingApproval(challenge.userId(), NOW);
    }

    @Test
    void resendCreatesNewChallengeButKeepsOriginalThirtyDayDeadline() {
        V3VerificationChallenge challenge = challenge(
                0,
                "pending",
                NOW.minusSeconds(1),
                NOW.minusSeconds(1)
        );
        when(repository.findChallengeForUpdate("challenge-uuid")).thenReturn(Optional.of(challenge));
        when(repository.countVerificationMessagesSince(eq(challenge.userId()), eq(NOW.minusSeconds(86_400))))
                .thenReturn(1);
        when(passwordEncoder.encode(anyString())).thenReturn("replacement-otp-hash");
        when(repository.insertChallenge(
                anyString(),
                eq(challenge.userId()),
                eq(challenge.emailMasked()),
                eq("replacement-otp-hash"),
                eq(5),
                eq(challenge.resendCount() + 1),
                eq(NOW.plus(Duration.ofMinutes(1))),
                eq(NOW.plus(Duration.ofMinutes(10))),
                eq(NOW)
        )).thenReturn(202L);

        V3PendingEmailChallenge replacement = service.prepareResend("challenge-uuid", metadata(), NOW);

        assertEquals(USER_CREATED_AT.plus(Duration.ofDays(30)), replacement.registrationExpiresAt());
        assertEquals(challenge.resendCount() + 1, replacement.resendCount());
        verify(repository).cancelChallenge(challenge.challengeId(), NOW);
    }

    @Test
    void purgeAnonymizesSecurityHistoryThenDeletesOrphanAddress() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        V3ProvisionalUser user = new V3ProvisionalUser(
                42L,
                77L,
                "expired@example.com",
                cutoff.minusSeconds(1)
        );
        when(repository.findProvisionalUserForUpdate(user.userId())).thenReturn(Optional.of(user));
        when(repository.deleteExpiredProvisionalUser(user.userId(), cutoff)).thenReturn(1);

        boolean deleted = service.purgeExpiredProvisionalUser(user.userId(), cutoff, NOW);

        assertTrue(deleted);
        verify(repository).anonymizeLoginAttempts(
                eq(user.userId()),
                eq(user.email()),
                org.mockito.ArgumentMatchers.matches("deleted-v3-[a-f0-9]{32}@invalid\\.local")
        );
        verify(repository).deleteAddressIfOrphan(user.addressId());
    }

    @Test
    void identityCleanupPurgesEveryExpiredDuplicateCandidate() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        when(repository.findExpiredProvisionalUserIdsByIdentity(
                "teacher@example.com", "+639123456789", cutoff))
                .thenReturn(List.of(41L, 42L));
        when(repository.findProvisionalUserForUpdate(41L)).thenReturn(Optional.empty());
        when(repository.findProvisionalUserForUpdate(42L)).thenReturn(Optional.empty());

        service.purgeExpiredProvisionalIdentity(
                "teacher@example.com",
                "+639123456789",
                cutoff,
                NOW
        );

        verify(repository).findProvisionalUserForUpdate(41L);
        verify(repository).findProvisionalUserForUpdate(42L);
    }

    private V3VerificationChallenge challenge(
            int attemptCount,
            String status,
            Instant expiresAt,
            Instant nextResendAt
    ) {
        return new V3VerificationChallenge(
                101L,
                "challenge-uuid",
                42L,
                77L,
                "SCHOOL-001",
                "teacher@example.com",
                "t***r@example.com",
                "otp-hash",
                status,
                "sent",
                attemptCount,
                5,
                1,
                nextResendAt,
                expiresAt,
                USER_CREATED_AT,
                "pending_email_verification",
                false
        );
    }

    private V3RequestMetadata metadata() {
        return new V3RequestMetadata("127.0.0.1", "JUnit", "browser-1");
    }
}
