package com.capstone.assessment.v3.account.service;

import com.capstone.assessment.v3.account.dto.V3RejectTeacherRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountDetailResponse;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountSummaryResponse;
import com.capstone.assessment.v3.account.model.V3TeacherAccount;
import com.capstone.assessment.v3.account.repository.V3TeacherAccountRepository;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
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

    private static final String PRINCIPAL_ROLE = "principal";
    private static final Set<String> PRINCIPAL_VISIBLE_STATUSES = Set.of(
            PENDING_APPROVAL_STATUS,
            ACTIVE_STATUS,
            REJECTED_STATUS,
            "inactive",
            "locked"
    );

    private final V3TeacherAccountRepository repository;
    private final V3AuditService auditService;
    private final V3NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public V3TeacherAccountService(
            V3TeacherAccountRepository repository,
            V3AuditService auditService,
            V3NotificationService notificationService
    ) {
        this(repository, auditService, notificationService, Clock.systemUTC());
    }

    V3TeacherAccountService(
            V3TeacherAccountRepository repository,
            V3AuditService auditService,
            V3NotificationService notificationService,
            Clock clock
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<V3TeacherAccountSummaryResponse> listTeachers(
            V3AuthenticatedUser principal,
            String requestedStatus
    ) {
        requirePrincipal(principal);
        String status = normalizeStatus(requestedStatus);
        return repository.findVerifiedTeachers(principal.schoolId(), status)
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

        if (teacher.emailVerifiedAt() == null) {
            throw conflict(
                    "EMAIL_VERIFICATION_REQUIRED",
                    "The teacher must verify the registered email before principal review."
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

    private String normalizeStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return PENDING_APPROVAL_STATUS;
        }
        String status = requestedStatus.trim().toLowerCase(Locale.ROOT);
        if (!PRINCIPAL_VISIBLE_STATUSES.contains(status)) {
            throw new V3FieldValidationException(
                    "Teacher-list validation failed.",
                    HttpStatus.BAD_REQUEST,
                    Map.of(
                            "code", "VALIDATION_FAILED",
                            "status", "Use pending_approval, active, rejected, inactive, or locked."
                    )
            );
        }
        return status;
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
                teacher.updatedAt()
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
                )
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
