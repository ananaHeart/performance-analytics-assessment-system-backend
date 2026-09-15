package com.capstone.assessment.v2.auth.service;

import com.capstone.assessment.v2.auth.config.V2EmailVerificationProperties;
import com.capstone.assessment.v2.auth.dto.V2EmailVerificationResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2EmailVerificationOtp;
import com.capstone.assessment.v2.auth.model.V2EmailVerificationUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.capstone.assessment.v2.auth.repository.V2EmailVerificationRepository;
import com.capstone.assessment.v2.notification.service.V2NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Profile("v2")
@Service
public class V2EmailVerificationService {

    private final V2EmailVerificationRepository repository;
    private final V2AuthRepository authRepository;
    private final V2NotificationService notificationService;
    private final V2VerificationEmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final V2EmailVerificationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public V2EmailVerificationService(
            V2EmailVerificationRepository repository,
            V2AuthRepository authRepository,
            V2NotificationService notificationService,
            V2VerificationEmailSender emailSender,
            PasswordEncoder passwordEncoder,
            V2EmailVerificationProperties properties,
            ObjectMapper objectMapper
    ) {
        this(repository, authRepository, notificationService, emailSender, passwordEncoder,
                properties, objectMapper, Clock.systemUTC(), new SecureRandom());
    }

    V2EmailVerificationService(
            V2EmailVerificationRepository repository,
            V2AuthRepository authRepository,
            V2NotificationService notificationService,
            V2VerificationEmailSender emailSender,
            PasswordEncoder passwordEncoder,
            V2EmailVerificationProperties properties,
            ObjectMapper objectMapper,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.repository = repository;
        this.authRepository = authRepository;
        this.notificationService = notificationService;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Transactional
    public V2EmailVerificationResponse issueInitialCode(long userId, String email, V2RequestMetadata metadata) {
        return issueCode(userId, normalizeEmail(email), metadata, false);
    }

    @Transactional
    public V2EmailVerificationResponse resendCode(String email, V2RequestMetadata metadata) {
        V2EmailVerificationUser user = requirePendingUnverifiedUser(email);
        V2EmailVerificationOtp latest = repository.findLatestCodeForUpdate(user.userId()).orElse(null);
        Instant now = clock.instant();
        if (latest != null && latest.usedAt() == null && now.isBefore(latest.resendAvailableAt())) {
            throw new V2AuthException(
                    "VERIFICATION_RESEND_COOLDOWN",
                    "Please wait before requesting another verification code.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        return issueCode(user.userId(), user.email(), metadata, true);
    }

    @Transactional
    public V2EmailVerificationResponse verify(String email, String otp, V2RequestMetadata metadata) {
        V2EmailVerificationUser user = requirePendingUnverifiedUser(email);
        V2EmailVerificationOtp code = repository.findLatestCodeForUpdate(user.userId())
                .orElseThrow(() -> badRequest("VERIFICATION_CODE_REQUIRED", "Request a verification code first."));
        Instant now = clock.instant();
        if (code.usedAt() != null) {
            throw badRequest("VERIFICATION_CODE_USED", "This verification code has already been used.");
        }
        if (!now.isBefore(code.expiresAt())) {
            repository.markUsed(code.otpId(), now);
            throw badRequest("VERIFICATION_CODE_EXPIRED", "The verification code has expired.");
        }
        if (code.attemptCount() >= code.maxAttempts()) {
            throw new V2AuthException(
                    "VERIFICATION_ATTEMPTS_EXCEEDED",
                    "Too many incorrect attempts. Request a new verification code.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        if (!passwordEncoder.matches(otp, code.otpHash())) {
            repository.incrementAttempts(code.otpId());
            throw badRequest("INVALID_VERIFICATION_CODE", "The verification code is incorrect.");
        }
        repository.markUsed(code.otpId(), now);
        if (repository.markEmailVerified(user.userId(), now) != 1) {
            throw new V2AuthException("EMAIL_VERIFICATION_STATE_CHANGED", "Email verification state changed.", HttpStatus.CONFLICT);
        }
        recordAudit(user, metadata, "VERIFY_TEACHER_EMAIL", now, Map.of("emailVerified", true));
        notificationService.notifySchoolPrincipals(
                user.schoolId(),
                "teacher_registration_pending",
                "Teacher approval pending",
                user.firstName() + " " + user.lastName() + " verified their email and is ready for approval.",
                "users",
                String.valueOf(user.userId()),
                "teacher-registration:" + user.userId(),
                now
        );
        return new V2EmailVerificationResponse(user.email(), true, "pending", null, null);
    }

    private V2EmailVerificationResponse issueCode(
            long userId,
            String email,
            V2RequestMetadata metadata,
            boolean resend
    ) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getOtpTtl());
        Instant resendAvailableAt = now.plus(properties.getResendCooldown());
        String otp = "%06d".formatted(secureRandom.nextInt(1_000_000));
        repository.invalidateUnusedCodes(userId, now);
        repository.insertCode(
                userId,
                passwordEncoder.encode(otp),
                properties.getMaxAttempts(),
                expiresAt,
                resendAvailableAt,
                now
        );
        emailSender.sendVerificationCode(email, otp);
        V2EmailVerificationUser user = repository.findTeacherByEmail(email)
                .orElse(new V2EmailVerificationUser(userId, null, null, null, email, "pending", false));
        recordAudit(user, metadata, resend ? "RESEND_TEACHER_EMAIL_OTP" : "SEND_TEACHER_EMAIL_OTP", now,
                Map.of("expiresAt", expiresAt.toString()));
        return new V2EmailVerificationResponse(email, false, "pending", expiresAt, resendAvailableAt);
    }

    private V2EmailVerificationUser requirePendingUnverifiedUser(String email) {
        V2EmailVerificationUser user = repository.findTeacherByEmail(normalizeEmail(email))
                .orElseThrow(() -> badRequest("VERIFICATION_NOT_AVAILABLE", "Email verification is not available for this account."));
        if (user.emailVerified() || !"pending".equalsIgnoreCase(user.status())) {
            throw badRequest("VERIFICATION_NOT_AVAILABLE", "Email verification is not available for this account.");
        }
        return user;
    }

    private void recordAudit(
            V2EmailVerificationUser user,
            V2RequestMetadata metadata,
            String action,
            Instant now,
            Map<String, ?> details
    ) {
        V2RequestMetadata safe = metadata == null ? new V2RequestMetadata(null, null, null) : metadata;
        authRepository.recordAudit(
                UUID.randomUUID().toString(), user.userId(), action, "users", String.valueOf(user.userId()),
                "success", safe.ipAddress(), safe.deviceIdentifier(), safe.userAgent(), writeJson(details), now
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String writeJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize email verification audit details.", exception);
        }
    }

    private V2AuthException badRequest(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.BAD_REQUEST);
    }
}
