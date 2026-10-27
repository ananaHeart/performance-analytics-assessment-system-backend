package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.dto.V3TeacherRegistrationRequest;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3PendingRegistrationChallenge;
import com.capstone.assessment.v3.auth.model.V3ProvisionalUser;
import com.capstone.assessment.v3.auth.model.V3VerificationChallenge;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Teacher registration verification: one code, sent by the channel the teacher chose (email or
 * SMS). Entering it verifies that channel's address and moves the account to pending approval.
 */
@Profile("v3")
@Service
public class V3RegistrationPersistenceService {

    // Internal status name for "awaiting verification", by email or SMS.
    static final String PENDING_EMAIL_STATUS = "pending_email_verification";
    static final String PENDING_APPROVAL_STATUS = "pending_approval";

    private final V3RegistrationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final V3AuthProperties properties;
    private final V3AuditService auditService;
    private final V3NotificationService notificationService;
    private final V3SmsVerificationSender smsSender;
    private final SecureRandom secureRandom = new SecureRandom();

    V3RegistrationPersistenceService(
            V3RegistrationRepository repository,
            PasswordEncoder passwordEncoder,
            V3AuthProperties properties,
            V3AuditService auditService,
            V3NotificationService notificationService
    ) {
        this(repository, passwordEncoder, properties, auditService, notificationService,
                new V3SmsVerificationSender(properties));
    }

    @Autowired
    public V3RegistrationPersistenceService(
            V3RegistrationRepository repository,
            PasswordEncoder passwordEncoder,
            V3AuthProperties properties,
            V3AuditService auditService,
            V3NotificationService notificationService,
            V3SmsVerificationSender smsSender
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.smsSender = smsSender;
    }

    /** Creates the provisional teacher and its registration code for {@code verificationMethod}. */
    @Transactional
    public V3PendingRegistrationChallenge createRegistration(
            String schoolId,
            V3TeacherRegistrationRequest request,
            String email,
            String contactNumber,
            String verificationMethod,
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
        repository.replaceTeacherSubjects(userId, V3TeacherSubjects.ADDITIONAL_MAJOR,
                V3TeacherSubjects.clean(request.additionalMajorSubjectIds(), request.majorId()));
        repository.replaceTeacherSubjects(userId, V3TeacherSubjects.TAUGHT_BEFORE,
                V3TeacherSubjects.clean(request.taughtSubjectIds(), null));

        V3PendingRegistrationChallenge challenge = createChallenge(
                userId,
                addressId,
                verificationMethod,
                email,
                contactNumber,
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
                        "verificationMethod", verificationMethod,
                        "challengeUuid", challenge.challengeUuid()
                ),
                now
        );
        return challenge;
    }

    /**
     * A new code for the registration. {@code requestedMethod} ("email" or "sms", optional) moves it
     * to the other channel, e.g. when the SMS can't get through; a failed send needs no cooldown.
     */
    @Transactional(noRollbackFor = V3AuthException.class)
    public V3PendingRegistrationChallenge prepareResend(
            String challengeUuid,
            String requestedMethod,
            V3RequestMetadata metadata,
            Instant now
    ) {
        V3VerificationChallenge current = requireRegistrationChallenge(challengeUuid);
        ensureRegistrationUsable(current, now);

        if ("verified".equals(current.challengeStatus()) || current.emailVerified() || current.contactVerified()) {
            throw alreadyVerified();
        }
        if ("cancelled".equals(current.challengeStatus())) {
            throw codeReplaced();
        }
        if (!"failed".equals(current.deliveryStatus())
                && current.nextResendAt() != null && now.isBefore(current.nextResendAt())) {
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

        String method = requestedMethod == null || requestedMethod.isBlank()
                ? current.deliveryChannel()
                : requestedMethod.trim().toLowerCase(Locale.ROOT);
        if ("sms".equals(method) && !smsSender.isEnabled()) {
            throw new V3AuthException(
                    "SMS_NOT_AVAILABLE",
                    "SMS verification is not available. Use email instead.",
                    HttpStatus.CONFLICT
            );
        }

        repository.cancelChallenge(current.challengeId(), now);
        V3PendingRegistrationChallenge replacement = createChallenge(
                current.userId(),
                current.addressId(),
                method,
                current.email(),
                current.contactNumber(),
                current.resendCount() + 1,
                now,
                current.userCreatedAt().plus(properties.getRegistrationRetention())
        );
        auditService.record(
                current.userId(),
                replacement.bySms() ? "RESEND_SMS_VERIFICATION" : "RESEND_EMAIL_VERIFICATION",
                "verification_challenges",
                replacement.challengeUuid(),
                "success",
                metadata,
                Map.of("resendCount", replacement.resendCount(), "verificationMethod", method),
                now
        );
        return replacement;
    }

    /** Confirms the registration code; the email or contact number it was sent to becomes verified. */
    @Transactional(noRollbackFor = V3AuthException.class)
    public V3VerificationChallenge verify(
            String challengeUuid,
            String otp,
            V3RequestMetadata metadata,
            Instant now
    ) {
        V3VerificationChallenge challenge = requireRegistrationChallenge(challengeUuid);
        ensureRegistrationUsable(challenge, now);
        String action = challenge.bySms() ? "VERIFY_TEACHER_CONTACT" : "VERIFY_TEACHER_EMAIL";

        if (challenge.emailVerified() || challenge.contactVerified()
                || "verified".equals(challenge.challengeStatus())) {
            throw alreadyVerified();
        }
        if ("cancelled".equals(challenge.challengeStatus())) {
            throw codeReplaced();
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
                    action,
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

        int pendingStatusId = repository.findStatusId(PENDING_EMAIL_STATUS);
        int approvalStatusId = repository.findStatusId(PENDING_APPROVAL_STATUS);
        int changed = challenge.bySms()
                ? repository.markUserContactVerified(challenge.userId(), pendingStatusId, approvalStatusId, now)
                : repository.markUserEmailVerified(challenge.userId(), pendingStatusId, approvalStatusId, now);
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
                action,
                "verification_challenges",
                challenge.challengeUuid(),
                "success",
                metadata,
                Map.of("accountStatus", PENDING_APPROVAL_STATUS, "verificationMethod", challenge.deliveryChannel()),
                now
        );
        notificationService.notifyTeacherPendingApproval(challenge.userId(), now);
        return challenge;
    }

    @Transactional
    public void markDeliverySent(V3PendingRegistrationChallenge challenge, String provider, Instant now) {
        repository.markDeliverySent(challenge.challengeId(), now, provider);
        auditDelivery(challenge, "success", provider, now);
    }

    @Transactional
    public void markDeliveryFailed(V3PendingRegistrationChallenge challenge, String provider, Instant now) {
        repository.markDeliveryFailed(challenge.challengeId(), now, provider);
        auditDelivery(challenge, "failed", provider, now);
    }

    private void auditDelivery(V3PendingRegistrationChallenge challenge, String outcome, String provider, Instant now) {
        auditService.record(
                challenge.userId(),
                challenge.bySms() ? "SEND_SMS_VERIFICATION" : "SEND_EMAIL_VERIFICATION",
                "verification_challenges",
                challenge.challengeUuid(),
                outcome,
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
                Map.of("reason", "not_verified_within_retention_period"),
                now
        );
        int deleted = repository.deleteExpiredProvisionalUser(user.userId(), cutoff);
        if (deleted == 1) {
            repository.deleteAddressIfOrphan(user.addressId());
            return true;
        }
        return false;
    }

    private V3PendingRegistrationChallenge createChallenge(
            long userId,
            long addressId,
            String method,
            String email,
            String contactNumber,
            int resendCount,
            Instant now,
            Instant registrationExpiresAt
    ) {
        String otp = "%06d".formatted(secureRandom.nextInt(1_000_000));
        String challengeUuid = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(properties.getOtpTtl());
        Instant resendAt = now.plus(properties.getResendCooldown());
        String masked = "sms".equals(method)
                ? V3SmsVerificationSender.maskPhone(contactNumber)
                : V3VerificationEmailSender.maskEmail(email);
        long challengeId = repository.insertChallenge(
                challengeUuid,
                userId,
                method,
                masked,
                passwordEncoder.encode(otp),
                properties.getMaxOtpAttempts(),
                resendCount,
                resendAt,
                expiresAt,
                now
        );
        return new V3PendingRegistrationChallenge(
                challengeId,
                challengeUuid,
                userId,
                addressId,
                method,
                email,
                contactNumber,
                masked,
                otp,
                resendCount,
                expiresAt,
                resendAt,
                registrationExpiresAt
        );
    }

    private V3VerificationChallenge requireRegistrationChallenge(String challengeUuid) {
        V3VerificationChallenge challenge = repository.findChallengeForUpdate(challengeUuid)
                .orElseThrow(this::invalidChallenge);
        if (challenge.verificationPurpose() != null
                && !"teacher_registration".equals(challenge.verificationPurpose())) {
            throw invalidChallenge();
        }
        return challenge;
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
                    "The registration is no longer awaiting verification.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private V3AuthException alreadyVerified() {
        return new V3AuthException(
                "EMAIL_ALREADY_VERIFIED",
                "This registration is already verified.",
                HttpStatus.CONFLICT
        );
    }

    /** VER-2: only the newest code works; an older one was cancelled when a new code was sent. */
    private V3AuthException codeReplaced() {
        return new V3AuthException(
                "VERIFICATION_CODE_REPLACED",
                "A newer verification code was sent. Enter the latest code.",
                HttpStatus.CONFLICT
        );
    }

    private V3AuthException invalidChallenge() {
        return new V3AuthException(
                "INVALID_VERIFICATION_CHALLENGE",
                "The verification challenge is invalid.",
                HttpStatus.BAD_REQUEST
        );
    }
}
