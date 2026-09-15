package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.dto.V3EmailVerificationResponse;
import com.capstone.assessment.v3.auth.dto.V3RegistrationReferenceDataResponse;
import com.capstone.assessment.v3.auth.dto.V3RegistrationResponse;
import com.capstone.assessment.v3.auth.dto.V3ResendVerificationRequest;
import com.capstone.assessment.v3.auth.dto.V3TeacherRegistrationRequest;
import com.capstone.assessment.v3.auth.dto.V3VerifyEmailRequest;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3PendingEmailChallenge;
import com.capstone.assessment.v3.auth.model.V3VerificationChallenge;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Profile("v3")
@Service
public class V3TeacherRegistrationService {

    private static final int MINIMUM_TEACHER_AGE = 18;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._%+-]*@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}$"
    );
    private static final Pattern LOCAL_CONTACT_PATTERN = Pattern.compile("^09\\d{9}$");
    private static final Pattern INTERNATIONAL_CONTACT_PATTERN = Pattern.compile("^\\+639\\d{9}$");
    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{10,128}$"
    );

    private final V3RegistrationRepository repository;
    private final V3RegistrationPersistenceService persistenceService;
    private final V3VerificationEmailSender emailSender;
    private final V3AuthProperties properties;
    private final Clock clock;

    @Autowired
    public V3TeacherRegistrationService(
            V3RegistrationRepository repository,
            V3RegistrationPersistenceService persistenceService,
            V3VerificationEmailSender emailSender,
            V3AuthProperties properties
    ) {
        this(repository, persistenceService, emailSender, properties, Clock.systemUTC());
    }

    V3TeacherRegistrationService(
            V3RegistrationRepository repository,
            V3RegistrationPersistenceService persistenceService,
            V3VerificationEmailSender emailSender,
            V3AuthProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.persistenceService = persistenceService;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    public V3RegistrationReferenceDataResponse getReferenceData() {
        return new V3RegistrationReferenceDataResponse(
                repository.listGenders(),
                repository.listActiveSuffixes(),
                repository.listMajors(),
                repository.listActiveEducationalAttainments(),
                repository.listSchools(),
                List.of(
                        new V3RegistrationReferenceDataResponse.VerificationMethodOption(
                                "email", "Email", true, null),
                        new V3RegistrationReferenceDataResponse.VerificationMethodOption(
                                "sms", "Text message (SMS)", false,
                                "SMS verification is not available yet.")
                ),
                new V3RegistrationReferenceDataResponse.VerificationPolicy(
                        properties.getOtpTtl().toSeconds(),
                        properties.getResendCooldown().toSeconds(),
                        properties.getMaxOtpAttempts(),
                        properties.getRegistrationRetention().toDays()
                )
        );
    }

    public V3RegistrationResponse register(
            V3TeacherRegistrationRequest request,
            V3RequestMetadata metadata
    ) {
        Instant now = clock.instant();
        NormalizedRegistration normalized = validateAndNormalize(request, now);
        V3PendingEmailChallenge challenge;
        try {
            challenge = persistenceService.createRegistration(
                    normalized.schoolId(),
                    request,
                    normalized.email(),
                    normalized.contactNumber(),
                    metadata,
                    now
            );
        } catch (DataIntegrityViolationException exception) {
            throw duplicateAccountData();
        }
        deliver(challenge, now);
        return registrationResponse(challenge, "sent");
    }

    public V3EmailVerificationResponse resend(
            V3ResendVerificationRequest request,
            V3RequestMetadata metadata
    ) {
        Instant now = clock.instant();
        V3PendingEmailChallenge challenge = persistenceService.prepareResend(
                request.challengeUuid().trim(),
                metadata,
                now
        );
        deliver(challenge, now);
        return pendingVerificationResponse(challenge, "sent");
    }

    public V3EmailVerificationResponse verify(
            V3VerifyEmailRequest request,
            V3RequestMetadata metadata
    ) {
        V3VerificationChallenge challenge = persistenceService.verify(
                request.challengeUuid().trim(),
                request.otp(),
                metadata,
                clock.instant()
        );
        return new V3EmailVerificationResponse(
                V3RegistrationPersistenceService.PENDING_APPROVAL_STATUS,
                true,
                challenge.emailMasked(),
                challenge.challengeUuid(),
                challenge.deliveryStatus(),
                challenge.expiresAt(),
                challenge.nextResendAt(),
                challenge.userCreatedAt().plus(properties.getRegistrationRetention())
        );
    }

    private NormalizedRegistration validateAndNormalize(V3TeacherRegistrationRequest request, Instant now) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", "VALIDATION_FAILED");

        String method = normalize(request.verificationMethod()).toLowerCase(Locale.ROOT);
        if (!"email".equals(method)) {
            errors.put("verificationMethod", "Email is the only available verification method.");
        }

        String schoolCode = normalize(request.schoolCode());
        String schoolId = repository.findSchoolId(schoolCode).orElse(null);
        if (schoolId == null) {
            errors.put("schoolCode", "The selected school code is not registered.");
        }

        String email = normalize(request.email()).toLowerCase(Locale.ROOT);
        boolean validEmail = isValidEmail(email);
        if (!validEmail) {
            errors.put("email", "Enter a complete valid email address.");
        }

        String contact = normalizeContact(request.contactNumber(), errors);
        if (validEmail || contact != null) {
            persistenceService.purgeExpiredProvisionalIdentity(
                    validEmail ? email : null,
                    contact,
                    now.minus(properties.getRegistrationRetention()),
                    now
            );
        }
        if (validEmail && repository.emailExists(email)) {
            errors.put("email", "This email address is already registered.");
        }
        if (contact != null && repository.contactExists(contact)) {
            errors.put("contactNumber", "This contact number is already registered.");
        }

        validateDates(request, now, errors);
        if (!STRONG_PASSWORD_PATTERN.matcher(request.password()).matches()) {
            errors.put(
                    "password",
                    "Password must have at least 10 characters, uppercase, lowercase, number, and special character."
            );
        }
        if (!repository.genderExists(request.genderId())) {
            errors.put("genderId", "The selected gender does not exist.");
        }
        if (!repository.majorExists(request.majorId())) {
            errors.put("majorId", "The selected teacher major does not exist.");
        }
        if (request.suffixId() != null && !repository.suffixExists(request.suffixId())) {
            errors.put("suffixId", "The selected suffix is unavailable.");
        }
        if (!repository.educationalAttainmentExists(request.educationalAttainmentId())) {
            errors.put("educationalAttainmentId", "The selected educational attainment is unavailable.");
        }

        if (errors.size() > 1) {
            throw new V3FieldValidationException("Validation failed.", HttpStatus.BAD_REQUEST, errors);
        }
        return new NormalizedRegistration(schoolId, email, contact);
    }

    private void validateDates(
            V3TeacherRegistrationRequest request,
            Instant now,
            Map<String, Object> errors
    ) {
        LocalDate today = LocalDate.now(clock);
        if (request.birthDate() == null
                || !request.birthDate().isBefore(today)
                || request.birthDate().plusYears(MINIMUM_TEACHER_AGE).isAfter(today)) {
            errors.put("birthDate", "Teacher must be at least 18 years old and birth date must be in the past.");
        }

        if (request.teachingStartMonth() == null || request.teachingStartYear() == null) {
            errors.put("teachingStartMonth", "Teaching start month and year are required.");
            errors.put("teachingStartYear", "Teaching start month and year are required.");
            return;
        }

        YearMonth teachingStart = YearMonth.of(request.teachingStartYear(), request.teachingStartMonth());
        YearMonth currentMonth = YearMonth.from(LocalDate.ofInstant(now, clock.getZone()));
        if (teachingStart.isAfter(currentMonth)) {
            errors.put("teachingStartYear", "Teaching start month and year cannot be in the future.");
        }
        if (request.birthDate() != null) {
            YearMonth minimumStart = YearMonth.from(request.birthDate().plusYears(MINIMUM_TEACHER_AGE));
            if (teachingStart.isBefore(minimumStart)) {
                errors.put("teachingStartYear", "Teaching start must be on or after the teacher turned 18.");
            }
        }
    }

    private String normalizeContact(String value, Map<String, Object> errors) {
        String normalized = normalize(value);
        if (LOCAL_CONTACT_PATTERN.matcher(normalized).matches()) {
            return "+63" + normalized.substring(1);
        }
        if (INTERNATIONAL_CONTACT_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }
        errors.put("contactNumber", "Contact number must use 09XXXXXXXXX or +639XXXXXXXXX format.");
        return null;
    }

    private boolean isValidEmail(String value) {
        return EMAIL_PATTERN.matcher(value).matches()
                && !value.contains("..")
                && !value.startsWith(".")
                && !value.endsWith(".");
    }

    private void deliver(V3PendingEmailChallenge challenge, Instant now) {
        try {
            String provider = emailSender.sendVerificationCode(challenge.recipientEmail(), challenge.rawOtp());
            persistenceService.markDeliverySent(challenge, provider, now);
        } catch (V3AuthException exception) {
            persistenceService.markDeliveryFailed(challenge, "smtp", now);
            Map<String, Object> details = challengeDetails(challenge, "failed");
            details.put("accountCreated", true);
            throw new V3AuthException(exception.getCode(), exception.getMessage(), exception.getStatus(), details);
        }
    }

    private V3RegistrationResponse registrationResponse(V3PendingEmailChallenge challenge, String deliveryStatus) {
        return new V3RegistrationResponse(
                V3RegistrationPersistenceService.PENDING_EMAIL_STATUS,
                "email",
                challenge.emailMasked(),
                challenge.challengeUuid(),
                deliveryStatus,
                challenge.otpExpiresAt(),
                challenge.resendAvailableAt(),
                challenge.registrationExpiresAt()
        );
    }

    private V3EmailVerificationResponse pendingVerificationResponse(
            V3PendingEmailChallenge challenge,
            String deliveryStatus
    ) {
        return new V3EmailVerificationResponse(
                V3RegistrationPersistenceService.PENDING_EMAIL_STATUS,
                false,
                challenge.emailMasked(),
                challenge.challengeUuid(),
                deliveryStatus,
                challenge.otpExpiresAt(),
                challenge.resendAvailableAt(),
                challenge.registrationExpiresAt()
        );
    }

    private Map<String, Object> challengeDetails(V3PendingEmailChallenge challenge, String deliveryStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("accountStatus", V3RegistrationPersistenceService.PENDING_EMAIL_STATUS);
        details.put("verificationMethod", "email");
        details.put("emailMasked", challenge.emailMasked());
        details.put("challengeUuid", challenge.challengeUuid());
        details.put("deliveryStatus", deliveryStatus);
        details.put("otpExpiresAt", challenge.otpExpiresAt());
        details.put("resendAvailableAt", challenge.resendAvailableAt());
        details.put("registrationExpiresAt", challenge.registrationExpiresAt());
        return details;
    }

    private V3FieldValidationException duplicateAccountData() {
        return new V3FieldValidationException(
                "The email address or contact number is already registered.",
                HttpStatus.CONFLICT,
                Map.of(
                        "code", "DUPLICATE_ACCOUNT_DATA",
                        "email", "The email address may already be registered.",
                        "contactNumber", "The contact number may already be registered."
                )
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record NormalizedRegistration(String schoolId, String email, String contactNumber) {
    }
}
