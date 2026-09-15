package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.dto.V3TeacherRegistrationRequest;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3PendingEmailChallenge;
import com.capstone.assessment.v3.auth.model.V3ProvisionalUser;
import com.capstone.assessment.v3.auth.model.V3VerificationChallenge;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Profile("v3")
@Service
public class V3RegistrationPersistenceService {

    static final String PENDING_EMAIL_STATUS = "pending_email_verification";
    static final String PENDING_APPROVAL_STATUS = "pending_approval";

    private final V3RegistrationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final V3AuthProperties properties;
    private final V3AuditService auditService;
    private final V3NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public V3RegistrationPersistenceService(
            V3RegistrationRepository repository,
            PasswordEncoder passwordEncoder,
            V3AuthProperties properties,
            V3AuditService auditService,
            V3NotificationService notificationService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public V3PendingEmailChallenge createRegistration(
            String schoolId,
            V3TeacherRegistrationRequest request,
            String email,
            String contactNumber,
            V3RequestMetadata metadata,
            Instant now
    ) {
        long addressId = repository.insertAddress(request.address());
        long userId = repository.insertTeacher(
                schoolId,
                addressId,
                repository.findRoleId("teacher"),
                repository.findStatusId(PENDING_EMAIL_STATUS),
                request,
                email,
                contactNumber,
                passwordEncoder.encode(request.password()),
                now
        );

        V3PendingEmailChallenge challenge = createChallenge(
                userId,
                addressId,
                email,
                0,
                now,
                now.plus(properties.getRegistrationRetention())
        );
        auditService.record(
                userId,
                "REGISTER_TEACHER",
                "users",
                Long.toString(userId),
                "success",
                metadata,
                Map.of(
                        "accountStatus", PENDING_EMAIL_STATUS,
                        "verificationMethod", "email",
                        "challengeUuid", challenge.challengeUuid()
                ),
                now
        );
        return challenge;
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3PendingEmailChallenge prepareResend(
            String challengeUuid,
            V3RequestMetadata metadata,
            Instant now
    ) {
        V3VerificationChallenge current = repository.findChallengeForUpdate(challengeUuid)
                .orElseThrow(this::invalidChallenge);
        ensureRegistrationUsable(current, now);

        if ("verified".equals(current.challengeStatus()) || current.emailVerified()) {
            throw new V3AuthException(
                    "EMAIL_ALREADY_VERIFIED",
                    "The email address is already verified.",
                    HttpStatus.CONFLICT
            );
        }
        if ("cancelled".equals(current.challengeStatus())) {
            throw invalidChallenge();
        }
        if (current.nextResendAt() != null && now.isBefore(current.nextResendAt())) {
            throw new V3AuthException(
                    "VERIFICATION_RESEND_COOLDOWN",
                    "Please wait before requesting another verification code.",
                    HttpStatus.TOO_MANY_REQUESTS,
                    Map.of("resendAvailableAt", current.nextResendAt())
            );
        }

        int messagesToday = repository.countVerificationMessagesSince(
                current.userId(),
                now.minusSeconds(24 * 60 * 60)
        );
        if (messagesToday >= properties.getMaxVerificationMessagesPerDay()) {
            throw new V3AuthException(
                    "VERIFICATION_DAILY_LIMIT_REACHED",
                    "The daily verification-message limit has been reached.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        repository.cancelChallenge(current.challengeId(), now);
        V3PendingEmailChallenge replacement = createChallenge(
                current.userId(),
                current.addressId(),
                current.email(),
                current.resendCount() + 1,
                now,
                current.userCreatedAt().plus(properties.getRegistrationRetention())
        );
        auditService.record(
                current.userId(),
                "RESEND_EMAIL_VERIFICATION",
                "verification_challenges",
                replacement.challengeUuid(),
                "success",
                metadata,
                Map.of("resendCount", replacement.resendCount()),
                now
        );
        return replacement;
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3VerificationChallenge verify(
            String challengeUuid,
            String otp,
            V3RequestMetadata metadata,
            Instant now
    ) {
        V3VerificationChallenge challenge = repository.findChallengeForUpdate(challengeUuid)
                .orElseThrow(this::invalidChallenge);
        ensureRegistrationUsable(challenge, now);

        if (challenge.emailVerified() || "verified".equals(challenge.challengeStatus())) {
            throw new V3AuthException(
                    "EMAIL_ALREADY_VERIFIED",
                    "The email address is already verified.",
                    HttpStatus.CONFLICT
            );
        }
        if (!"pending".equals(challenge.challengeStatus())) {
            throw invalidChallenge();
        }
        if (!now.isBefore(challenge.expiresAt())) {
            repository.markChallengeExpired(challenge.challengeId(), now);
            throw new V3AuthException(
                    "VERIFICATION_CODE_EXPIRED",
                    "The verification code has expired. Request a new code.",
                    HttpStatus.GONE,
                    Map.of("resendAvailableAt", challenge.nextResendAt())
            );
        }

        if (!passwordEncoder.matches(otp, challenge.codeHash())) {
            int nextAttempt = challenge.attemptCount() + 1;
            boolean locked = nextAttempt >= challenge.maximumAttemptCount();
            repository.recordInvalidAttempt(challenge.challengeId(), nextAttempt, locked, now);
            auditService.record(
                    challenge.userId(),
                    "VERIFY_TEACHER_EMAIL",
                    "verification_challenges",
                    challenge.challengeUuid(),
                    "failed",
                    metadata,
                    Map.of("reason", locked ? "maximum_attempts" : "invalid_code"),
                    now
            );
            throw new V3AuthException(
                    locked ? "VERIFICATION_CHALLENGE_LOCKED" : "INVALID_VERIFICATION_CODE",
                    locked
                            ? "Too many incorrect codes. Request a new verification code."
                            : "The verification code is invalid.",
                    locked ? HttpStatus.LOCKED : HttpStatus.BAD_REQUEST,
                    Map.of("remainingAttempts", Math.max(0, challenge.maximumAttemptCount() - nextAttempt))
            );
        }

        int changed = repository.markUserEmailVerified(
                challenge.userId(),
                repository.findStatusId(PENDING_EMAIL_STATUS),
                repository.findStatusId(PENDING_APPROVAL_STATUS),
                now
        );
        if (changed != 1) {
            throw new V3AuthException(
                    "ACCOUNT_STATE_CHANGED",
                    "The registration status changed. Please restart verification.",
                    HttpStatus.CONFLICT
            );
        }
        repository.markChallengeVerified(challenge.challengeId(), now);
        repository.cancelOtherChallenges(challenge.userId(), challenge.challengeId(), now);
        auditService.record(
                challenge.userId(),
                "VERIFY_TEACHER_EMAIL",
                "verification_challenges",
                challenge.challengeUuid(),
                "success",
                metadata,
                Map.of("accountStatus", PENDING_APPROVAL_STATUS),
                now
        );
        notificationService.notifyTeacherPendingApproval(challenge.userId(), now);
        return challenge;
    }

    @Transactional
    public void markDeliverySent(V3PendingEmailChallenge challenge, String provider, Instant now) {
        repository.markDeliverySent(challenge.challengeId(), now, provider);
        auditService.record(
                challenge.userId(),
                "SEND_EMAIL_VERIFICATION",
                "verification_challenges",
                challenge.challengeUuid(),
                "success",
                null,
                Map.of("provider", provider),
                now
        );
    }

    @Transactional
    public void markDeliveryFailed(V3PendingEmailChallenge challenge, String provider, Instant now) {
        repository.markDeliveryFailed(challenge.challengeId(), now, provider);
        auditService.record(
                challenge.userId(),
                "SEND_EMAIL_VERIFICATION",
                "verification_challenges",
                challenge.challengeUuid(),
                "failed",
                null,
                Map.of("provider", provider),
                now
        );
    }

    public List<Long> findExpiredProvisionalUserIds(Instant cutoff) {
        return repository.findExpiredProvisionalUserIds(cutoff, properties.getCleanupBatchSize());
    }

    @Transactional
    public void purgeExpiredProvisionalIdentity(
            String email,
            String contactNumber,
            Instant cutoff,
            Instant now
    ) {
        List<Long> userIds = repository.findExpiredProvisionalUserIdsByIdentity(
                email,
                contactNumber,
                cutoff
        );
        for (Long userId : userIds) {
            purgeExpiredProvisionalUser(userId, cutoff, now);
        }
    }

    @Transactional
    public boolean purgeExpiredProvisionalUser(long userId, Instant cutoff, Instant now) {
        V3ProvisionalUser user = repository.findProvisionalUserForUpdate(userId).orElse(null);
        if (user == null || user.createdAt().isAfter(cutoff)) {
            return false;
        }

        String replacement = "deleted-v3-" + UUID.randomUUID().toString().replace("-", "") + "@invalid.local";
        repository.anonymizeLoginAttempts(user.userId(), user.email(), replacement);
        auditService.record(
                user.userId(),
                "PURGE_UNVERIFIED_REGISTRATION",
                "users",
                Long.toString(user.userId()),
                "success",
                null,
                Map.of("reason", "email_not_verified_within_retention_period"),
                now
        );
        int deleted = repository.deleteExpiredProvisionalUser(user.userId(), cutoff);
        if (deleted == 1) {
            repository.deleteAddressIfOrphan(user.addressId());
            return true;
        }
        return false;
    }

    private V3PendingEmailChallenge createChallenge(
            long userId,
            long addressId,
            String email,
            int resendCount,
            Instant now,
            Instant registrationExpiresAt
    ) {
        String otp = "%06d".formatted(secureRandom.nextInt(1_000_000));
        String challengeUuid = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(properties.getOtpTtl());
        Instant resendAt = now.plus(properties.getResendCooldown());
        long challengeId = repository.insertChallenge(
                challengeUuid,
                userId,
                V3VerificationEmailSender.maskEmail(email),
                passwordEncoder.encode(otp),
                properties.getMaxOtpAttempts(),
                resendCount,
                resendAt,
                expiresAt,
                now
        );
        return new V3PendingEmailChallenge(
                challengeId,
                challengeUuid,
                userId,
                addressId,
                email,
                V3VerificationEmailSender.maskEmail(email),
                otp,
                resendCount,
                expiresAt,
                resendAt,
                registrationExpiresAt
        );
    }

    private void ensureRegistrationUsable(V3VerificationChallenge challenge, Instant now) {
        Instant registrationExpiresAt = challenge.userCreatedAt().plus(properties.getRegistrationRetention());
        if (!now.isBefore(registrationExpiresAt)) {
            purgeExpiredProvisionalUser(
                    challenge.userId(),
                    now.minus(properties.getRegistrationRetention()),
                    now
            );
            throw new V3AuthException(
                    "REGISTRATION_EXPIRED",
                    "The unverified registration expired. Submit a new registration.",
                    HttpStatus.GONE
            );
        }
        if (!PENDING_EMAIL_STATUS.equals(challenge.accountStatus())) {
            throw new V3AuthException(
                    "ACCOUNT_STATE_CHANGED",
                    "The registration is no longer awaiting email verification.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private V3AuthException invalidChallenge() {
        return new V3AuthException(
                "INVALID_VERIFICATION_CHALLENGE",
                "The verification challenge is invalid.",
                HttpStatus.BAD_REQUEST
        );
    }
}
