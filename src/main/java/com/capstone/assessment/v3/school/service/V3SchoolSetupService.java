package com.capstone.assessment.v3.school.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import com.capstone.assessment.v3.school.dto.V3ClassAssignmentRequest;
import com.capstone.assessment.v3.school.dto.V3ClassAssignmentResponse;
import com.capstone.assessment.v3.school.dto.V3ClassAssignmentStatusRequest;
import com.capstone.assessment.v3.school.dto.V3ClassRequest;
import com.capstone.assessment.v3.school.dto.V3ClassResponse;
import com.capstone.assessment.v3.school.dto.V3ManualStudentEnrollmentRequest;
import com.capstone.assessment.v3.school.dto.V3ManualStudentEnrollmentResponse;
import com.capstone.assessment.v3.school.dto.V3SchoolProfileResponse;
import com.capstone.assessment.v3.school.dto.V3SchoolProfileUpdateRequest;
import com.capstone.assessment.v3.school.dto.V3SchoolSetupReferenceDataResponse;
import com.capstone.assessment.v3.school.dto.V3StudentEnrollmentStatusRequest;
import com.capstone.assessment.v3.school.dto.V3StudentProfileUpdateRequest;
import com.capstone.assessment.v3.school.dto.V3StudentRosterEntryResponse;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.AssignmentContext;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.ClassContext;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.EnrollmentContext;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.StudentContext;
import com.capstone.assessment.v3.school.repository.V3SchoolSetupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Profile("v3")
@Service
public class V3SchoolSetupService {

    private static final String PRINCIPAL_ROLE = "principal";
    private static final String TEACHER_ROLE = "teacher";
    private static final Set<String> ASSIGNMENT_ROLES = Set.of("primary", "co_teacher");
    private static final Set<String> ENROLLMENT_STATUSES = Set.of(
            "enrolled", "transferred", "dropped", "completed"
    );
    private static final Set<String> ADDRESS_SOURCES = Set.of("api", "manual");
    private static final Pattern CONTACT_PATTERN = Pattern.compile("^(?:09\\d{9}|\\+639\\d{9})$");
    private static final Pattern STUDENT_LRN_PATTERN = Pattern.compile("^[0-9]{12}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$",
            Pattern.CASE_INSENSITIVE
    );

    private final V3SchoolSetupRepository repository;
    private final V3AuditService auditService;
    private final V3NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public V3SchoolSetupService(
            V3SchoolSetupRepository repository,
            V3AuditService auditService,
            V3NotificationService notificationService
    ) {
        this(repository, auditService, notificationService, Clock.systemUTC());
    }

    V3SchoolSetupService(
            V3SchoolSetupRepository repository,
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
    public V3SchoolSetupReferenceDataResponse getReferenceData(V3AuthenticatedUser principal) {
        requirePrincipal(principal);
        return new V3SchoolSetupReferenceDataResponse(
                requireSchool(principal.schoolId()),
                repository.listAcademicYears(principal.schoolId()),
                repository.listTermPeriods(principal.schoolId()),
                repository.listGradeLevels(),
                repository.listSubjects(),
                repository.listActiveTeachers(principal.schoolId()),
                repository.listGenders(),
                repository.listActiveSuffixes()
        );
    }

    @Transactional(readOnly = true)
    public V3SchoolProfileResponse getSchoolProfile(V3AuthenticatedUser principal) {
        requirePrincipal(principal);
        return requireSchool(principal.schoolId());
    }

    @Transactional
    public V3SchoolProfileResponse updateSchoolProfile(
            V3AuthenticatedUser principal,
            V3SchoolProfileUpdateRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String schoolName = requiredTrimmed(request.schoolName(), "schoolName", 120);
        String contactNumber = normalizeContact(request.contactNumber());
        String email = normalizeEmail(request.email());
        V3SchoolProfileUpdateRequest.Address address = normalizeAddress(request.address());

        requireSchool(principal.schoolId());

        try {
            repository.updateSchoolProfile(principal.schoolId(), schoolName, contactNumber, email);
            repository.updateSchoolAddress(principal.schoolId(), address);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("SCHOOL_PROFILE_CONFLICT",
                    "The school email or profile values conflict with existing data.");
        }

        auditService.record(
                principal.userId(), "school.profile.update", "school_profiles", principal.schoolId(),
                "success", metadata, Map.of("schoolName", schoolName), clock.instant()
        );
        return requireSchool(principal.schoolId());
    }

    @Transactional(readOnly = true)
    public List<V3ClassResponse> listClasses(
            V3AuthenticatedUser principal,
            Integer academicYearId,
            Integer gradeLevelId
    ) {
        requirePrincipal(principal);
        validateOptionalPositive(academicYearId, "academicYearId");
        validateOptionalPositive(gradeLevelId, "gradeLevelId");
        return repository.listClasses(principal.schoolId(), academicYearId, gradeLevelId);
    }

    @Transactional
    public V3ClassResponse createClass(
            V3AuthenticatedUser principal,
            V3ClassRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        return createOrReuseClass(
                principal,
                request.academicYearId(),
                request.gradeLevelId(),
                requiredTrimmed(request.sectionName(), "sectionName", 50),
                metadata,
                true
        );
    }

    @Transactional(readOnly = true)
    public List<V3ClassAssignmentResponse> listAssignments(
            V3AuthenticatedUser principal,
            Integer academicYearId
    ) {
        requirePrincipal(principal);
        validateOptionalPositive(academicYearId, "academicYearId");
        return repository.listAssignments(principal.schoolId(), academicYearId);
    }

    @Transactional
    public V3ClassAssignmentResponse assignTeacher(
            V3AuthenticatedUser principal,
            V3ClassAssignmentRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String role = normalizeAllowed(request.assignmentRole(), ASSIGNMENT_ROLES, "assignmentRole");
        requireActiveClass(requireClass(principal.schoolId(), request.classId()));
        if (!repository.activeTeacherExists(principal.schoolId(), request.teacherUserId())) {
            throw invalid("teacherUserId",
                    "Select an active, email-verified teacher from the principal's school.");
        }
        if (!repository.subjectExists(request.subjectId())) {
            throw invalid("subjectId", "The selected subject does not exist.");
        }

        AssignmentContext existing = repository.findExactAssignment(
                principal.schoolId(), request.classId(), request.teacherUserId(), request.subjectId()
        ).orElse(null);

        long assignmentId;
        boolean created;
        String action;
        Instant now = clock.instant();
        try {
            if (existing == null) {
                assignmentId = repository.insertAssignment(
                        request.classId(), request.teacherUserId(), request.subjectId(), role
                );
                created = true;
                action = "class_assignment.create";
            } else if ("active".equals(existing.status())) {
                if (!role.equals(existing.assignmentRole())) {
                    throw conflict("CLASS_ASSIGNMENT_ROLE_CONFLICT",
                            "This active teacher assignment already exists with a different role.");
                }
                return repository.toAssignmentResponse(existing, false);
            } else {
                String reason = "Reactivated while assigning the same teacher, class, and subject.";
                if (repository.reactivateAssignment(
                        existing.classAssignmentId(), principal.userId(), role, reason, now
                ) != 1) {
                    throw conflict("CLASS_ASSIGNMENT_STATE_CHANGED",
                            "The class assignment changed. Refresh before trying again.");
                }
                assignmentId = existing.classAssignmentId();
                created = false;
                action = "class_assignment.reactivate";
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("CLASS_ASSIGNMENT_CONFLICT",
                    "The class assignment conflicts with existing data.");
        }

        auditService.record(
                principal.userId(), action, "class_assignments", Long.toString(assignmentId),
                "success", metadata,
                Map.of("classId", request.classId(), "teacherUserId", request.teacherUserId(),
                        "subjectId", request.subjectId(), "assignmentRole", role),
                now
        );
        AssignmentContext saved = repository.findAssignment(principal.schoolId(), assignmentId)
                .orElseThrow(() -> new IllegalStateException("The saved class assignment could not be reloaded."));
        if (created) {
            notifyAssignmentCreated(saved, now);
        } else {
            notifyAssignmentReactivated(saved, now);
        }
        return repository.toAssignmentResponse(saved, created);
    }

    @Transactional
    public V3ClassAssignmentResponse archiveAssignment(
            V3AuthenticatedUser principal,
            long classAssignmentId,
            V3ClassAssignmentStatusRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requiredReason(request);
        AssignmentContext assignment = repository.lockAssignment(principal.schoolId(), classAssignmentId)
                .orElseThrow(this::assignmentNotFound);
        if (!"active".equals(assignment.status())) {
            throw conflict("CLASS_ASSIGNMENT_NOT_ACTIVE", "Only an active assignment may be archived.");
        }
        Instant now = clock.instant();
        if (repository.archiveAssignment(classAssignmentId, principal.userId(), reason, now) != 1) {
            throw conflict("CLASS_ASSIGNMENT_STATE_CHANGED",
                    "The class assignment changed. Refresh before trying again.");
        }
        recordAssignmentStatusAudit(principal, assignment, "archived", reason, metadata, now);
        AssignmentContext saved = repository.findAssignment(principal.schoolId(), classAssignmentId)
                .orElseThrow(this::assignmentNotFound);
        notifyAssignmentArchived(saved, now);
        return repository.toAssignmentResponse(saved, false);
    }

    @Transactional
    public V3ClassAssignmentResponse reactivateAssignment(
            V3AuthenticatedUser principal,
            long classAssignmentId,
            V3ClassAssignmentStatusRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requiredReason(request);
        AssignmentContext assignment = repository.lockAssignment(principal.schoolId(), classAssignmentId)
                .orElseThrow(this::assignmentNotFound);
        if ("active".equals(assignment.status())) {
            throw conflict("CLASS_ASSIGNMENT_ALREADY_ACTIVE", "The class assignment is already active.");
        }
        requireActiveClass(requireClass(principal.schoolId(), assignment.classId()));
        if (!repository.activeTeacherExists(principal.schoolId(), assignment.teacherUserId())) {
            throw conflict("TEACHER_ACCOUNT_INACTIVE",
                    "The teacher account must be active and email-verified before reactivation.");
        }
        Instant now = clock.instant();
        if (repository.reactivateAssignment(
                classAssignmentId, principal.userId(), assignment.assignmentRole(), reason, now
        ) != 1) {
            throw conflict("CLASS_ASSIGNMENT_STATE_CHANGED",
                    "The class assignment changed. Refresh before trying again.");
        }
        recordAssignmentStatusAudit(principal, assignment, "active", reason, metadata, now);
        AssignmentContext saved = repository.findAssignment(principal.schoolId(), classAssignmentId)
                .orElseThrow(this::assignmentNotFound);
        notifyAssignmentReactivated(saved, now);
        return repository.toAssignmentResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public List<V3StudentRosterEntryResponse> getPrincipalRoster(
            V3AuthenticatedUser principal,
            long classId,
            String enrollmentStatus
    ) {
        requirePrincipal(principal);
        requireClass(principal.schoolId(), classId);
        return repository.listRoster(
                principal.schoolId(), classId, normalizeEnrollmentStatus(enrollmentStatus)
        );
    }

    @Transactional(readOnly = true)
    public List<V3StudentRosterEntryResponse> getTeacherRoster(
            V3AuthenticatedUser teacher,
            long classId,
            String enrollmentStatus
    ) {
        requireTeacher(teacher);
        if (!repository.teacherOwnsActiveClass(teacher.schoolId(), teacher.userId(), classId)) {
            throw new V3AuthException(
                    "CLASS_ROSTER_FORBIDDEN",
                    "The class roster does not belong to an active assignment of the authenticated teacher.",
                    HttpStatus.FORBIDDEN
            );
        }
        return repository.listRoster(
                teacher.schoolId(), classId, normalizeEnrollmentStatus(enrollmentStatus)
        );
    }

    @Transactional
    public V3ManualStudentEnrollmentResponse enrollStudentManually(
            V3AuthenticatedUser principal,
            long classId,
            V3ManualStudentEnrollmentRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        if (classId <= 0) {
            throw invalid("classId", "This identifier must be positive.");
        }
        ClassContext classContext = requireClass(principal.schoolId(), classId);
        requireActiveClass(classContext);

        String studentLrn = requiredStudentLrn(request == null ? null : request.studentLrn());
        String firstName = requiredTrimmed(request == null ? null : request.firstName(), "firstName", 50);
        String middleName = optionalTrimmed(request == null ? null : request.middleName());
        String lastName = requiredTrimmed(request == null ? null : request.lastName(), "lastName", 50);
        Integer suffixId = request == null ? null : request.suffixId();
        Integer genderId = request == null ? null : request.genderId();
        LocalDate birthDate = normalizeStudentBirthDate(request == null ? null : request.birthDate());
        validateStudentReferences(genderId, suffixId);

        boolean studentCreated = false;
        boolean enrollmentCreated = false;
        boolean enrollmentReactivated = false;
        long studentId;
        long classListId;
        Instant now = clock.instant();

        try {
            StudentContext student = repository.lockStudentByLrn(studentLrn).orElse(null);
            if (student == null) {
                long addressId = repository.insertBlankManualAddress();
                studentId = repository.insertStudent(
                        principal.schoolId(), addressId, genderId, studentLrn,
                        firstName, middleName, lastName, suffixId, birthDate
                );
                studentCreated = true;
                auditService.record(
                        principal.userId(), "student.create", "students", Long.toString(studentId),
                        "success", metadata,
                        Map.of("studentLrn", studentLrn, "source", "manual"), now
                );
            } else {
                requireStudentOwnedBySchool(principal.schoolId(), student);
                requireActiveStudent(student);
                if (!sameStudentIdentity(
                        student, firstName, middleName, lastName, suffixId, genderId, birthDate
                )) {
                    throw conflict(
                            "STUDENT_PROFILE_REVIEW_REQUIRED",
                            "The LRN already exists with different identity details. Review the existing profile instead of overwriting it."
                    );
                }
                studentId = student.studentId();
            }

            EnrollmentContext activeEnrollment = repository.lockActiveEnrollment(
                    principal.schoolId(), studentId, classContext.academicYearId()
            ).orElse(null);
            if (activeEnrollment != null) {
                if (activeEnrollment.classId() != classId) {
                    throw conflict(
                            "STUDENT_ALREADY_ENROLLED",
                            "The student is already actively enrolled in another class for this academic year."
                    );
                }
                classListId = activeEnrollment.classListId();
            } else {
                EnrollmentContext existingMembership = repository.lockMembership(
                        principal.schoolId(), studentId, classId
                ).orElse(null);
                if (existingMembership == null) {
                    String reason = "Manually enrolled by an authorized principal.";
                    classListId = repository.insertManualMembership(
                            UUID.randomUUID().toString(), classId, studentId,
                            classContext.academicYearId(), principal.userId(), reason
                    );
                    enrollmentCreated = true;
                    auditService.record(
                            principal.userId(), "class_list.enroll", "class_lists",
                            Long.toString(classListId), "success", metadata,
                            Map.of("studentId", studentId, "classId", classId,
                                    "academicYearId", classContext.academicYearId(),
                                    "source", "manual"), now
                    );
                } else {
                    String reason = "Re-enrolled manually by an authorized principal.";
                    if (repository.updateMembershipStatus(
                            existingMembership.classListId(), existingMembership.enrollmentStatus(),
                            "enrolled", principal.userId(), reason, now
                    ) != 1) {
                        throw conflict(
                                "CLASS_LIST_STATE_CHANGED",
                                "The enrollment changed. Refresh before trying again."
                        );
                    }
                    classListId = existingMembership.classListId();
                    enrollmentReactivated = true;
                    recordEnrollmentStatusAudit(
                            principal, existingMembership, "enrolled", reason, metadata, now
                    );
                }
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "STUDENT_ENROLLMENT_CONFLICT",
                    "The student or enrollment conflicts with existing data. Refresh and try again."
            );
        }

        return new V3ManualStudentEnrollmentResponse(
                requireRosterEntry(principal.schoolId(), classListId),
                studentCreated, enrollmentCreated, enrollmentReactivated
        );
    }

    @Transactional
    public V3StudentRosterEntryResponse updateStudentProfile(
            V3AuthenticatedUser principal,
            long classId,
            long studentId,
            V3StudentProfileUpdateRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        if (classId <= 0) {
            throw invalid("classId", "This identifier must be positive.");
        }
        if (studentId <= 0) {
            throw invalid("studentId", "This identifier must be positive.");
        }
        requireClass(principal.schoolId(), classId);

        String firstName = requiredTrimmed(request == null ? null : request.firstName(), "firstName", 50);
        String middleName = optionalTrimmed(request == null ? null : request.middleName());
        String lastName = requiredTrimmed(request == null ? null : request.lastName(), "lastName", 50);
        Integer suffixId = request == null ? null : request.suffixId();
        Integer genderId = request == null ? null : request.genderId();
        LocalDate birthDate = normalizeStudentBirthDate(request == null ? null : request.birthDate());
        String reason = requiredTrimmed(request == null ? null : request.reason(), "reason", 255);
        validateStudentReferences(genderId, suffixId);

        StudentContext student = repository.lockStudent(principal.schoolId(), studentId)
                .orElseThrow(this::studentNotFound);
        EnrollmentContext membership = repository.lockMembership(
                principal.schoolId(), studentId, classId
        ).orElseThrow(this::classListNotFound);

        if (sameStudentIdentity(
                student, firstName, middleName, lastName, suffixId, genderId, birthDate
        )) {
            return requireRosterEntry(principal.schoolId(), membership.classListId());
        }

        try {
            if (repository.updateStudentProfile(
                    studentId, genderId, firstName, middleName, lastName, suffixId, birthDate
            ) != 1) {
                throw conflict(
                        "STUDENT_PROFILE_STATE_CHANGED",
                        "The student profile changed. Refresh before trying again."
                );
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "STUDENT_PROFILE_CONFLICT",
                    "The corrected student profile conflicts with existing data."
            );
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("classId", classId);
        details.put("studentLrn", student.studentLrn());
        details.put("previousName", fullName(student.firstName(), student.middleName(), student.lastName()));
        details.put("newName", fullName(firstName, middleName, lastName));
        auditService.record(
                principal.userId(), "student.profile.correct", "students", Long.toString(studentId),
                "success", metadata, details, clock.instant()
        );
        return requireRosterEntry(principal.schoolId(), membership.classListId());
    }

    @Transactional
    public V3StudentRosterEntryResponse updateEnrollmentStatus(
            V3AuthenticatedUser principal,
            long classListId,
            V3StudentEnrollmentStatusRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        if (classListId <= 0) {
            throw invalid("classListId", "This identifier must be positive.");
        }
        String newStatus = normalizeAllowed(
                request == null ? null : request.enrollmentStatus(),
                ENROLLMENT_STATUSES,
                "enrollmentStatus"
        );
        String reason = requiredTrimmed(request == null ? null : request.reason(), "reason", 255);
        EnrollmentContext membership = repository.lockMembership(principal.schoolId(), classListId)
                .orElseThrow(this::classListNotFound);

        if (newStatus.equals(membership.enrollmentStatus())) {
            return requireRosterEntry(principal.schoolId(), classListId);
        }
        if ("enrolled".equals(newStatus)) {
            ClassContext classContext = requireClass(principal.schoolId(), membership.classId());
            requireActiveClass(classContext);
            StudentContext student = repository.lockStudent(principal.schoolId(), membership.studentId())
                    .orElseThrow(this::studentNotFound);
            requireActiveStudent(student);
            EnrollmentContext activeEnrollment = repository.lockActiveEnrollment(
                    principal.schoolId(), membership.studentId(), membership.academicYearId()
            ).orElse(null);
            if (activeEnrollment != null && activeEnrollment.classListId() != classListId) {
                throw conflict(
                        "STUDENT_ALREADY_ENROLLED",
                        "The student is already actively enrolled in another class for this academic year."
                );
            }
        }

        Instant now = clock.instant();
        try {
            if (repository.updateMembershipStatus(
                    classListId, membership.enrollmentStatus(), newStatus,
                    principal.userId(), reason, now
            ) != 1) {
                throw conflict(
                        "CLASS_LIST_STATE_CHANGED",
                        "The enrollment changed. Refresh before trying again."
                );
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "STUDENT_ENROLLMENT_CONFLICT",
                    "The enrollment status conflicts with another active class membership."
            );
        }

        recordEnrollmentStatusAudit(principal, membership, newStatus, reason, metadata, now);
        return requireRosterEntry(principal.schoolId(), classListId);
    }

    public V3ClassResponse createOrReuseClass(
            V3AuthenticatedUser principal,
            int academicYearId,
            int gradeLevelId,
            String sectionName,
            V3RequestMetadata metadata,
            boolean writeAudit
    ) {
        requirePrincipal(principal);
        if (!repository.academicYearExists(principal.schoolId(), academicYearId)) {
            throw invalid("academicYearId", "The selected academic year does not belong to this school.");
        }
        if (!repository.gradeLevelExists(gradeLevelId)) {
            throw invalid("gradeLevelId", "The selected grade level does not exist.");
        }

        ClassContext existing = repository.findClassByContext(
                principal.schoolId(), academicYearId, gradeLevelId, sectionName
        ).orElse(null);
        if (existing != null) {
            requireActiveClass(existing);
            return repository.toClassResponse(existing, false);
        }

        int sectionId = repository.findSectionId(principal.schoolId(), gradeLevelId, sectionName)
                .orElseGet(() -> insertOrReloadSection(principal.schoolId(), gradeLevelId, sectionName));
        long classId;
        try {
            classId = repository.insertClass(academicYearId, sectionId);
        } catch (DataIntegrityViolationException exception) {
            ClassContext raced = repository.findClassByContext(
                    principal.schoolId(), academicYearId, gradeLevelId, sectionName
            ).orElseThrow(() -> conflict("CLASS_CONFLICT",
                    "The class conflicts with existing school data."));
            requireActiveClass(raced);
            return repository.toClassResponse(raced, false);
        }

        if (writeAudit) {
            auditService.record(
                    principal.userId(), "class.create", "classes", Long.toString(classId),
                    "success", metadata,
                    Map.of("academicYearId", academicYearId, "gradeLevelId", gradeLevelId,
                            "sectionName", sectionName),
                    clock.instant()
            );
        }
        ClassContext saved = repository.findClass(principal.schoolId(), classId)
                .orElseThrow(() -> new IllegalStateException("The saved V3 class could not be reloaded."));
        return repository.toClassResponse(saved, true);
    }

    private int insertOrReloadSection(String schoolId, int gradeLevelId, String sectionName) {
        try {
            return repository.insertSection(schoolId, gradeLevelId, sectionName);
        } catch (DataIntegrityViolationException exception) {
            return repository.findSectionId(schoolId, gradeLevelId, sectionName)
                    .orElseThrow(() -> conflict("SECTION_CONFLICT",
                            "The section conflicts with existing school data."));
        }
    }

    private V3SchoolProfileUpdateRequest.Address normalizeAddress(
            V3SchoolProfileUpdateRequest.Address address
    ) {
        if (address == null) {
            throw invalid("address", "School address is required.");
        }
        String source = normalizeAllowed(address.addressSource(), ADDRESS_SOURCES, "address.addressSource");
        validateCodeNamePair(address.regionCode(), address.regionName(), "region");
        validateCodeNamePair(address.provinceCode(), address.provinceName(), "province");
        validateCodeNamePair(address.cityMunicipalityCode(), address.cityMunicipalityName(),
                "cityMunicipality");
        validateCodeNamePair(address.barangayCode(), address.barangayName(), "barangay");
        return new V3SchoolProfileUpdateRequest.Address(
                optionalTrimmed(address.regionCode()), optionalTrimmed(address.regionName()),
                optionalTrimmed(address.provinceCode()), optionalTrimmed(address.provinceName()),
                optionalTrimmed(address.cityMunicipalityCode()),
                optionalTrimmed(address.cityMunicipalityName()),
                optionalTrimmed(address.barangayCode()), optionalTrimmed(address.barangayName()),
                optionalTrimmed(address.addressLine()), optionalTrimmed(address.postalCode()), source
        );
    }

    private void validateCodeNamePair(String code, String name, String field) {
        if ((code == null || code.isBlank()) != (name == null || name.isBlank())) {
            throw invalid("address." + field,
                    "The address code and display name must either both be supplied or both be omitted.");
        }
    }

    private String normalizeContact(String value) {
        String contact = optionalTrimmed(value);
        if (contact == null) {
            return null;
        }
        if (!CONTACT_PATTERN.matcher(contact).matches()) {
            throw invalid("contactNumber", "Use 09XXXXXXXXX or +639XXXXXXXXX.");
        }
        return contact.startsWith("09") ? "+63" + contact.substring(1) : contact;
    }

    private String normalizeEmail(String value) {
        String email = optionalTrimmed(value);
        if (email == null) {
            return null;
        }
        email = email.toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw invalid("email", "Enter a complete valid email address.");
        }
        return email;
    }

    private String normalizeEnrollmentStatus(String value) {
        if (value == null || value.isBlank()) {
            return "enrolled";
        }
        return normalizeAllowed(value, ENROLLMENT_STATUSES, "enrollmentStatus");
    }

    private String requiredReason(V3ClassAssignmentStatusRequest request) {
        return requiredTrimmed(request == null ? null : request.reason(), "reason", 255);
    }

    private String requiredStudentLrn(String value) {
        String studentLrn = requiredTrimmed(value, "studentLrn", 12);
        if (!STUDENT_LRN_PATTERN.matcher(studentLrn).matches()) {
            throw invalid("studentLrn", "The LRN must contain exactly 12 digits.");
        }
        return studentLrn;
    }

    private LocalDate normalizeStudentBirthDate(LocalDate birthDate) {
        if (birthDate != null && !birthDate.isBefore(LocalDate.now(clock))) {
            throw invalid("birthDate", "The birth date must be in the past.");
        }
        return birthDate;
    }

    private void validateStudentReferences(Integer genderId, Integer suffixId) {
        if (genderId == null || genderId <= 0 || !repository.genderExists(genderId)) {
            throw invalid("genderId", "Select a valid gender reference.");
        }
        if (suffixId != null && (suffixId <= 0 || !repository.activeSuffixExists(suffixId))) {
            throw invalid("suffixId", "Select an active suffix reference or omit it.");
        }
    }

    private boolean sameStudentIdentity(
            StudentContext student,
            String firstName,
            String middleName,
            String lastName,
            Integer suffixId,
            int genderId,
            LocalDate birthDate
    ) {
        return equalText(student.firstName(), firstName)
                && equalText(student.middleName(), middleName)
                && equalText(student.lastName(), lastName)
                && Objects.equals(student.suffixId(), suffixId)
                && student.genderId() == genderId
                && Objects.equals(student.birthDate(), birthDate);
    }

    private boolean equalText(String first, String second) {
        return Objects.equals(normalizedComparison(first), normalizedComparison(second));
    }

    private String normalizedComparison(String value) {
        String normalized = optionalTrimmed(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String fullName(String firstName, String middleName, String lastName) {
        return String.join(" ", java.util.stream.Stream.of(firstName, middleName, lastName)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private void requireStudentOwnedBySchool(String schoolId, StudentContext student) {
        if (!schoolId.equals(student.schoolId())) {
            throw conflict(
                    "STUDENT_LRN_OWNED_BY_ANOTHER_SCHOOL",
                    "The LRN is already registered under another school."
            );
        }
    }

    private void requireActiveStudent(StudentContext student) {
        if (!"active".equals(student.status())) {
            throw conflict(
                    "STUDENT_NOT_ACTIVE",
                    "Only an active student profile may be enrolled."
            );
        }
    }

    private V3StudentRosterEntryResponse requireRosterEntry(String schoolId, long classListId) {
        return repository.findRosterEntry(schoolId, classListId)
                .orElseThrow(() -> new IllegalStateException("The saved class-list entry could not be reloaded."));
    }

    private V3SchoolProfileResponse requireSchool(String schoolId) {
        return repository.findSchoolProfile(schoolId).orElseThrow(this::schoolNotFound);
    }

    private ClassContext requireClass(String schoolId, long classId) {
        return repository.findClass(schoolId, classId).orElseThrow(() -> new V3AuthException(
                "CLASS_NOT_FOUND", "The class was not found in the authenticated principal's school.",
                HttpStatus.NOT_FOUND
        ));
    }

    private void requireActiveClass(ClassContext classContext) {
        if (!"active".equals(classContext.status())) {
            throw conflict(
                    "CLASS_NOT_ACTIVE",
                    "Only an active class may receive teacher assignments or SF1 enrollments."
            );
        }
    }

    private void notifyAssignmentCreated(AssignmentContext assignment, Instant now) {
        notificationService.notifyClassAssignmentCreated(
                assignment.teacherUserId(),
                assignment.classAssignmentId(),
                assignment.gradeLevelName(),
                assignment.sectionName(),
                assignment.subjectName(),
                assignment.academicYearName(),
                now
        );
    }

    private void notifyAssignmentArchived(AssignmentContext assignment, Instant now) {
        notificationService.notifyClassAssignmentArchived(
                assignment.teacherUserId(),
                assignment.classAssignmentId(),
                assignment.gradeLevelName(),
                assignment.sectionName(),
                assignment.subjectName(),
                assignment.academicYearName(),
                now
        );
    }

    private void notifyAssignmentReactivated(AssignmentContext assignment, Instant now) {
        notificationService.notifyClassAssignmentReactivated(
                assignment.teacherUserId(),
                assignment.classAssignmentId(),
                assignment.gradeLevelName(),
                assignment.sectionName(),
                assignment.subjectName(),
                assignment.academicYearName(),
                now
        );
    }

    private void recordAssignmentStatusAudit(
            V3AuthenticatedUser principal,
            AssignmentContext assignment,
            String newStatus,
            String reason,
            V3RequestMetadata metadata,
            Instant now
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousStatus", assignment.status());
        details.put("newStatus", newStatus);
        details.put("reason", reason);
        auditService.record(
                principal.userId(), "class_assignment.status", "class_assignments",
                Long.toString(assignment.classAssignmentId()), "success", metadata, details, now
        );
    }

    private void recordEnrollmentStatusAudit(
            V3AuthenticatedUser principal,
            EnrollmentContext membership,
            String newStatus,
            String reason,
            V3RequestMetadata metadata,
            Instant now
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousStatus", membership.enrollmentStatus());
        details.put("newStatus", newStatus);
        details.put("reason", reason);
        details.put("classId", membership.classId());
        details.put("studentId", membership.studentId());
        auditService.record(
                principal.userId(), "class_list.status", "class_lists",
                Long.toString(membership.classListId()), "success", metadata, details, now
        );
    }

    private void requirePrincipal(V3AuthenticatedUser user) {
        requireRole(user, PRINCIPAL_ROLE,
                "PRINCIPAL_ROLE_REQUIRED", "Only an authenticated principal may manage school setup.");
    }

    private void requireTeacher(V3AuthenticatedUser user) {
        requireRole(user, TEACHER_ROLE,
                "TEACHER_ROLE_REQUIRED", "Only an authenticated teacher may retrieve this roster.");
    }

    private void requireRole(V3AuthenticatedUser user, String role, String code, String message) {
        if (user == null) {
            throw new V3AuthException("AUTHENTICATION_REQUIRED", "Authentication is required.",
                    HttpStatus.UNAUTHORIZED);
        }
        if (!role.equalsIgnoreCase(user.role())) {
            throw new V3AuthException(code, message, HttpStatus.FORBIDDEN);
        }
        if (!"active".equalsIgnoreCase(user.status())
                || user.schoolId() == null || user.schoolId().isBlank()) {
            throw new V3AuthException(
                    "ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active account associated with a school is required.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private String normalizeAllowed(String value, Set<String> allowed, String field) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw invalid(field, "Allowed values: " + allowed.stream().sorted().toList());
        }
        return normalized;
    }

    private String requiredTrimmed(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "This field is required.");
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > maxLength) {
            throw invalid(field, "This field may contain at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    private String optionalTrimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private void validateOptionalPositive(Integer value, String field) {
        if (value != null && value <= 0) {
            throw invalid(field, "This identifier must be positive.");
        }
    }

    private V3FieldValidationException invalid(String field, String message) {
        return new V3FieldValidationException(
                "School-setup validation failed.",
                HttpStatus.BAD_REQUEST,
                Map.of("code", "VALIDATION_FAILED", field, message)
        );
    }

    private V3AuthException schoolNotFound() {
        return new V3AuthException(
                "SCHOOL_NOT_FOUND", "The authenticated account's school profile was not found.",
                HttpStatus.NOT_FOUND
        );
    }

    private V3AuthException assignmentNotFound() {
        return new V3AuthException(
                "CLASS_ASSIGNMENT_NOT_FOUND",
                "The class assignment was not found in the principal's school.",
                HttpStatus.NOT_FOUND
        );
    }

    private V3AuthException studentNotFound() {
        return new V3AuthException(
                "STUDENT_NOT_FOUND",
                "The student was not found in the authenticated principal's school.",
                HttpStatus.NOT_FOUND
        );
    }

    private V3AuthException classListNotFound() {
        return new V3AuthException(
                "CLASS_LIST_NOT_FOUND",
                "The class-list entry was not found in the authenticated principal's school.",
                HttpStatus.NOT_FOUND
        );
    }

    private V3AuthException conflict(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.CONFLICT);
    }
}
