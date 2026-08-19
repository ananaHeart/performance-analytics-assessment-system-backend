package com.capstone.assessment.v2.schoolsetup.service;

import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
import com.capstone.assessment.v2.schoolsetup.dto.V2AvailableClassResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2ClassAssignmentResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2CreateClassAssignmentRequest;
import com.capstone.assessment.v2.schoolsetup.dto.V2SchoolSetupReferenceResponse;
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
            long assignmentId = schoolSetupRepository.insertAssignment(
                    request.classId(),
                    request.teacherUserId(),
                    request.subjectId(),
                    assignmentRole,
                    now
            );
            V2ClassAssignmentResponse response = schoolSetupRepository
                    .findAssignment(assignmentId, principal.schoolId())
                    .orElseThrow(() -> new IllegalStateException("Created class assignment could not be retrieved."));
            recordSuccessAudit(principal, response, metadata, now);
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw conflict("DUPLICATE_CLASS_ASSIGNMENT", "The class assignment conflicts with existing data.");
        }
    }

    private void recordSuccessAudit(
            V2AuthenticatedUser principal,
            V2ClassAssignmentResponse assignment,
            V2RequestMetadata metadata,
            Instant createdAt
    ) {
        authRepository.recordAudit(
                UUID.randomUUID().toString(),
                principal.userId(),
                "CREATE_CLASS_ASSIGNMENT",
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
