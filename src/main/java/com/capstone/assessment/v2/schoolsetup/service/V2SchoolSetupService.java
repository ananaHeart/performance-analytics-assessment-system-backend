package com.capstone.assessment.v2.schoolsetup.service;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
import com.capstone.assessment.v2.schoolsetup.dto.V2AvailableClassResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2ClassAssignmentResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2CreateClassAssignmentRequest;
import com.capstone.assessment.v2.schoolsetup.dto.V2SchoolSetupReferenceResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2UpdateClassAssignmentRequest;
import com.capstone.assessment.v2.schoolsetup.model.V2ClassContext;
import com.capstone.assessment.v2.schoolsetup.repository.V2SchoolSetupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Profile("v2")
@Service
public class V2SchoolSetupService {

    private final V2SchoolSetupRepository schoolSetupRepository;
    private final V2AuthRepository authRepository;
    private final Clock clock;

    @Autowired
    public V2SchoolSetupService(
            V2SchoolSetupRepository schoolSetupRepository,
            V2AuthRepository authRepository
    ) {
        this(schoolSetupRepository, authRepository, Clock.systemUTC());
    }

    V2SchoolSetupService(
            V2SchoolSetupRepository schoolSetupRepository,
            V2AuthRepository authRepository,
            Clock clock
    ) {
        this.schoolSetupRepository = schoolSetupRepository;
        this.authRepository = authRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public V2SchoolSetupReferenceResponse getReferenceData(V2AuthenticatedUser principal) {
        requirePrincipal(principal);
        return new V2SchoolSetupReferenceResponse(
                schoolSetupRepository.listAcademicYears(),
                schoolSetupRepository.listGradeLevels(),
                schoolSetupRepository.listSubjects()
        );
    }

    @Transactional(readOnly = true)
    public List<V2AvailableClassResponse> getAvailableClasses(
            V2AuthenticatedUser principal,
            Integer academicYearId,
            Integer gradeLevelId,
            Integer subjectId
    ) {
        requirePrincipal(principal);
        requirePositive(academicYearId, "ACADEMIC_YEAR_REQUIRED", "Academic year ID is required.");
        requirePositive(gradeLevelId, "GRADE_LEVEL_REQUIRED", "Grade level ID is required.");
        requirePositive(subjectId, "SUBJECT_REQUIRED", "Subject ID is required.");
        if (!schoolSetupRepository.subjectExists(subjectId)) {
            throw badRequest("SUBJECT_NOT_FOUND", "Subject was not found.");
        }
        return schoolSetupRepository.findAvailableClasses(
                principal.schoolId(), academicYearId, gradeLevelId, subjectId
        );
    }

    @Transactional(readOnly = true)
    public List<V2ClassAssignmentResponse> listAssignments(
            V2AuthenticatedUser principal,
            Integer academicYearId
    ) {
        requirePrincipal(principal);
        if (academicYearId != null && academicYearId <= 0) {
            throw badRequest("INVALID_ACADEMIC_YEAR", "Academic year ID must be positive.");
        }
        return schoolSetupRepository.listAssignments(principal.schoolId(), academicYearId);
    }

    @Transactional
    public V2ClassAssignmentResponse createAssignment(
            V2AuthenticatedUser principal,
            V2CreateClassAssignmentRequest request,
            V2RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String assignmentRole = normalizeRole(request.assignmentRole());
        Instant now = clock.instant();

        V2ClassContext classContext = schoolSetupRepository.findClassContext(request.classId())
                .orElseThrow(() -> badRequest("CLASS_NOT_FOUND", "Class was not found."));
        if (!"active".equalsIgnoreCase(classContext.classStatus())) {
            throw conflict("CLASS_NOT_ACTIVE", "Only an active class can receive a teacher assignment.");
        }
        if (!schoolSetupRepository.activeTeacherExistsInSchool(request.teacherUserId(), principal.schoolId())) {
            throw badRequest(
                    "TEACHER_NOT_AVAILABLE",
                    "Teacher must be active and belong to the principal's school."
            );
        }
        if (!schoolSetupRepository.subjectExists(request.subjectId())) {
            throw badRequest("SUBJECT_NOT_FOUND", "Subject was not found.");
        }
        if (!schoolSetupRepository.classHasEnrolledStudents(request.classId(), principal.schoolId())) {
            throw conflict(
                    "CLASS_HAS_NO_ENROLLED_STUDENTS",
                    "Class must contain enrolled students from the principal's school."
            );
        }

        schoolSetupRepository.lockClass(request.classId());
        if (schoolSetupRepository.activeAssignmentExists(
                request.classId(), request.teacherUserId(), request.subjectId()
        )) {
            throw conflict("DUPLICATE_CLASS_ASSIGNMENT", "This active class assignment already exists.");
        }
        if ("primary".equals(assignmentRole)
                && schoolSetupRepository.activePrimaryAssignmentExists(request.classId(), request.subjectId())) {
            throw conflict(
                    "PRIMARY_ASSIGNMENT_EXISTS",
                    "This class and subject already have an active primary teacher."
            );
        }

        try {
            Long existingAssignmentId = schoolSetupRepository.findAssignmentId(
                    request.classId(),
                    request.teacherUserId(),
                    request.subjectId()
            ).orElse(null);
            if (existingAssignmentId != null) {
                throw conflict(
                        "ARCHIVED_ASSIGNMENT_REQUIRES_REACTIVATION",
                        "Restore the archived assignment from Settings and provide a reason."
                );
            }
            long assignmentId = schoolSetupRepository.insertAssignment(
                    request.classId(), request.teacherUserId(), request.subjectId(), assignmentRole, now
            );
            V2ClassAssignmentResponse response = schoolSetupRepository
                    .findAssignment(assignmentId, principal.schoolId())
                    .orElseThrow(() -> new IllegalStateException("Created class assignment could not be retrieved."));
            recordSuccessAudit(principal, response, metadata, now, "CREATE_CLASS_ASSIGNMENT");
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw conflict("DUPLICATE_CLASS_ASSIGNMENT", "The class assignment conflicts with existing data.");
        }
    }

    @Transactional
    public V2ClassAssignmentResponse updateAssignment(
            V2AuthenticatedUser principal,
            long classAssignmentId,
            V2UpdateClassAssignmentRequest request,
            V2RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        if (classAssignmentId <= 0) {
            throw badRequest("INVALID_CLASS_ASSIGNMENT_ID", "Class assignment ID must be positive.");
        }

        V2ClassAssignmentResponse existing = schoolSetupRepository
                .findAssignment(classAssignmentId, principal.schoolId())
                .orElseThrow(() -> new V2AuthException(
                        "CLASS_ASSIGNMENT_NOT_FOUND",
                        "Class assignment was not found in the principal's school.",
                        HttpStatus.NOT_FOUND
                ));
        if (!"active".equalsIgnoreCase(existing.status())) {
            throw conflict("CLASS_ASSIGNMENT_NOT_ACTIVE", "Only an active class assignment can be edited.");
        }

        String assignmentRole = normalizeRole(request.assignmentRole());
        boolean unchanged = existing.classId().equals(request.classId())
                && existing.teacherUserId().equals(request.teacherUserId())
                && existing.subjectId().equals(request.subjectId())
                && assignmentRole.equalsIgnoreCase(existing.assignmentRole());
        if (unchanged) {
            throw badRequest("NO_ASSIGNMENT_CHANGES", "Change at least one assignment field.");
        }
        if (schoolSetupRepository.assignmentHasAssessments(classAssignmentId)) {
            throw conflict(
                    "ASSIGNMENT_HAS_ASSESSMENTS",
                    "This assignment already has assessments and cannot be edited. Archive it and create a new assignment instead."
            );
        }

        V2ClassContext classContext = schoolSetupRepository.findClassContext(request.classId())
                .orElseThrow(() -> badRequest("CLASS_NOT_FOUND", "Class was not found."));
        if (!"active".equalsIgnoreCase(classContext.classStatus())) {
            throw conflict("CLASS_NOT_ACTIVE", "Only an active class can receive a teacher assignment.");
        }
        if (!schoolSetupRepository.activeTeacherExistsInSchool(request.teacherUserId(), principal.schoolId())) {
            throw badRequest(
                    "TEACHER_NOT_AVAILABLE",
                    "Teacher must be active and belong to the principal's school."
            );
        }
        if (!schoolSetupRepository.subjectExists(request.subjectId())) {
            throw badRequest("SUBJECT_NOT_FOUND", "Subject was not found.");
        }
        if (!schoolSetupRepository.classHasEnrolledStudents(request.classId(), principal.schoolId())) {
            throw conflict(
                    "CLASS_HAS_NO_ENROLLED_STUDENTS",
                    "Class must contain enrolled students from the principal's school."
            );
        }

        long firstClassId = Math.min(existing.classId(), request.classId());
        long secondClassId = Math.max(existing.classId(), request.classId());
        schoolSetupRepository.lockClass(firstClassId);
        if (secondClassId != firstClassId) {
            schoolSetupRepository.lockClass(secondClassId);
        }
        if (schoolSetupRepository.activeAssignmentExistsExcluding(
                classAssignmentId,
                request.classId(),
                request.teacherUserId(),
                request.subjectId()
        )) {
            throw conflict("DUPLICATE_CLASS_ASSIGNMENT", "This active class assignment already exists.");
        }
        if ("primary".equals(assignmentRole)
                && schoolSetupRepository.activePrimaryAssignmentExistsExcluding(
                        classAssignmentId,
                        request.classId(),
                        request.subjectId()
                )) {
            throw conflict(
                    "PRIMARY_ASSIGNMENT_EXISTS",
                    "This class and subject already have an active primary teacher."
            );
        }

        Instant now = clock.instant();
        try {
            if (schoolSetupRepository.updateActiveAssignment(
                    classAssignmentId,
                    principal.schoolId(),
                    request.classId(),
                    request.teacherUserId(),
                    request.subjectId(),
                    assignmentRole
            ) != 1) {
                throw conflict(
                        "CLASS_ASSIGNMENT_STATE_CHANGED",
                        "The assignment changed before the update completed."
                );
            }
            V2ClassAssignmentResponse response = schoolSetupRepository
                    .findAssignment(classAssignmentId, principal.schoolId())
                    .orElseThrow(() -> new IllegalStateException("Updated class assignment could not be retrieved."));
            recordUpdateAudit(principal, existing, response, metadata, now);
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw conflict("DUPLICATE_CLASS_ASSIGNMENT", "The class assignment conflicts with existing data.");
        }
    }

    @Transactional
    public V2ClassAssignmentResponse deactivateAssignment(
            V2AuthenticatedUser principal,
            long classAssignmentId,
            V2RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        if (classAssignmentId <= 0) {
            throw badRequest("INVALID_CLASS_ASSIGNMENT_ID", "Class assignment ID must be positive.");
        }

        V2ClassAssignmentResponse existing = schoolSetupRepository
                .findAssignment(classAssignmentId, principal.schoolId())
                .orElseThrow(() -> new V2AuthException(
                        "CLASS_ASSIGNMENT_NOT_FOUND",
                        "Class assignment was not found in the principal's school.",
                        HttpStatus.NOT_FOUND
                ));
        if (!"active".equalsIgnoreCase(existing.status())) {
            throw conflict("CLASS_ASSIGNMENT_NOT_ACTIVE", "Only an active class assignment can be removed.");
        }

        if (schoolSetupRepository.archiveActiveAssignment(classAssignmentId, principal.schoolId()) != 1) {
            throw conflict(
                    "CLASS_ASSIGNMENT_STATE_CHANGED",
                    "The class assignment changed before the removal completed."
            );
        }

        Instant now = clock.instant();
        recordDeactivationAudit(principal, existing, metadata, now);
        return schoolSetupRepository.findAssignment(classAssignmentId, principal.schoolId())
                .orElseThrow(() -> new IllegalStateException("Archived class assignment could not be retrieved."));
    }

    @Transactional
    public V2ClassAssignmentResponse reactivateAssignment(
            V2AuthenticatedUser principal,
            long classAssignmentId,
            String reason,
            V2RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        if (classAssignmentId <= 0) {
            throw badRequest("INVALID_CLASS_ASSIGNMENT_ID", "Class assignment ID must be positive.");
        }
        String normalizedReason = normalizeReactivationReason(reason);

        V2ClassAssignmentResponse existing = schoolSetupRepository
                .findAssignment(classAssignmentId, principal.schoolId())
                .orElseThrow(() -> new V2AuthException(
                        "CLASS_ASSIGNMENT_NOT_FOUND",
                        "Class assignment was not found in the principal's school.",
                        HttpStatus.NOT_FOUND
                ));
        if (!"archived".equalsIgnoreCase(existing.status())) {
            throw conflict("CLASS_ASSIGNMENT_NOT_ARCHIVED", "Only an archived assignment can be restored.");
        }

        V2ClassContext classContext = schoolSetupRepository.findClassContext(existing.classId())
                .orElseThrow(() -> badRequest("CLASS_NOT_FOUND", "Class was not found."));
        if (!"active".equalsIgnoreCase(classContext.classStatus())) {
            throw conflict("CLASS_NOT_ACTIVE", "Only an active class can receive a teacher assignment.");
        }
        if (!schoolSetupRepository.activeTeacherExistsInSchool(existing.teacherUserId(), principal.schoolId())) {
            throw badRequest(
                    "TEACHER_NOT_AVAILABLE",
                    "Teacher must be active and belong to the principal's school."
            );
        }
        if (!schoolSetupRepository.subjectExists(existing.subjectId())) {
            throw badRequest("SUBJECT_NOT_FOUND", "Subject was not found.");
        }
        if (!schoolSetupRepository.classHasEnrolledStudents(existing.classId(), principal.schoolId())) {
            throw conflict(
                    "CLASS_HAS_NO_ENROLLED_STUDENTS",
                    "Class must contain enrolled students from the principal's school."
            );
        }

        schoolSetupRepository.lockClass(existing.classId());
        if ("primary".equalsIgnoreCase(existing.assignmentRole())
                && schoolSetupRepository.activePrimaryAssignmentExists(existing.classId(), existing.subjectId())) {
            throw conflict(
                    "PRIMARY_ASSIGNMENT_EXISTS",
                    "This class and subject already have an active primary teacher."
            );
        }

        Instant now = clock.instant();
        try {
            if (schoolSetupRepository.reactivateArchivedAssignment(
                    classAssignmentId,
                    principal.schoolId(),
                    existing.assignmentRole(),
                    now
            ) != 1) {
                throw conflict(
                        "CLASS_ASSIGNMENT_STATE_CHANGED",
                        "The archived assignment changed before reactivation completed."
                );
            }
            V2ClassAssignmentResponse response = schoolSetupRepository
                    .findAssignment(classAssignmentId, principal.schoolId())
                    .orElseThrow(() -> new IllegalStateException("Restored class assignment could not be retrieved."));
            recordReactivationAudit(principal, response, normalizedReason, metadata, now);
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw conflict("DUPLICATE_CLASS_ASSIGNMENT", "The class assignment conflicts with existing data.");
        }
    }

    private void recordSuccessAudit(
            V2AuthenticatedUser principal,
            V2ClassAssignmentResponse assignment,
            V2RequestMetadata metadata,
            Instant createdAt,
            String action
    ) {
        authRepository.recordAudit(
                UUID.randomUUID().toString(),
                principal.userId(),
                action,
                "class_assignments",
                String.valueOf(assignment.classAssignmentId()),
                "success",
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                "{\"classId\":" + assignment.classId()
                        + ",\"teacherUserId\":" + assignment.teacherUserId()
                        + ",\"subjectId\":" + assignment.subjectId()
                        + ",\"assignmentRole\":\"" + assignment.assignmentRole() + "\"}",
                createdAt
        );
    }

    private void recordDeactivationAudit(
            V2AuthenticatedUser principal,
            V2ClassAssignmentResponse assignment,
            V2RequestMetadata metadata,
            Instant deactivatedAt
    ) {
        authRepository.recordAudit(
                UUID.randomUUID().toString(),
                principal.userId(),
                "DEACTIVATE_CLASS_ASSIGNMENT",
                "class_assignments",
                String.valueOf(assignment.classAssignmentId()),
                "success",
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                "{\"classId\":" + assignment.classId()
                        + ",\"teacherUserId\":" + assignment.teacherUserId()
                        + ",\"subjectId\":" + assignment.subjectId()
                        + ",\"previousStatus\":\"active\",\"newStatus\":\"archived\"}",
                deactivatedAt
        );
    }

    private void recordUpdateAudit(
            V2AuthenticatedUser principal,
            V2ClassAssignmentResponse previous,
            V2ClassAssignmentResponse updated,
            V2RequestMetadata metadata,
            Instant updatedAt
    ) {
        authRepository.recordAudit(
                UUID.randomUUID().toString(),
                principal.userId(),
                "UPDATE_CLASS_ASSIGNMENT",
                "class_assignments",
                String.valueOf(updated.classAssignmentId()),
                "success",
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                "{\"previous\":{\"classId\":" + previous.classId()
                        + ",\"teacherUserId\":" + previous.teacherUserId()
                        + ",\"subjectId\":" + previous.subjectId()
                        + ",\"assignmentRole\":\"" + previous.assignmentRole()
                        + "\"},\"updated\":{\"classId\":" + updated.classId()
                        + ",\"teacherUserId\":" + updated.teacherUserId()
                        + ",\"subjectId\":" + updated.subjectId()
                        + ",\"assignmentRole\":\"" + updated.assignmentRole() + "\"}}",
                updatedAt
        );
    }

    private void recordReactivationAudit(
            V2AuthenticatedUser principal,
            V2ClassAssignmentResponse assignment,
            String reason,
            V2RequestMetadata metadata,
            Instant reactivatedAt
    ) {
        authRepository.recordAudit(
                UUID.randomUUID().toString(),
                principal.userId(),
                "REACTIVATE_CLASS_ASSIGNMENT",
                "class_assignments",
                String.valueOf(assignment.classAssignmentId()),
                "success",
                metadata.ipAddress(),
                metadata.deviceIdentifier(),
                metadata.userAgent(),
                "{\"classId\":" + assignment.classId()
                        + ",\"teacherUserId\":" + assignment.teacherUserId()
                        + ",\"subjectId\":" + assignment.subjectId()
                        + ",\"previousStatus\":\"archived\",\"newStatus\":\"active\",\"reason\":"
                        + quoteJson(reason) + "}",
                reactivatedAt
        );
    }

    private String normalizeReactivationReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw badRequest(
                    "INVALID_REACTIVATION_REASON",
                    "Reactivation reason must contain 10 to 500 characters."
            );
        }
        return normalized;
    }

    private String quoteJson(String value) {
        return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(value)) + "\"";
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "primary";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (!"primary".equals(normalized) && !"co_teacher".equals(normalized)) {
            throw badRequest("INVALID_ASSIGNMENT_ROLE", "Assignment role must be primary or co_teacher.");
        }
        return normalized;
    }

    private void requirePrincipal(V2AuthenticatedUser user) {
        if (user == null || !"principal".equalsIgnoreCase(user.role())) {
            throw new V2AuthException("FORBIDDEN", "Principal access is required.", HttpStatus.FORBIDDEN);
        }
        if (user.schoolId() == null || user.schoolId().isBlank()) {
            throw new V2AuthException("SCHOOL_CONTEXT_REQUIRED", "School context is required.", HttpStatus.FORBIDDEN);
        }
    }

    private void requirePositive(Integer value, String code, String message) {
        if (value == null || value <= 0) {
            throw badRequest(code, message);
        }
    }

    private V2AuthException badRequest(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.BAD_REQUEST);
    }

    private V2AuthException conflict(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.CONFLICT);
    }
}
