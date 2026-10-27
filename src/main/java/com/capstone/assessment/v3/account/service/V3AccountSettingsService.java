package com.capstone.assessment.v3.account.service;

import com.capstone.assessment.v3.account.dto.V3AccountCodeResponse;
import com.capstone.assessment.v3.account.dto.V3AccountProfileRequest;
import com.capstone.assessment.v3.account.dto.V3AccountResponse;
import com.capstone.assessment.v3.account.dto.V3AccountVerificationRequest;
import com.capstone.assessment.v3.account.dto.V3ContactChangeRequest;
import com.capstone.assessment.v3.account.dto.V3EmailChangeRequest;
import com.capstone.assessment.v3.account.dto.V3PasswordChangeRequest;
import com.capstone.assessment.v3.account.dto.V3PasswordResetRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherSubjectsRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherSubjectsResponse;
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
import com.capstone.assessment.v3.auth.service.V3EmailCodePurpose;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.auth.service.V3SmsVerificationSender;
import com.capstone.assessment.v3.auth.service.V3TeacherRegistrationService;
import com.capstone.assessment.v3.auth.service.V3TeacherSubjects;
import com.capstone.assessment.v3.auth.service.V3VerificationEmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The signed-in user's own account (principal or teacher).
 * SET-1: change email or contact number, verified by a code sent to the NEW address; the first
 * change is free, then once every 6 months per field (counted from the audit log).
 * SET-3: correct one's own name. SET-6: change password. VER-1: verify the channel not chosen
 * at registration. Codes reuse the registration senders, so SMS works only where SMS is on.
 */
@Profile("v3")
@Service
public class V3AccountSettingsService {

    static final String EMAIL_CHANGE_ACTION = "account.email_change";
    static final String CONTACT_CHANGE_ACTION = "account.contact_change";
    private static final int CHANGE_INTERVAL_MONTHS = 6;
    private static final ZoneId SCHOOL_TIMEZONE = ZoneId.of("Asia/Manila");

    /** Each flow: its purpose, channel and the prefix that ties the code to the value it confirms. */
    private enum Flow {
        EMAIL_CHANGE("sensitive_action", "email", "email-change|"),
        CONTACT_CHANGE("sensitive_action", "sms", "contact-change|"),
        VERIFY_EMAIL("email_verification", "email", "verify-email|"),
        VERIFY_SMS("contact_verification", "sms", "verify-sms|"),
        RESET_EMAIL("sensitive_action", "email", "password-reset|"),
        RESET_SMS("sensitive_action", "sms", "password-reset|");

        final String purpose;
        final String channel;
        final String binding;

        Flow(String purpose, String channel, String binding) {
            this.purpose = purpose;
            this.channel = channel;
            this.binding = binding;
        }
    }

    private final V3AccountSettingsRepository repository;
    private final V3RegistrationRepository registrationRepository;
    private final V3AuthRepository authRepository;
    private final V3AuditService auditService;
    private final V3VerificationEmailSender emailSender;
    private final V3SmsVerificationSender smsSender;
    private final PasswordEncoder passwordEncoder;
    private final V3AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public V3AccountSettingsService(
            V3AccountSettingsRepository repository,
            V3RegistrationRepository registrationRepository,
            V3AuthRepository authRepository,
            V3AuditService auditService,
            V3VerificationEmailSender emailSender,
            V3SmsVerificationSender smsSender,
            PasswordEncoder passwordEncoder,
            V3AuthProperties properties
    ) {
        this(repository, registrationRepository, authRepository, auditService, emailSender, smsSender,
                passwordEncoder, properties, Clock.systemUTC());
    }

    V3AccountSettingsService(
            V3AccountSettingsRepository repository,
            V3RegistrationRepository registrationRepository,
            V3AuthRepository authRepository,
            V3AuditService auditService,
            V3VerificationEmailSender emailSender,
            V3SmsVerificationSender smsSender,
            PasswordEncoder passwordEncoder,
            V3AuthProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.authRepository = authRepository;
        this.auditService = auditService;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public V3AccountResponse getAccount(V3AuthenticatedUser user) {
        return toResponse(requireAccount(user));
    }

    /** SET-3: correct one's own name and suffix. */
    @Transactional
    public V3AccountResponse updateProfile(V3AuthenticatedUser user, V3AccountProfileRequest request,
                                           V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        if (request.suffixId() != null && !registrationRepository.suffixExists(request.suffixId())) {
            throw fieldError("suffixId", "The selected suffix is unavailable.");
        }
        Instant now = clock.instant();
        String middleName = request.middleName() == null || request.middleName().isBlank()
                ? null : request.middleName().trim();
        repository.updateName(account.userId(), request.firstName().trim(), middleName, request.lastName().trim(),
                request.suffixId(), now);
        auditService.record(account.userId(), "account.profile_update", "users", Long.toString(account.userId()),
                "success", metadata, Map.of("fields", "name"), now);
        return toResponse(requireAccount(user));
    }

    /** SET-6: change password; every other session ends, this one stays signed in. */
    @Transactional
    public Instant changePassword(V3AuthenticatedUser user, V3PasswordChangeRequest request,
                                  V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        if (!passwordEncoder.matches(request.currentPassword(), account.passwordHash())) {
            throw new V3FieldValidationException("Password change failed.", HttpStatus.BAD_REQUEST,
                    Map.of("code", "CURRENT_PASSWORD_INCORRECT", "currentPassword", "The current password is incorrect."));
        }
        if (!V3TeacherRegistrationService.isStrongPassword(request.newPassword())) {
            throw fieldError("newPassword",
                    "Password must have at least 10 characters, uppercase, lowercase, number, and special character.");
        }
        if (passwordEncoder.matches(request.newPassword(), account.passwordHash())) {
            throw new V3FieldValidationException("Password change failed.", HttpStatus.BAD_REQUEST,
                    Map.of("code", "PASSWORD_UNCHANGED", "newPassword", "Choose a password different from the current one."));
        }
        Instant now = clock.instant();
        repository.updatePassword(account.userId(), passwordEncoder.encode(request.newPassword()), now);
        authRepository.revokeOtherSessions(account.userId(), user.sessionUuid(), now);
        auditService.record(account.userId(), "account.password_change", "users", Long.toString(account.userId()),
                "success", metadata, Map.of("otherSessionsEnded", true), now);
        return now;
    }

    /** SET-1: send a code to the new email; it is saved only after the code is confirmed. */
    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountCodeResponse startEmailChange(V3AuthenticatedUser user, V3EmailChangeRequest request,
                                                  V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        String newEmail = validNewEmail(account, request.newEmail());
        requireChangeAllowed(account.userId(), EMAIL_CHANGE_ACTION);
        return sendCode(account, Flow.EMAIL_CHANGE, newEmail, 0, metadata);
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountCodeResponse resendEmailChange(V3AuthenticatedUser user, V3EmailChangeRequest request,
                                                   V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        String newEmail = validNewEmail(account, request.newEmail());
        return resend(account, Flow.EMAIL_CHANGE, request.challengeUuid(), newEmail, metadata);
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountResponse confirmEmailChange(V3AuthenticatedUser user, V3EmailChangeRequest request,
                                                V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        String newEmail = validNewEmail(account, request.newEmail());
        requireChangeAllowed(account.userId(), EMAIL_CHANGE_ACTION);
        Instant now = clock.instant();
        confirmCode(account, Flow.EMAIL_CHANGE, request.challengeUuid(), request.code(), newEmail, now);
        repository.updateEmail(account.userId(), newEmail, now);
        auditService.record(account.userId(), EMAIL_CHANGE_ACTION, "users", Long.toString(account.userId()),
                "success", metadata, Map.of("previousEmail", V3VerificationEmailSender.maskEmail(account.email()),
                        "newEmail", V3VerificationEmailSender.maskEmail(newEmail)), now);
        return toResponse(requireAccount(user));
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountCodeResponse startContactChange(V3AuthenticatedUser user, V3ContactChangeRequest request,
                                                    V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        String newContact = validNewContact(account, request.newContactNumber());
        requireChangeAllowed(account.userId(), CONTACT_CHANGE_ACTION);
        return sendCode(account, Flow.CONTACT_CHANGE, newContact, 0, metadata);
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountCodeResponse resendContactChange(V3AuthenticatedUser user, V3ContactChangeRequest request,
                                                     V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        String newContact = validNewContact(account, request.newContactNumber());
        return resend(account, Flow.CONTACT_CHANGE, request.challengeUuid(), newContact, metadata);
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountResponse confirmContactChange(V3AuthenticatedUser user, V3ContactChangeRequest request,
                                                  V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        String newContact = validNewContact(account, request.newContactNumber());
        requireChangeAllowed(account.userId(), CONTACT_CHANGE_ACTION);
        Instant now = clock.instant();
        confirmCode(account, Flow.CONTACT_CHANGE, request.challengeUuid(), request.code(), newContact, now);
        repository.updateContact(account.userId(), newContact, now);
        auditService.record(account.userId(), CONTACT_CHANGE_ACTION, "users", Long.toString(account.userId()),
                "success", metadata, Map.of("previousContact", maskPhone(account.contactNumber()),
                        "newContact", maskPhone(newContact)), now);
        return toResponse(requireAccount(user));
    }

    /** VER-1: send a code to the user's current email or number that is not verified yet. */
    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountCodeResponse startVerification(V3AuthenticatedUser user, V3AccountVerificationRequest request,
                                                   V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        Flow flow = verificationFlow(request.channel());
        return sendCode(account, flow, verificationTarget(account, flow), 0, metadata);
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountCodeResponse resendVerification(V3AuthenticatedUser user, V3AccountVerificationRequest request,
                                                    V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        ChallengeRow previous = requireChallenge(account, request.challengeUuid());
        Flow flow = "sms".equals(previous.channel()) ? Flow.VERIFY_SMS : Flow.VERIFY_EMAIL;
        return resend(account, flow, request.challengeUuid(), verificationTarget(account, flow), metadata);
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountResponse confirmVerification(V3AuthenticatedUser user, V3AccountVerificationRequest request,
                                                 V3RequestMetadata metadata) {
        AccountRow account = requireAccount(user);
        ChallengeRow challenge = requireChallenge(account, request.challengeUuid());
        Flow flow = "sms".equals(challenge.channel()) ? Flow.VERIFY_SMS : Flow.VERIFY_EMAIL;
        String target = verificationTarget(account, flow);
        Instant now = clock.instant();
        confirmCode(account, flow, request.challengeUuid(), request.code(), target, now);
        repository.markVerified(account.userId(), flow.channel, now);
        auditService.record(account.userId(), flow == Flow.VERIFY_SMS ? "account.verify_contact" : "account.verify_email",
                "users", Long.toString(account.userId()), "success", metadata, Map.of("channel", flow.channel), now);
        return toResponse(requireAccount(user));
    }

    /**
     * TCH-7: forgot password. The answer looks the same whether or not the account exists, so the
     * form cannot be used to find out who is registered.
     */
    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountCodeResponse requestPasswordReset(V3PasswordResetRequest request, V3RequestMetadata metadata) {
        String identifier = request.identifier() == null ? "" : request.identifier().trim();
        boolean bySms = !identifier.contains("@");
        String destination = bySms
                ? V3TeacherRegistrationService.toE164Contact(identifier)
                : identifier.toLowerCase(Locale.ROOT);
        if (destination == null || (!bySms && !V3TeacherRegistrationService.isValidEmailAddress(destination))) {
            throw fieldError("identifier", "Enter your email or mobile number (09XXXXXXXXX).");
        }
        Flow flow = bySms ? Flow.RESET_SMS : Flow.RESET_EMAIL;
        if (bySms && !smsSender.isEnabled()) {
            throw new V3AuthException("SMS_NOT_AVAILABLE", "SMS codes are not available. Use your email instead.",
                    HttpStatus.CONFLICT);
        }
        Optional<Long> userId = repository.findResettableUserId(flow.channel, destination);
        if (userId.isEmpty()) {
            return lookalikeCode(flow.channel, bySms ? maskPhone(destination) : V3VerificationEmailSender.maskEmail(destination));
        }
        AccountRow account = repository.lockAccount(userId.get()).orElseThrow(this::invalidChallenge);
        return sendCode(account, flow, destination, 0, metadata);
    }

    @Transactional(noRollbackFor = V3AuthException.class)
    public V3AccountCodeResponse resendPasswordReset(V3PasswordResetRequest request, V3RequestMetadata metadata) {
        String challengeUuid = request.challengeUuid() == null ? "" : request.challengeUuid().trim();
        Optional<Long> owner = repository.findChallengeOwner(challengeUuid);
        if (owner.isEmpty()) {
            return lookalikeCode("email", "your registered address");
        }
        AccountRow account = repository.lockAccount(owner.get()).orElseThrow(this::invalidChallenge);
        ChallengeRow previous = requireChallenge(account, challengeUuid);
        Flow flow = "sms".equals(previous.channel()) ? Flow.RESET_SMS : Flow.RESET_EMAIL;
        return resend(account, flow, challengeUuid, resetDestination(account, flow), metadata);
    }

    /** TCH-7: a correct code sets the new password and ends every session. */
    @Transactional(noRollbackFor = V3AuthException.class)
    public Instant confirmPasswordReset(V3PasswordResetRequest request, V3RequestMetadata metadata) {
        if (!V3TeacherRegistrationService.isStrongPassword(request.newPassword())) {
            throw fieldError("newPassword",
                    "Password must have at least 10 characters, uppercase, lowercase, number, and special character.");
        }
        String challengeUuid = request.challengeUuid() == null ? "" : request.challengeUuid().trim();
        long owner = repository.findChallengeOwner(challengeUuid).orElseThrow(this::invalidChallenge);
        AccountRow account = repository.lockAccount(owner).orElseThrow(this::invalidChallenge);
        ChallengeRow challenge = requireChallenge(account, challengeUuid);
        Flow flow = "sms".equals(challenge.channel()) ? Flow.RESET_SMS : Flow.RESET_EMAIL;
        Instant now = clock.instant();
        confirmCode(account, flow, challengeUuid, request.code(), resetDestination(account, flow), now);
        repository.updatePassword(account.userId(), passwordEncoder.encode(request.newPassword()), now);
        repository.endSessionsAndClearLock(account.userId(), now);
        auditService.record(account.userId(), "account.password_reset", "users", Long.toString(account.userId()),
                "success", metadata, Map.of("channel", flow.channel, "sessionsEnded", true), now);
        return now;
    }

    private String resetDestination(AccountRow account, Flow flow) {
        return flow == Flow.RESET_SMS ? account.contactNumber() : account.email();
    }

    /** The same shape as a real code, for an address that has no resettable account. */
    private static V3EmailCodePurpose emailPurpose(Flow flow) {
        return switch (flow) {
            case EMAIL_CHANGE -> V3EmailCodePurpose.EMAIL_CHANGE;
            case RESET_EMAIL, RESET_SMS -> V3EmailCodePurpose.PASSWORD_RESET;
            default -> V3EmailCodePurpose.VERIFY_EMAIL;
        };
    }

    private V3AccountCodeResponse lookalikeCode(String channel, String masked) {
        Instant now = clock.instant();
        return new V3AccountCodeResponse(UUID.randomUUID().toString(), channel, masked,
                now.plus(properties.getOtpTtl()), now.plus(properties.getResendCooldown()));
    }

    /** REG-3: the teacher's own additional majors and subjects taught before. */
    @Transactional(readOnly = true)
    public V3TeacherSubjectsResponse getSubjects(V3AuthenticatedUser user) {
        requireTeacher(user);
        return toSubjectsResponse(registrationRepository.listTeacherSubjects(user.userId()));
    }

    @Transactional
    public V3TeacherSubjectsResponse updateSubjects(V3AuthenticatedUser user, V3TeacherSubjectsRequest request,
                                                    V3RequestMetadata metadata) {
        requireTeacher(user);
        AccountRow account = requireAccount(user);
        List<Integer> additional = V3TeacherSubjects.clean(request.additionalMajorSubjectIds(),
                repository.findMajorId(account.userId()));
        List<Integer> taught = V3TeacherSubjects.clean(request.taughtSubjectIds(), null);
        Map<String, Object> errors = new LinkedHashMap<>();
        V3TeacherSubjects.validate(additional, "additionalMajorSubjectIds", registrationRepository, errors);
        V3TeacherSubjects.validate(taught, "taughtSubjectIds", registrationRepository, errors);
        if (!errors.isEmpty()) {
            errors.put("code", "VALIDATION_FAILED");
            throw new V3FieldValidationException("Validation failed.", HttpStatus.BAD_REQUEST, errors);
        }
        registrationRepository.replaceTeacherSubjects(account.userId(), V3TeacherSubjects.ADDITIONAL_MAJOR, additional);
        registrationRepository.replaceTeacherSubjects(account.userId(), V3TeacherSubjects.TAUGHT_BEFORE, taught);
        auditService.record(account.userId(), "account.subjects_update", "users", Long.toString(account.userId()),
                "success", metadata, Map.of("additionalMajorSubjectIds", additional, "taughtSubjectIds", taught),
                clock.instant());
        return getSubjects(user);
    }

    /** REG-3: shared by the teacher's own view and the principal's teacher details. */
    public static V3TeacherSubjectsResponse toSubjectsResponse(List<V3RegistrationRepository.TeacherSubject> rows) {
        return new V3TeacherSubjectsResponse(
                rows.stream().filter(row -> V3TeacherSubjects.ADDITIONAL_MAJOR.equals(row.relation()))
                        .map(row -> new V3TeacherSubjectsResponse.Subject(row.subjectId(), row.subjectName())).toList(),
                rows.stream().filter(row -> V3TeacherSubjects.TAUGHT_BEFORE.equals(row.relation()))
                        .map(row -> new V3TeacherSubjectsResponse.Subject(row.subjectId(), row.subjectName())).toList()
        );
    }

    private static void requireTeacher(V3AuthenticatedUser user) {
        if (user == null || !"teacher".equalsIgnoreCase(user.role())) {
            throw new V3AuthException("TEACHER_ROLE_REQUIRED", "Only a teacher has majors and subjects taught.",
                    HttpStatus.FORBIDDEN);
        }
    }

    // ------------------------------------------------------------------ codes

    private V3AccountCodeResponse sendCode(AccountRow account, Flow flow, String destination, int resendCount,
                                           V3RequestMetadata metadata) {
        if ("sms".equals(flow.channel) && !smsSender.isEnabled()) {
            throw new V3AuthException("SMS_NOT_AVAILABLE", "SMS codes are not available. Use email instead.",
                    HttpStatus.CONFLICT);
        }
        Instant now = clock.instant();
        if (repository.countAccountCodesSince(account.userId(), now.minusSeconds(24 * 60 * 60))
                >= properties.getMaxVerificationMessagesPerDay()) {
            throw new V3AuthException("VERIFICATION_DAILY_LIMIT_REACHED",
                    "The daily verification-message limit has been reached.", HttpStatus.TOO_MANY_REQUESTS);
        }
        String otp = "%06d".formatted(secureRandom.nextInt(1_000_000));
        String challengeUuid = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(properties.getOtpTtl());
        Instant resendAt = now.plus(properties.getResendCooldown());
        String masked = "sms".equals(flow.channel) ? maskPhone(destination) : V3VerificationEmailSender.maskEmail(destination);
        long challengeId = repository.insertChallenge(challengeUuid, account.userId(), flow.purpose, flow.channel,
                masked, passwordEncoder.encode(flow.binding + otp + "|" + destination),
                properties.getMaxOtpAttempts(), resendCount, resendAt, expiresAt, now);
        try {
            String provider = "sms".equals(flow.channel)
                    ? smsSender.send(destination, otp)
                    : emailSender.sendVerificationCode(destination, otp, emailPurpose(flow));
            registrationRepository.markDeliverySent(challengeId, now, provider);
        } catch (V3SmsVerificationSender.SmsRateLimitedException exception) {
            registrationRepository.markDeliveryFailed(challengeId, now, smsSender.provider());
            throw new V3AuthException("SMS_RATE_LIMITED", "Too many SMS codes were requested. Wait a minute and resend.",
                    HttpStatus.TOO_MANY_REQUESTS, Map.of("challengeUuid", challengeUuid));
        } catch (V3SmsVerificationSender.SmsUnavailableException exception) {
            registrationRepository.markDeliveryFailed(challengeId, now, smsSender.provider());
            throw new V3AuthException("SMS_DELIVERY_FAILED", "The SMS code could not be sent. Try again.",
                    HttpStatus.SERVICE_UNAVAILABLE, Map.of("challengeUuid", challengeUuid));
        } catch (V3AuthException exception) {
            registrationRepository.markDeliveryFailed(challengeId, now, "smtp");
            throw exception;
        }
        auditService.record(account.userId(), "account.code_sent", "verification_challenges", challengeUuid,
                "success", metadata, Map.of("purpose", flow.purpose, "channel", flow.channel), now);
        return new V3AccountCodeResponse(challengeUuid, flow.channel, masked, expiresAt, resendAt);
    }

    /** VER-2: a resend sends a new code with a new challengeUuid; the old code stops working. */
    private V3AccountCodeResponse resend(AccountRow account, Flow flow, String challengeUuid, String destination,
                                         V3RequestMetadata metadata) {
        ChallengeRow previous = requireChallenge(account, challengeUuid);
        if (!flow.purpose.equals(previous.purpose()) || !flow.channel.equals(previous.channel())) {
            throw invalidChallenge();
        }
        if ("cancelled".equals(previous.status())) {
            throw codeReplaced();
        }
        Instant now = clock.instant();
        if (!"failed".equals(previous.deliveryStatus())
                && previous.nextResendAt() != null && now.isBefore(previous.nextResendAt())) {
            throw new V3AuthException("VERIFICATION_RESEND_COOLDOWN",
                    "Please wait before requesting another verification code.", HttpStatus.TOO_MANY_REQUESTS,
                    Map.of("resendAvailableAt", previous.nextResendAt()));
        }
        registrationRepository.cancelChallenge(previous.challengeId(), now);
        return sendCode(account, flow, destination, previous.resendCount() + 1, metadata);
    }

    private void confirmCode(AccountRow account, Flow flow, String challengeUuid, String code, String destination,
                             Instant now) {
        ChallengeRow challenge = requireChallenge(account, challengeUuid);
        if (!flow.purpose.equals(challenge.purpose()) || !flow.channel.equals(challenge.channel())) {
            throw invalidChallenge();
        }
        if ("cancelled".equals(challenge.status())) {
            throw codeReplaced();
        }
        if (!"pending".equals(challenge.status())) {
            throw invalidChallenge();
        }
        if (!now.isBefore(challenge.expiresAt())) {
            registrationRepository.markChallengeExpired(challenge.challengeId(), now);
            throw new V3AuthException("VERIFICATION_CODE_EXPIRED", "The verification code has expired. Request a new code.",
                    HttpStatus.GONE);
        }
        String candidate = code == null ? "" : code.trim();
        if (!passwordEncoder.matches(flow.binding + candidate + "|" + destination, challenge.codeHash())) {
            int nextAttempt = challenge.attemptCount() + 1;
            boolean locked = nextAttempt >= challenge.maximumAttemptCount();
            registrationRepository.recordInvalidAttempt(challenge.challengeId(), nextAttempt, locked, now);
            throw new V3AuthException(
                    locked ? "VERIFICATION_CHALLENGE_LOCKED" : "INVALID_VERIFICATION_CODE",
                    locked ? "Too many incorrect codes. Request a new verification code." : "The verification code is invalid.",
                    locked ? HttpStatus.LOCKED : HttpStatus.BAD_REQUEST,
                    Map.of("remainingAttempts", Math.max(0, challenge.maximumAttemptCount() - nextAttempt)));
        }
        registrationRepository.markChallengeVerified(challenge.challengeId(), now);
    }

    private ChallengeRow requireChallenge(AccountRow account, String challengeUuid) {
        if (challengeUuid == null || challengeUuid.isBlank()) {
            throw fieldError("challengeUuid", "The challengeUuid is required.");
        }
        return repository.lockChallenge(challengeUuid.trim(), account.userId()).orElseThrow(this::invalidChallenge);
    }

    // ------------------------------------------------------------------ rules

    /** SET-1: the first change is free; after that, once every 6 months per field. */
    private void requireChangeAllowed(long userId, String auditAction) {
        Instant next = nextChangeAt(userId, auditAction);
        if (next != null) {
            throw new V3AuthException("CHANGE_NOT_ALLOWED_YET",
                    "This can be changed again on " + next.atZone(SCHOOL_TIMEZONE).toLocalDate() + ".",
                    HttpStatus.CONFLICT, Map.of("nextAllowedAt", next));
        }
    }

    private Instant nextChangeAt(long userId, String auditAction) {
        return repository.findLastChange(userId, auditAction)
                .map(last -> last.atZone(SCHOOL_TIMEZONE).plusMonths(CHANGE_INTERVAL_MONTHS).toInstant())
                .filter(next -> next.isAfter(clock.instant()))
                .orElse(null);
    }

    private String validNewEmail(AccountRow account, String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!V3TeacherRegistrationService.isValidEmailAddress(email)) {
            throw fieldError("newEmail", "Enter a complete valid email address.");
        }
        if (email.equalsIgnoreCase(account.email())) {
            throw fieldError("newEmail", "This is already your email address.");
        }
        if (repository.emailUsedByAnother(email, account.userId())) {
            throw fieldError("newEmail", "This email address is already registered.");
        }
        return email;
    }

    private String validNewContact(AccountRow account, String value) {
        String contact = V3TeacherRegistrationService.toE164Contact(value);
        if (contact == null) {
            throw fieldError("newContactNumber", "Contact number must use 09XXXXXXXXX or +639XXXXXXXXX format.");
        }
        if (contact.equals(account.contactNumber())) {
            throw fieldError("newContactNumber", "This is already your contact number.");
        }
        if (repository.contactUsedByAnother(contact, account.userId())) {
            throw fieldError("newContactNumber", "This contact number is already registered.");
        }
        return contact;
    }

    private Flow verificationFlow(String channel) {
        if (channel == null) {
            throw fieldError("channel", "Choose email or sms.");
        }
        return "sms".equalsIgnoreCase(channel.trim()) ? Flow.VERIFY_SMS : Flow.VERIFY_EMAIL;
    }

    private String verificationTarget(AccountRow account, Flow flow) {
        boolean sms = flow == Flow.VERIFY_SMS;
        if (sms ? account.contactVerifiedAt() != null : account.emailVerifiedAt() != null) {
            throw new V3AuthException("ALREADY_VERIFIED",
                    sms ? "The contact number is already verified." : "The email address is already verified.",
                    HttpStatus.CONFLICT);
        }
        String target = sms ? account.contactNumber() : account.email();
        if (target == null || target.isBlank()) {
            throw fieldError("channel", sms ? "There is no contact number to verify." : "There is no email to verify.");
        }
        return target;
    }

    private AccountRow requireAccount(V3AuthenticatedUser user) {
        if (user == null) {
            throw new V3AuthException("AUTHENTICATION_REQUIRED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        return repository.lockAccount(user.userId())
                .orElseThrow(() -> new V3AuthException("AUTHENTICATION_REQUIRED", "Authentication is required.",
                        HttpStatus.UNAUTHORIZED));
    }

    private V3AccountResponse toResponse(AccountRow account) {
        return new V3AccountResponse(
                account.userId(),
                account.role(),
                account.firstName(),
                account.middleName(),
                account.lastName(),
                account.suffixId(),
                account.email(),
                account.emailVerifiedAt() != null,
                account.contactNumber(),
                account.contactVerifiedAt() != null,
                nextChangeAt(account.userId(), EMAIL_CHANGE_ACTION),
                nextChangeAt(account.userId(), CONTACT_CHANGE_ACTION),
                account.passwordChangedAt()
        );
    }

    private static String maskPhone(String phone) {
        return phone == null ? null : V3SmsVerificationSender.maskPhone(phone);
    }

    private static V3FieldValidationException fieldError(String field, String message) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", "VALIDATION_FAILED");
        errors.put(field, message);
        return new V3FieldValidationException("Validation failed.", HttpStatus.BAD_REQUEST, errors);
    }

    private V3AuthException invalidChallenge() {
        return new V3AuthException("INVALID_VERIFICATION_CHALLENGE", "The verification challenge is invalid.",
                HttpStatus.BAD_REQUEST);
    }

    private V3AuthException codeReplaced() {
        return new V3AuthException("VERIFICATION_CODE_REPLACED",
                "A newer verification code was sent. Enter the latest code.", HttpStatus.CONFLICT);
    }
}
