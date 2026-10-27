package com.capstone.assessment.v3.account.service;

import com.capstone.assessment.v3.account.dto.V3RejectTeacherRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherContractRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherDecisionRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherReasonRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherStatusRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherUpdateRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountDetailResponse;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountSummaryResponse;
import com.capstone.assessment.v3.account.model.V3TeacherAccount;
import com.capstone.assessment.v3.account.repository.V3TeacherAccountRepository;
import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.dto.V3TeacherRegistrationRequest;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Profile("v3")
@Service
public class V3TeacherAccountService {

    static final String PENDING_APPROVAL_STATUS = "pending_approval";
    static final String ACTIVE_STATUS = "active";
    static final String REJECTED_STATUS = "rejected";
    static final String INACTIVE_STATUS = "inactive";
    static final String LOCKED_STATUS = "locked";
    static final String ARCHIVED_STATUS = "archived";
    /** REG-2 (D2): a teacher account is valid for the 3-year contract. */
    static final int CONTRACT_YEARS = 3;
    private static final ZoneId SCHOOL_TIMEZONE = ZoneId.of("Asia/Manila");

    private static final String PRINCIPAL_ROLE = "principal";
    private static final Set<String> APPROVED_ACCOUNT_STATUSES = Set.of(ACTIVE_STATUS, INACTIVE_STATUS, LOCKED_STATUS);
    /** TCH-3: the principal's tabs (Pending, Approved, Rejected, Archived) and the older single statuses. */
    private static final Map<String, List<String>> LIST_FILTERS = Map.of(
            "pending", List.of(PENDING_APPROVAL_STATUS),
            PENDING_APPROVAL_STATUS, List.of(PENDING_APPROVAL_STATUS),
            "approved", List.of(ACTIVE_STATUS, INACTIVE_STATUS, LOCKED_STATUS),
            REJECTED_STATUS, List.of(REJECTED_STATUS),
            ARCHIVED_STATUS, List.of(ARCHIVED_STATUS),
            ACTIVE_STATUS, List.of(ACTIVE_STATUS),
            INACTIVE_STATUS, List.of(INACTIVE_STATUS),
            LOCKED_STATUS, List.of(LOCKED_STATUS)
    );

    private final V3TeacherAccountRepository repository;
    private final V3AuditService auditService;
    private final V3NotificationService notificationService;
    private final V3RegistrationRepository registrationRepository;
    private final V3AuthProperties authProperties;
    private final Clock clock;

    @Autowired
    public V3TeacherAccountService(
            V3TeacherAccountRepository repository,
            V3AuditService auditService,
            V3NotificationService notificationService,
            V3RegistrationRepository registrationRepository,
            V3AuthProperties authProperties
    ) {
        this(repository, auditService, notificationService, registrationRepository, authProperties,
                Clock.systemUTC());
    }

    V3TeacherAccountService(
            V3TeacherAccountRepository repository,
            V3AuditService auditService,
            V3NotificationService notificationService,
            V3RegistrationRepository registrationRepository,
            V3AuthProperties authProperties,
            Clock clock
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.registrationRepository = registrationRepository;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<V3TeacherAccountSummaryResponse> listTeachers(
            V3AuthenticatedUser principal,
            String requestedStatus
    ) {
        requirePrincipal(principal);
        List<String> statuses = normalizeStatus(requestedStatus);
        return repository.findVerifiedTeachers(principal.schoolId(), statuses)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public V3TeacherAccountDetailResponse getTeacher(
            V3AuthenticatedUser principal,
            long teacherUserId
    ) {
        requirePrincipal(principal);
        return repository.findVerifiedTeacher(principal.schoolId(), teacherUserId)
                .map(this::toDetail)
                .orElseThrow(this::teacherNotFound);
    }

    @Transactional
    public V3TeacherAccountDetailResponse approveTeacher(
            V3AuthenticatedUser principal,
            long teacherUserId,
            V3RequestMetadata metadata
    ) {
        return decide(
                principal,
                teacherUserId,
                ACTIVE_STATUS,
                null,
                "teacher.approve",
                metadata
        );
    }

    @Transactional
    public V3TeacherAccountDetailResponse rejectTeacher(
            V3AuthenticatedUser principal,
            long teacherUserId,
            V3RejectTeacherRequest request,
            V3RequestMetadata metadata
    ) {
        String reason = normalizeReason(request == null ? null : request.reason());
        return decide(
                principal,
                teacherUserId,
                REJECTED_STATUS,
                reason,
                "teacher.reject",
                metadata
        );
    }

    private V3TeacherAccountDetailResponse decide(
            V3AuthenticatedUser principal,
            long teacherUserId,
            String targetStatus,
            String reason,
            String auditAction,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        Instant now = clock.instant();
        V3TeacherAccount teacher = repository.lockTeacher(principal.schoolId(), teacherUserId)
                .orElseThrow(this::teacherNotFound);

        if (teacher.emailVerifiedAt() == null && teacher.contactVerifiedAt() == null) {
            throw conflict(
                    "EMAIL_VERIFICATION_REQUIRED",
                    "The teacher must verify their email or contact number before principal review."
            );
        }
        if (!PENDING_APPROVAL_STATUS.equals(teacher.status())) {
            throw conflict(
                    "TEACHER_NOT_PENDING_APPROVAL",
                    "Only a teacher with pending_approval status may be approved or rejected."
            );
        }

        int updated = repository.transitionStatus(
                principal.schoolId(),
                teacherUserId,
                PENDING_APPROVAL_STATUS,
                targetStatus,
                now
        );
        if (updated != 1) {
            throw conflict(
                    "TEACHER_ACCOUNT_STATE_CHANGED",
                    "The teacher account changed during review. Refresh before trying again."
            );
        }

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("previousStatus", PENDING_APPROVAL_STATUS);
        auditDetails.put("newStatus", targetStatus);
        if (ACTIVE_STATUS.equals(targetStatus)) {
            LocalDate contractEnd = today().plusYears(CONTRACT_YEARS);
            repository.updateContractEndDate(teacherUserId, contractEnd, now);
            auditDetails.put("contractEndDate", contractEnd.toString());
        }
        if (reason != null) {
            auditDetails.put("reason", reason);
        }
        auditService.record(
                principal.userId(),
                auditAction,
                "users",
                Long.toString(teacherUserId),
                "success",
                metadata,
                auditDetails,
                now
        );

        V3TeacherAccountDetailResponse response = repository.findVerifiedTeacher(
                        principal.schoolId(), teacherUserId
                )
                .map(this::toDetail)
                .orElseThrow(() -> new IllegalStateException(
                        "The reviewed V3 teacher account could not be reloaded."
                ));
        if (ACTIVE_STATUS.equals(targetStatus)) {
            notificationService.notifyTeacherApproved(teacherUserId, now);
        } else {
            notificationService.notifyTeacherRejected(teacherUserId, now);
        }
        return response;
    }

    /**
     * TCH-5: reverse a decision after a mistake. Rejecting an approved teacher signs them out and
     * ends their open class assignments (TCH-9); approving a rejected teacher starts a new contract.
     */
    @Transactional
    public V3TeacherAccountDetailResponse changeDecision(
            V3AuthenticatedUser principal,
            long teacherUserId,
            V3TeacherDecisionRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String decision = request.decision().trim().toLowerCase(Locale.ROOT);
        String reason = requiredReason(request.reason());
        Instant now = clock.instant();
        V3TeacherAccount teacher = lockVerifiedTeacher(principal, teacherUserId);
        String current = approvalStatus(teacher.status());
        if (ARCHIVED_STATUS.equals(teacher.status())) {
            throw conflict("TEACHER_ARCHIVED", "Restore the teacher before changing the decision.");
        }
        if ("pending".equals(current)) {
            throw conflict("TEACHER_NOT_DECIDED", "Approve or reject a pending teacher first.");
        }
        if (decision.equals(current)) {
            throw conflict("TEACHER_DECISION_UNCHANGED", "The teacher is already " + current + ".");
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousStatus", teacher.status());
        details.put("reason", reason);
        String targetStatus;
        if (REJECTED_STATUS.equals(decision)) {
            targetStatus = REJECTED_STATUS;
            requireTransition(principal, teacher, targetStatus, now);
            details.put("endedClassAssignments", repository.endOpenClassAssignments(
                    teacherUserId, principal.userId(), "Teacher rejected: " + reason, now));
            repository.revokeSessions(teacherUserId, now);
        } else {
            targetStatus = ACTIVE_STATUS;
            requireTransition(principal, teacher, targetStatus, now);
            if (teacher.contractEndDate() == null || contractEnded(teacher)) {
                LocalDate contractEnd = today().plusYears(CONTRACT_YEARS);
                repository.updateContractEndDate(teacherUserId, contractEnd, now);
                details.put("contractEndDate", contractEnd.toString());
            }
        }
        details.put("newStatus", targetStatus);
        auditService.record(principal.userId(), "teacher.decision_change", "users",
                Long.toString(teacherUserId), "success", metadata, details, now);
        if (ACTIVE_STATUS.equals(targetStatus)) {
            notificationService.notifyTeacherApproved(teacherUserId, now);
        } else {
            notificationService.notifyTeacherRejected(teacherUserId, now);
        }
        return reload(principal, teacherUserId);
    }

    /** TCH-3: an approved teacher's account is Active or Inactive; inactive signs them out. */
    @Transactional
    public V3TeacherAccountDetailResponse changeAccountStatus(
            V3AuthenticatedUser principal,
            long teacherUserId,
            V3TeacherStatusRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String target = request.status().trim().toLowerCase(Locale.ROOT);
        String reason = requiredReason(request.reason());
        Instant now = clock.instant();
        V3TeacherAccount teacher = lockApprovedTeacher(principal, teacherUserId);
        if (target.equals(teacher.status())) {
            return reload(principal, teacherUserId);
        }
        if (ACTIVE_STATUS.equals(target) && contractEnded(teacher)) {
            throw new V3AuthException("TEACHER_CONTRACT_ENDED",
                    "The teacher's contract ended on " + teacher.contractEndDate()
                            + ". Extend the contract first.",
                    HttpStatus.CONFLICT, Map.of("contractEndDate", teacher.contractEndDate().toString()));
        }
        requireTransition(principal, teacher, target, now);
        if (INACTIVE_STATUS.equals(target)) {
            repository.revokeSessions(teacherUserId, now);
        }
        auditService.record(principal.userId(), "teacher.status_change", "users", Long.toString(teacherUserId),
                "success", metadata,
                Map.of("previousStatus", teacher.status(), "newStatus", target, "reason", reason), now);
        return reload(principal, teacherUserId);
    }

    /**
     * REG-2 (D2): the principal extends the contract. If the account became inactive because the
     * contract ended, it becomes active again.
     */
    @Transactional
    public V3TeacherAccountDetailResponse updateContract(
            V3AuthenticatedUser principal,
            long teacherUserId,
            V3TeacherContractRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requiredReason(request.reason());
        LocalDate today = today();
        LocalDate contractEnd = request.contractEndDate();
        if (contractEnd == null || !contractEnd.isAfter(today) || contractEnd.isAfter(today.plusYears(CONTRACT_YEARS))) {
            throw new V3FieldValidationException("Contract validation failed.", HttpStatus.BAD_REQUEST,
                    Map.of("code", "VALIDATION_FAILED",
                            "contractEndDate", "Choose a date after today and at most 3 years from today."));
        }
        Instant now = clock.instant();
        V3TeacherAccount teacher = lockApprovedTeacher(principal, teacherUserId);
        repository.updateContractEndDate(teacherUserId, contractEnd, now);
        boolean reactivated = INACTIVE_STATUS.equals(teacher.status()) && contractEnded(teacher);
        if (reactivated) {
            requireTransition(principal, teacher, ACTIVE_STATUS, now);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousContractEndDate", String.valueOf(teacher.contractEndDate()));
        details.put("contractEndDate", contractEnd.toString());
        details.put("reactivated", reactivated);
        details.put("reason", reason);
        auditService.record(principal.userId(), "teacher.contract_update", "users", Long.toString(teacherUserId),
                "success", metadata, details, now);
        return reload(principal, teacherUserId);
    }

    /**
     * TCH-4, TCH-9: archive the whole teacher. Their open class assignments end so the class and
     * subject can be given to someone else, and they are signed out. Past results stay in reports.
     */
    @Transactional
    public V3TeacherAccountDetailResponse archiveTeacher(
            V3AuthenticatedUser principal,
            long teacherUserId,
            V3TeacherReasonRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requiredReason(request == null ? null : request.reason());
        Instant now = clock.instant();
        V3TeacherAccount teacher = lockApprovedTeacher(principal, teacherUserId);
        if (repository.transitionArchiveState(principal.schoolId(), teacherUserId, teacher.status(),
                ARCHIVED_STATUS, now, now) != 1) {
            throw stateChanged();
        }
        int ended = repository.endOpenClassAssignments(teacherUserId, principal.userId(),
                "Teacher archived: " + reason, now);
        repository.revokeSessions(teacherUserId, now);
        auditService.record(principal.userId(), "teacher.archive", "users", Long.toString(teacherUserId),
                "success", metadata,
                Map.of("previousStatus", teacher.status(), "endedClassAssignments", ended, "reason", reason), now);
        return reload(principal, teacherUserId);
    }

    /** TCH-4: bring an archived teacher back; class assignments are given again by the principal. */
    @Transactional
    public V3TeacherAccountDetailResponse restoreTeacher(
            V3AuthenticatedUser principal,
            long teacherUserId,
            V3TeacherReasonRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requiredReason(request == null ? null : request.reason());
        Instant now = clock.instant();
        V3TeacherAccount teacher = lockVerifiedTeacher(principal, teacherUserId);
        if (!ARCHIVED_STATUS.equals(teacher.status())) {
            throw conflict("TEACHER_NOT_ARCHIVED", "Only an archived teacher can be restored.");
        }
        String target = contractEnded(teacher) ? INACTIVE_STATUS : ACTIVE_STATUS;
        if (repository.transitionArchiveState(principal.schoolId(), teacherUserId, ARCHIVED_STATUS,
                target, null, now) != 1) {
            throw stateChanged();
        }
        auditService.record(principal.userId(), "teacher.restore", "users", Long.toString(teacherUserId),
                "success", metadata, Map.of("newStatus", target, "reason", reason), now);
        return reload(principal, teacherUserId);
    }

    /** TCH-4: the principal corrects the teacher's details (GEN-1: no database editing needed). */
    @Transactional
    public V3TeacherAccountDetailResponse updateTeacher(
            V3AuthenticatedUser principal,
            long teacherUserId,
            V3TeacherUpdateRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        V3TeacherAccount teacher = lockVerifiedTeacher(principal, teacherUserId);
        if (ARCHIVED_STATUS.equals(teacher.status())) {
            throw conflict("TEACHER_ARCHIVED", "Restore the teacher before editing.");
        }
        validateDetails(request);
        Instant now = clock.instant();
        repository.updateTeacherDetails(teacherUserId, new V3TeacherAccountRepository.TeacherDetails(
                request.firstName().trim(),
                blankToNull(request.middleName()),
                request.lastName().trim(),
                request.suffixId(),
                request.genderId(),
                request.birthDate(),
                request.majorId(),
                request.educationalAttainmentId(),
                request.teachingStartMonth(),
                request.teachingStartYear()
        ), now);
        V3TeacherRegistrationRequest.Address address = request.address();
        repository.updateAddress(teacher.addressId(), new V3TeacherAccount.Address(
                address.countryCode() == null ? "PH" : address.countryCode().trim().toUpperCase(Locale.ROOT),
                blankToNull(address.regionCode()),
                blankToNull(address.regionName()),
                blankToNull(address.provinceCode()),
                blankToNull(address.provinceName()),
                blankToNull(address.cityMunicipalityCode()),
                address.cityMunicipalityName().trim(),
                blankToNull(address.barangayCode()),
                address.barangayName().trim(),
                address.addressLine().trim(),
                address.postalCode().trim(),
                null
        ));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fields", "details");
        if (request.reason() != null && !request.reason().isBlank()) {
            details.put("reason", request.reason().trim());
        }
        auditService.record(principal.userId(), "teacher.update", "users", Long.toString(teacherUserId),
                "success", metadata, details, now);
        return reload(principal, teacherUserId);
    }

    /** REG-2 (D2): runs daily; an ended contract makes the account inactive, never deleted. */
    public int deactivateEndedContracts() {
        LocalDate today = today();
        Instant now = clock.instant();
        int deactivated = 0;
        for (Long userId : repository.findActiveTeachersWithEndedContracts(today)) {
            if (repository.deactivateEndedContract(userId, today, now) == 1) {
                repository.revokeSessions(userId, now);
                auditService.record(null, "teacher.contract_ended", "users", Long.toString(userId),
                        "success", null, Map.of("newStatus", INACTIVE_STATUS), now);
                deactivated++;
            }
        }
        return deactivated;
    }

    private void validateDetails(V3TeacherUpdateRequest request) {
        Map<String, Object> errors = new LinkedHashMap<>();
        if (!registrationRepository.genderExists(request.genderId())) {
            errors.put("genderId", "The selected gender does not exist.");
        }
        if (!registrationRepository.majorExists(request.majorId())) {
            errors.put("majorId", "The selected teacher major does not exist.");
        }
        if (request.suffixId() != null && !registrationRepository.suffixExists(request.suffixId())) {
            errors.put("suffixId", "The selected suffix is unavailable.");
        }
        if (!registrationRepository.educationalAttainmentExists(request.educationalAttainmentId())) {
            errors.put("educationalAttainmentId", "The selected educational attainment is unavailable.");
        }
        LocalDate today = today();
        int minimumAge = authProperties.getMinimumTeacherAge();
        if (!request.birthDate().isBefore(today) || request.birthDate().plusYears(minimumAge).isAfter(today)) {
            errors.put("birthDate", "Teacher must be at least " + minimumAge
                    + " years old and birth date must be in the past.");
        }
        YearMonth teachingStart = YearMonth.of(request.teachingStartYear(), request.teachingStartMonth());
        if (teachingStart.isAfter(YearMonth.from(today))) {
            errors.put("teachingStartYear", "Teaching start month and year cannot be in the future.");
        } else if (teachingStart.isBefore(YearMonth.from(request.birthDate().plusYears(minimumAge)))) {
            errors.put("teachingStartYear", "Teaching start must be on or after the teacher turned " + minimumAge + ".");
        }
        if (!errors.isEmpty()) {
            errors.put("code", "VALIDATION_FAILED");
            throw new V3FieldValidationException("Teacher update validation failed.", HttpStatus.BAD_REQUEST, errors);
        }
    }

    private V3TeacherAccount lockVerifiedTeacher(V3AuthenticatedUser principal, long teacherUserId) {
        V3TeacherAccount teacher = repository.lockTeacher(principal.schoolId(), teacherUserId)
                .orElseThrow(this::teacherNotFound);
        if (teacher.emailVerifiedAt() == null && teacher.contactVerifiedAt() == null) {
            throw teacherNotFound();
        }
        return teacher;
    }

    private V3TeacherAccount lockApprovedTeacher(V3AuthenticatedUser principal, long teacherUserId) {
        V3TeacherAccount teacher = lockVerifiedTeacher(principal, teacherUserId);
        if (ARCHIVED_STATUS.equals(teacher.status())) {
            throw conflict("TEACHER_ARCHIVED", "The teacher is archived. Restore the teacher first.");
        }
        if (!APPROVED_ACCOUNT_STATUSES.contains(teacher.status())) {
            throw conflict("TEACHER_NOT_APPROVED", "Only an approved teacher can be changed this way.");
        }
        return teacher;
    }

    private void requireTransition(V3AuthenticatedUser principal, V3TeacherAccount teacher, String target, Instant now) {
        if (repository.transitionStatus(principal.schoolId(), teacher.userId(), teacher.status(), target, now) != 1) {
            throw stateChanged();
        }
    }

    private boolean contractEnded(V3TeacherAccount teacher) {
        return teacher.contractEndDate() != null && teacher.contractEndDate().isBefore(today());
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), SCHOOL_TIMEZONE);
    }

    private V3TeacherAccountDetailResponse reload(V3AuthenticatedUser principal, long teacherUserId) {
        return repository.findVerifiedTeacher(principal.schoolId(), teacherUserId)
                .map(this::toDetail)
                .orElseThrow(() -> new IllegalStateException("The teacher account could not be reloaded."));
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw invalidReason("A reason is required.");
        }
        String normalized = reason.trim();
        if (normalized.length() < 5 || normalized.length() > 255) {
            throw invalidReason("The reason must contain 5 to 255 characters.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private V3AuthException stateChanged() {
        return conflict("TEACHER_ACCOUNT_STATE_CHANGED",
                "The teacher account changed. Refresh before trying again.");
    }

    /** TCH-3: Pending / Approved / Rejected, separate from the account status. */
    static String approvalStatus(String status) {
        return switch (status) {
            case PENDING_APPROVAL_STATUS -> "pending";
            case REJECTED_STATUS -> "rejected";
            case ACTIVE_STATUS, INACTIVE_STATUS, LOCKED_STATUS, ARCHIVED_STATUS -> "approved";
            default -> "unverified";
        };
    }

    /** TCH-3: Active / Inactive / Archived; only an approved teacher has one. */
    static String accountStatus(String status) {
        return switch (status) {
            case ACTIVE_STATUS -> "active";
            case INACTIVE_STATUS, LOCKED_STATUS -> "inactive";
            case ARCHIVED_STATUS -> "archived";
            default -> null;
        };
    }

    private List<String> normalizeStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return List.of(PENDING_APPROVAL_STATUS);
        }
        List<String> statuses = LIST_FILTERS.get(requestedStatus.trim().toLowerCase(Locale.ROOT));
        if (statuses == null) {
            throw new V3FieldValidationException(
                    "Teacher-list validation failed.",
                    HttpStatus.BAD_REQUEST,
                    Map.of(
                            "code", "VALIDATION_FAILED",
                            "status", "Use pending, approved, rejected, archived, active, or inactive."
                    )
            );
        }
        return statuses;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw invalidReason("Rejection reason is required.");
        }
        String normalized = reason.trim();
        if (normalized.length() < 5 || normalized.length() > 500) {
            throw invalidReason("Rejection reason must contain 5 to 500 characters.");
        }
        return normalized;
    }

    private V3FieldValidationException invalidReason(String message) {
        return new V3FieldValidationException(
                "Teacher rejection validation failed.",
                HttpStatus.BAD_REQUEST,
                Map.of("code", "VALIDATION_FAILED", "reason", message)
        );
    }

    private void requirePrincipal(V3AuthenticatedUser principal) {
        if (principal == null) {
            throw new V3AuthException(
                    "AUTHENTICATION_REQUIRED",
                    "Authentication is required.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        if (!PRINCIPAL_ROLE.equalsIgnoreCase(principal.role())) {
            throw new V3AuthException(
                    "PRINCIPAL_ROLE_REQUIRED",
                    "Only an authenticated principal may review teacher accounts.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (!ACTIVE_STATUS.equalsIgnoreCase(principal.status())
                || principal.schoolId() == null
                || principal.schoolId().isBlank()) {
            throw new V3AuthException(
                    "ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active principal account associated with a school is required.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private V3TeacherAccountSummaryResponse toSummary(V3TeacherAccount teacher) {
        return new V3TeacherAccountSummaryResponse(
                teacher.userId(),
                teacher.schoolId(),
                fullName(teacher),
                teacher.email(),
                teacher.contactNumber(),
                teacher.status(),
                teacher.emailVerifiedAt(),
                teacher.createdAt(),
                teacher.updatedAt(),
                approvalStatus(teacher.status()),
                accountStatus(teacher.status()),
                teacher.contractEndDate(),
                teacher.archivedAt()
        );
    }

    private V3TeacherAccountDetailResponse toDetail(V3TeacherAccount teacher) {
        V3TeacherAccount.Address address = teacher.address();
        return new V3TeacherAccountDetailResponse(
                teacher.userId(),
                teacher.schoolId(),
                teacher.addressId(),
                teacher.genderId(),
                teacher.genderName(),
                teacher.majorId(),
                teacher.majorName(),
                teacher.educationalAttainmentId(),
                teacher.educationalAttainmentName(),
                teacher.suffixId(),
                teacher.suffixName(),
                teacher.firstName(),
                teacher.middleName(),
                teacher.lastName(),
                fullName(teacher),
                teacher.birthDate(),
                teacher.teachingStartMonth(),
                teacher.teachingStartYear(),
                teacher.email(),
                teacher.contactNumber(),
                teacher.role(),
                teacher.status(),
                teacher.emailVerifiedAt() != null,
                teacher.contactVerifiedAt() != null,
                teacher.emailVerifiedAt(),
                teacher.contactVerifiedAt(),
                teacher.createdAt(),
                teacher.updatedAt(),
                new V3TeacherAccountDetailResponse.Address(
                        address.countryCode(),
                        address.regionCode(),
                        address.regionName(),
                        address.provinceCode(),
                        address.provinceName(),
                        address.cityMunicipalityCode(),
                        address.cityMunicipalityName(),
                        address.barangayCode(),
                        address.barangayName(),
                        address.addressLine(),
                        address.postalCode(),
                        address.addressSource()
                ),
                approvalStatus(teacher.status()),
                accountStatus(teacher.status()),
                teacher.contractEndDate(),
                teacher.archivedAt(),
                V3AccountSettingsService.toSubjectsResponse(registrationRepository.listTeacherSubjects(teacher.userId()))
        );
    }

    private String fullName(V3TeacherAccount teacher) {
        List<String> parts = new ArrayList<>();
        addNamePart(parts, teacher.firstName());
        addNamePart(parts, teacher.middleName());
        addNamePart(parts, teacher.lastName());
        addNamePart(parts, teacher.suffixName());
        return String.join(" ", parts);
    }

    private void addNamePart(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private V3AuthException teacherNotFound() {
        return new V3AuthException(
                "TEACHER_NOT_FOUND",
                "The teacher account was not found in the principal's school.",
                HttpStatus.NOT_FOUND
        );
    }

    private V3AuthException conflict(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.CONFLICT);
    }
}
