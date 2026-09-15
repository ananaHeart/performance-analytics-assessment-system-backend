package com.capstone.assessment.v2.account.service;

import com.capstone.assessment.v2.account.dto.V2CreateTeacherRequest;
import com.capstone.assessment.v2.account.dto.V2RegistrationVerificationMethodOption;
import com.capstone.assessment.v2.account.dto.V2TeacherAccountResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherReferenceDataResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherRegistrationReferenceDataResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherRegistrationRequest;
import com.capstone.assessment.v2.account.model.V2TeacherAccount;
import com.capstone.assessment.v2.account.repository.V2TeacherAccountRepository;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.exception.V2FieldValidationException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
import com.capstone.assessment.v2.auth.service.V2EmailVerificationService;
import com.capstone.assessment.v2.notification.service.V2NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Profile("v2")
@Service
public class V2TeacherAccountService {

    private static final String TEACHER_ROLE = "teacher";
    private static final String PRINCIPAL_ROLE = "principal";
    private static final String PENDING_STATUS = "pending";
    private static final String EMAIL_VERIFICATION_METHOD = "email";
    private static final int MIN_TEACHER_AGE_YEARS = 18;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._%+-]*@[A-Za-z0-9][A-Za-z0-9.-]*\\.[A-Za-z]{2,}$"
    );
    private static final Pattern PH_MOBILE_LOCAL_PATTERN = Pattern.compile("^09\\d{9}$");
    private static final Pattern PH_MOBILE_INTERNATIONAL_PATTERN = Pattern.compile("^\\+639\\d{9}$");
    private static final Set<String> LISTABLE_STATUSES = Set.of(
            "pending", "active", "rejected", "inactive", "locked"
    );

    private final V2TeacherAccountRepository accountRepository;
    private final V2AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final V2NotificationService notificationService;
    private final V2EmailVerificationService emailVerificationService;
    private final Clock clock;

    @Autowired
    public V2TeacherAccountService(
            V2TeacherAccountRepository accountRepository,
            V2AuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            V2NotificationService notificationService,
            V2EmailVerificationService emailVerificationService
    ) {
        this(accountRepository, authRepository, passwordEncoder, objectMapper,
                notificationService, emailVerificationService, Clock.systemUTC());
    }

    V2TeacherAccountService(
            V2TeacherAccountRepository accountRepository,
            V2AuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(accountRepository, authRepository, passwordEncoder, objectMapper, null, null, clock);
    }

    V2TeacherAccountService(
            V2TeacherAccountRepository accountRepository,
            V2AuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            V2NotificationService notificationService,
            Clock clock
    ) {
        this(accountRepository, authRepository, passwordEncoder, objectMapper,
                notificationService, null, clock);
    }

    V2TeacherAccountService(
            V2TeacherAccountRepository accountRepository,
            V2AuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            V2NotificationService notificationService,
            V2EmailVerificationService emailVerificationService,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.emailVerificationService = emailVerificationService;
        this.clock = clock;
    }

    @Transactional
    public V2TeacherAccountResponse createTeacher(
            V2AuthenticatedUser principal,
            V2CreateTeacherRequest request,
            V2RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String email = normalizeEmail(request.email(), false);
        String contact = normalizeContact(request.contactNumber(), false);
        validateTeacherDates(request);
        validatePassword(request.temporaryPassword());
        validateReferences(request);
        V2CreateTeacherRequest normalizedRequest = requestWithResolvedSuffix(request);

        if (accountRepository.emailExists(email)) {
            throw conflict("DUPLICATE_EMAIL", "A user with this email already exists.");
        }
        if (accountRepository.contactExists(contact)) {
            throw conflict("DUPLICATE_CONTACT", "A user with this contact number already exists.");
        }

        Instant now = clock.instant();
        try {
            long addressId = accountRepository.insertAddress(request.address());
            long userId = accountRepository.insertTeacher(
                    principal.schoolId(),
                    addressId,
                    accountRepository.findRoleId(TEACHER_ROLE),
                    accountRepository.findStatusId(PENDING_STATUS),
                    normalizedRequest,
                    email,
                    contact,
                    passwordEncoder.encode(request.temporaryPassword()),
                    now
            );
            recordAudit(
                    principal,
                    metadata,
                    "CREATE_TEACHER_ACCOUNT",
                    userId,
                    "success",
                    Map.of("status", PENDING_STATUS)
            );
            if (emailVerificationService != null) {
                emailVerificationService.issueInitialCode(userId, email, metadata);
            } else {
                notifyPendingTeacher(principal.schoolId(), userId, normalizedRequest, now);
            }
            return toResponse(requiredTeacher(principal.schoolId(), userId));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("DUPLICATE_ACCOUNT_DATA", "The teacher email or contact number is already registered.");
        }
    }

    @Transactional(readOnly = true)
    public V2TeacherReferenceDataResponse getReferenceData(V2AuthenticatedUser principal) {
        requirePrincipal(principal);
        return new V2TeacherReferenceDataResponse(
                accountRepository.listGenders(),
                accountRepository.listActiveSuffixes(),
                accountRepository.listMajors(),
                accountRepository.listActiveEducationalAttainments()
        );
    }

    @Transactional(readOnly = true)
    public V2TeacherRegistrationReferenceDataResponse getPublicRegistrationReferenceData() {
        return new V2TeacherRegistrationReferenceDataResponse(
                accountRepository.listGenders(),
                accountRepository.listActiveSuffixes(),
                accountRepository.listMajors(),
                accountRepository.listActiveEducationalAttainments(),
                accountRepository.listSchools(),
                List.of(
                        new V2RegistrationVerificationMethodOption(
                                EMAIL_VERIFICATION_METHOD,
                                "Email",
                                true,
                                null
                        ),
                        new V2RegistrationVerificationMethodOption(
                                "sms",
                                "Text message (SMS)",
                                false,
                                "SMS verification is not available yet."
                        )
                )
        );
    }

    @Transactional
    public V2TeacherAccountResponse registerTeacher(
            V2TeacherRegistrationRequest request,
            V2RequestMetadata metadata
    ) {
        String verificationMethod = normalizeVerificationMethod(request.verificationMethod());
        String email = normalizeEmail(request.email(), true);
        String contact = normalizeContact(request.contactNumber(), true);
        String schoolCode = normalizeSchoolCode(request.schoolCode());
        V2CreateTeacherRequest teacherRequest = toCreateTeacherRequest(request);
        validateTeacherDates(teacherRequest);

        String schoolId = accountRepository.findSchoolIdByCode(schoolCode)
                .orElseThrow(() -> fieldValidation(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_SCHOOL_CODE",
                        "Validation failed.",
                        "schoolCode",
                        "The selected school code is not registered."
                ));

        validatePublicPassword(request.password());
        validateReferences(teacherRequest, true);
        V2CreateTeacherRequest normalizedTeacherRequest = requestWithResolvedSuffix(teacherRequest);
        validatePublicUniqueness(email, contact);

        Instant now = clock.instant();
        try {
            long addressId = accountRepository.insertAddress(request.address());
            long userId = accountRepository.insertTeacher(
                    schoolId,
                    addressId,
                    accountRepository.findRoleId(TEACHER_ROLE),
                    accountRepository.findStatusId(PENDING_STATUS),
                    normalizedTeacherRequest,
                    email,
                    contact,
                    passwordEncoder.encode(request.password()),
                    now
            );
            recordPublicRegistrationAudit(metadata, userId, schoolId, verificationMethod);
            if (emailVerificationService != null) {
                emailVerificationService.issueInitialCode(userId, email, metadata);
            }
            return toResponse(requiredTeacher(schoolId, userId));
        } catch (DataIntegrityViolationException exception) {
            throw fieldValidation(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_ACCOUNT_DATA",
                    "A user with this email or contact number already exists.",
                    "email",
                    "Email may already be registered.",
                    "contactNumber",
                    "Contact number may already be registered."
            );
        }
    }

    @Transactional(readOnly = true)
    public List<V2TeacherAccountResponse> listTeachers(V2AuthenticatedUser principal, String requestedStatus) {
        requirePrincipal(principal);
        String status = normalizeOptionalStatus(requestedStatus);
        return accountRepository.findTeachers(principal.schoolId(), status).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public V2TeacherAccountResponse approveTeacher(
            V2AuthenticatedUser principal,
            long teacherUserId,
            V2RequestMetadata metadata
    ) {
        return transition(principal, teacherUserId, "active", "APPROVE_TEACHER_ACCOUNT", metadata);
    }

    @Transactional
    public V2TeacherAccountResponse rejectTeacher(
            V2AuthenticatedUser principal,
            long teacherUserId,
            V2RequestMetadata metadata
    ) {
        return transition(principal, teacherUserId, "rejected", "REJECT_TEACHER_ACCOUNT", metadata);
    }

    private V2TeacherAccountResponse transition(
            V2AuthenticatedUser principal,
            long teacherUserId,
            String targetStatus,
            String action,
            V2RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        V2TeacherAccount existing = requiredTeacher(principal.schoolId(), teacherUserId);
        if (!PENDING_STATUS.equals(existing.status())) {
            throw conflict(
                    "INVALID_ACCOUNT_TRANSITION",
                    "Only pending teacher accounts can be approved or rejected."
            );
        }
        if (!existing.emailVerified()) {
            throw conflict(
                    "EMAIL_VERIFICATION_REQUIRED",
                    "The teacher must verify their email before principal approval."
            );
        }

        int updated = accountRepository.transitionStatus(
                principal.schoolId(),
                teacherUserId,
                accountRepository.findRoleId(TEACHER_ROLE),
                accountRepository.findStatusId(PENDING_STATUS),
                accountRepository.findStatusId(targetStatus)
        );
        if (updated != 1) {
            throw conflict("ACCOUNT_STATE_CHANGED", "The teacher account status changed before this request completed.");
        }
        recordAudit(
                principal,
                metadata,
                action,
                teacherUserId,
                "success",
                Map.of("previousStatus", PENDING_STATUS, "newStatus", targetStatus)
        );
        return toResponse(requiredTeacher(principal.schoolId(), teacherUserId));
    }

    private void validateReferences(V2CreateTeacherRequest request) {
        validateReferences(request, false);
    }

    private void validateReferences(V2CreateTeacherRequest request, boolean fieldErrors) {
        if (!accountRepository.genderExists(request.genderId())) {
            if (fieldErrors) {
                throw fieldValidation(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_GENDER",
                        "Validation failed.",
                        "genderId",
                        "The selected gender does not exist."
                );
            }
            throw badRequest("INVALID_GENDER", "The selected gender does not exist.");
        }
        if (!accountRepository.majorExists(request.majorId())) {
            if (fieldErrors) {
                throw fieldValidation(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_MAJOR",
                        "Validation failed.",
                        "majorId",
                        "The selected teacher major does not exist."
                );
            }
            throw badRequest("INVALID_MAJOR", "The selected teacher major does not exist.");
        }
        if (request.suffixId() != null && !accountRepository.suffixExists(request.suffixId())) {
            if (fieldErrors) {
                throw fieldValidation(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_SUFFIX",
                        "Validation failed.",
                        "suffixId",
                        "The selected suffix is unavailable."
                );
            }
            throw badRequest("INVALID_SUFFIX", "The selected suffix is unavailable.");
        }
        if (!accountRepository.educationalAttainmentExists(request.educationalAttainmentId())) {
            if (fieldErrors) {
                throw fieldValidation(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_EDUCATIONAL_ATTAINMENT",
                        "Validation failed.",
                        "educationalAttainmentId",
                        "The selected educational attainment is unavailable."
                );
            }
            throw badRequest("INVALID_EDUCATIONAL_ATTAINMENT", "The selected educational attainment is unavailable.");
        }
    }

    private String normalizeContact(String value, boolean fieldErrors) {
        String normalized = value == null ? "" : value.trim();
        if (PH_MOBILE_LOCAL_PATTERN.matcher(normalized).matches()) {
            return "+63" + normalized.substring(1);
        }
        if (PH_MOBILE_INTERNATIONAL_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }
        if (fieldErrors) {
            throw fieldValidation(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CONTACT_NUMBER",
                    "Validation failed.",
                    "contactNumber",
                    "Contact number must use 09XXXXXXXXX or +639XXXXXXXXX format."
            );
        }
        throw badRequest(
                "INVALID_CONTACT_NUMBER",
                "Contact number must use 09XXXXXXXXX or +639XXXXXXXXX format."
        );
    }

    private String normalizeEmail(String value, boolean fieldErrors) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.startsWith(".")
                || normalized.endsWith(".")) {
            if (fieldErrors) {
                throw fieldValidation(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_EMAIL",
                        "Validation failed.",
                        "email",
                        "Email must be a complete address such as teachername123@gmail.com."
                );
            }
            throw badRequest(
                    "INVALID_EMAIL",
                    "Email must be a complete address such as teachername123@gmail.com."
            );
        }
        return normalized;
    }

    private void validatePassword(String password) {
        boolean strong = password != null
                && password.length() >= 10
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(character -> !Character.isLetterOrDigit(character));
        if (!strong) {
            throw badRequest(
                    "WEAK_PASSWORD",
                    "Temporary password must include uppercase, lowercase, number, and special characters."
            );
        }
    }

    private void validatePublicPassword(String password) {
        boolean strong = password != null
                && password.length() >= 10
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(character -> !Character.isLetterOrDigit(character));
        if (!strong) {
            throw fieldValidation(
                    HttpStatus.BAD_REQUEST,
                    "WEAK_PASSWORD",
                    "Validation failed.",
                    "password",
                    "Password must be at least 10 characters and include uppercase, lowercase, number, and special characters."
            );
        }
    }

    private void validateTeacherDates(V2CreateTeacherRequest request) {
        LocalDate today = LocalDate.now(clock);
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", "INVALID_TEACHER_DATES");

        LocalDate birthDate = request.birthDate();
        if (birthDate == null) {
            errors.put("birthDate", "Birth date is required.");
        } else if (!birthDate.isBefore(today)) {
            errors.put("birthDate", "Birth date must be before today.");
        } else if (birthDate.plusYears(MIN_TEACHER_AGE_YEARS).isAfter(today)) {
            errors.put("birthDate", "Teacher must be at least 18 years old.");
        }

        LocalDate teachingStartDate = request.teachingStartDate();
        if (teachingStartDate != null) {
            if (!teachingStartDate.isBefore(today)) {
                errors.put("teachingStartDate", "Teaching start date must be before today.");
            } else if (birthDate != null
                    && birthDate.isBefore(today)
                    && teachingStartDate.isBefore(birthDate.plusYears(MIN_TEACHER_AGE_YEARS))) {
                errors.put(
                        "teachingStartDate",
                        "Teaching start date cannot be earlier than the teacher's 18th birthday."
                );
            }
        }

        if (errors.size() > 1) {
            throw new V2FieldValidationException("Validation failed.", HttpStatus.BAD_REQUEST, errors);
        }
    }

    private void validatePublicUniqueness(String email, String contact) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", "DUPLICATE_ACCOUNT_DATA");

        if (accountRepository.emailExists(email)) {
            errors.put("email", "A user with this email already exists.");
        }
        if (accountRepository.contactExists(contact)) {
            errors.put("contactNumber", "A user with this contact number already exists.");
        }

        if (errors.size() > 1) {
            throw new V2FieldValidationException(
                    "A user with this email or contact number already exists.",
                    HttpStatus.CONFLICT,
                    errors
            );
        }
    }

    private String normalizeSchoolCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!LISTABLE_STATUSES.contains(normalized)) {
            throw badRequest("INVALID_STATUS_FILTER", "Unsupported teacher status filter.");
        }
        return normalized;
    }

    private void requirePrincipal(V2AuthenticatedUser user) {
        if (user == null || !PRINCIPAL_ROLE.equalsIgnoreCase(user.role())) {
            throw new V2AuthException(
                    "FORBIDDEN",
                    "Only a principal may manage teacher accounts.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private V2TeacherAccount requiredTeacher(String schoolId, long teacherUserId) {
        return accountRepository.findTeacher(schoolId, teacherUserId)
                .orElseThrow(() -> new V2AuthException(
                        "TEACHER_NOT_FOUND",
                        "Teacher account was not found.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private void recordAudit(
            V2AuthenticatedUser principal,
            V2RequestMetadata metadata,
            String action,
            long teacherUserId,
            String outcome,
            Map<String, String> details
    ) {
        authRepository.recordAudit(
                UUID.randomUUID().toString(),
                principal.userId(),
                action,
                "users",
                Long.toString(teacherUserId),
                outcome,
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                writeJson(details),
                clock.instant()
        );
    }

    private void recordPublicRegistrationAudit(
            V2RequestMetadata metadata,
            long teacherUserId,
            String schoolId,
            String verificationMethod
    ) {
        authRepository.recordAudit(
                UUID.randomUUID().toString(),
                teacherUserId,
                "REGISTER_TEACHER_ACCOUNT",
                "users",
                Long.toString(teacherUserId),
                "success",
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                writeJson(Map.of(
                        "status", PENDING_STATUS,
                        "schoolId", schoolId,
                        "source", "public_self_registration",
                        "verificationMethod", verificationMethod
                )),
                clock.instant()
        );
    }

    private V2CreateTeacherRequest toCreateTeacherRequest(V2TeacherRegistrationRequest request) {
        return new V2CreateTeacherRequest(
                request.firstName(),
                request.middleName(),
                request.lastName(),
                request.suffix(),
                request.suffixId(),
                request.birthDate(),
                request.teachingStartDate(),
                request.email(),
                request.contactNumber(),
                request.password(),
                request.genderId(),
                request.majorId(),
                request.educationalAttainmentId(),
                request.address()
        );
    }

    private String normalizeVerificationMethod(String value) {
        String method = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (EMAIL_VERIFICATION_METHOD.equals(method)) {
            return method;
        }
        if ("sms".equals(method)) {
            throw fieldValidation(
                    HttpStatus.BAD_REQUEST,
                    "VERIFICATION_METHOD_UNAVAILABLE",
                    "The selected verification method is not available.",
                    "verificationMethod",
                    "SMS verification is not available yet. Select email verification."
            );
        }
        throw fieldValidation(
                HttpStatus.BAD_REQUEST,
                "INVALID_VERIFICATION_METHOD",
                "Validation failed.",
                "verificationMethod",
                "Select an available verification method."
        );
    }

    private V2CreateTeacherRequest requestWithResolvedSuffix(V2CreateTeacherRequest request) {
        String suffix = null;
        if (request.suffixId() != null) {
            suffix = accountRepository.findSuffixName(request.suffixId())
                    .orElseThrow(() -> badRequest("INVALID_SUFFIX", "The selected suffix is unavailable."));
        }
        return new V2CreateTeacherRequest(
                request.firstName(),
                request.middleName(),
                request.lastName(),
                suffix,
                request.suffixId(),
                request.birthDate(),
                request.teachingStartDate(),
                request.email(),
                request.contactNumber(),
                request.temporaryPassword(),
                request.genderId(),
                request.majorId(),
                request.educationalAttainmentId(),
                request.address()
        );
    }

    private V2FieldValidationException fieldValidation(
            HttpStatus status,
            String code,
            String message,
            String field,
            String fieldMessage
    ) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", code);
        errors.put(field, fieldMessage);
        return new V2FieldValidationException(message, status, errors);
    }

    private V2FieldValidationException fieldValidation(
            HttpStatus status,
            String code,
            String message,
            String firstField,
            String firstFieldMessage,
            String secondField,
            String secondFieldMessage
    ) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", code);
        errors.put(firstField, firstFieldMessage);
        errors.put(secondField, secondFieldMessage);
        return new V2FieldValidationException(message, status, errors);
    }

    private String writeJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize V2 audit details.", exception);
        }
    }

    private V2TeacherAccountResponse toResponse(V2TeacherAccount teacher) {
        return new V2TeacherAccountResponse(
                teacher.userId(),
                teacher.schoolId(),
                teacher.addressId(),
                teacher.genderId(),
                teacher.majorId(),
                teacher.educationalAttainmentId(),
                teacher.firstName(),
                teacher.middleName(),
                teacher.lastName(),
                teacher.suffix(),
                teacher.birthDate(),
                teacher.teachingStartDate(),
                teacher.email(),
                teacher.contactNumber(),
                teacher.role(),
                teacher.status(),
                teacher.emailVerified(),
                teacher.contactVerified(),
                teacher.createdAt(),
                teacher.updatedAt()
        );
    }

    private V2AuthException badRequest(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.BAD_REQUEST);
    }

    private V2AuthException conflict(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.CONFLICT);
    }

    private void notifyPendingTeacher(
            String schoolId,
            long teacherUserId,
            V2CreateTeacherRequest request,
            Instant createdAt
    ) {
        if (notificationService == null) {
            return;
        }
        String teacherName = (request.firstName().trim() + " " + request.lastName().trim())
                .replaceAll("\\s+", " ");
        notificationService.notifySchoolPrincipals(
                schoolId,
                "teacher_registration_pending",
                "Teacher approval pending",
                teacherName + " submitted a teacher account for approval.",
                "users",
                Long.toString(teacherUserId),
                "teacher-registration:" + teacherUserId,
                createdAt
        );
    }
}
