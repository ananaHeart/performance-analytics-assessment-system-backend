package com.capstone.assessment.v3.school.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import com.capstone.assessment.v3.school.dto.V3ClassAssignmentRequest;
import com.capstone.assessment.v3.school.dto.V3ClassAssignmentStatusRequest;
import com.capstone.assessment.v3.school.dto.V3ClassRequest;
import com.capstone.assessment.v3.school.dto.V3ManualStudentEnrollmentRequest;
import com.capstone.assessment.v3.school.dto.V3ManualStudentEnrollmentResponse;
import com.capstone.assessment.v3.school.dto.V3StudentEnrollmentStatusRequest;
import com.capstone.assessment.v3.school.dto.V3StudentProfileUpdateRequest;
import com.capstone.assessment.v3.school.dto.V3StudentRosterEntryResponse;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.AssignmentContext;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.ClassContext;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.EnrollmentContext;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.StudentContext;
import com.capstone.assessment.v3.school.repository.V3SchoolSetupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V3SchoolSetupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final V3AuthenticatedUser PRINCIPAL = new V3AuthenticatedUser(
            11L, "SCHOOL-001", "principal@example.com", "principal", "active", "session"
    );
    private static final V3AuthenticatedUser TEACHER = new V3AuthenticatedUser(
            22L, "SCHOOL-001", "teacher@example.com", "teacher", "active", "session"
    );
    private static final V3RequestMetadata METADATA = new V3RequestMetadata(
            "127.0.0.1", "JUnit", null
    );

    private final V3SchoolSetupRepository repository = mock(V3SchoolSetupRepository.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private final V3NotificationService notificationService = mock(V3NotificationService.class);
    private V3SchoolSetupService service;

    @BeforeEach
    void setUp() {
        service = new V3SchoolSetupService(
                repository,
                auditService,
                notificationService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void archivedClassCannotBeReusedForSf1OrManualClassCreation() {
        when(repository.academicYearExists("SCHOOL-001", 2026)).thenReturn(true);
        when(repository.gradeLevelExists(8)).thenReturn(true);
        when(repository.findClassByContext("SCHOOL-001", 2026, 8, "Narra"))
                .thenReturn(Optional.of(classContext("archived")));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.createClass(
                        PRINCIPAL,
                        new V3ClassRequest(2026, 8, "Narra"),
                        METADATA
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("CLASS_NOT_ACTIVE", exception.getCode());
        verify(repository, never()).insertClass(2026, 80);
        verifyNoInteractions(auditService);
    }

    @Test
    void teacherCannotBeAssignedToArchivedClass() {
        when(repository.findClass("SCHOOL-001", 700L))
                .thenReturn(Optional.of(classContext("archived")));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.assignTeacher(
                        PRINCIPAL,
                        new V3ClassAssignmentRequest(700L, 22L, 1, "primary"),
                        METADATA
                )
        );

        assertEquals("CLASS_NOT_ACTIVE", exception.getCode());
        verify(repository, never()).insertAssignment(700L, 22L, 1, "primary");
        verifyNoInteractions(auditService);
    }

    @Test
    void assignmentCannotBeReactivatedWhenItsClassIsArchived() {
        AssignmentContext archivedAssignment = assignment("archived");
        when(repository.lockAssignment("SCHOOL-001", 900L))
                .thenReturn(Optional.of(archivedAssignment));
        when(repository.findClass("SCHOOL-001", 700L))
                .thenReturn(Optional.of(classContext("archived")));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.reactivateAssignment(
                        PRINCIPAL,
                        900L,
                        new V3ClassAssignmentStatusRequest("Reassigning teacher for the new term."),
                        METADATA
                )
        );

        assertEquals("CLASS_NOT_ACTIVE", exception.getCode());
        verify(repository, never()).reactivateAssignment(
                900L, 11L, "primary", "Reassigning teacher for the new term.", NOW
        );
        verifyNoInteractions(auditService);
    }

    @Test
    void creatingAssignmentNotifiesTheAssignedTeacher() {
        AssignmentContext activeAssignment = assignment("active");
        when(repository.findClass("SCHOOL-001", 700L))
                .thenReturn(Optional.of(classContext("active")));
        when(repository.activeTeacherExists("SCHOOL-001", 22L)).thenReturn(true);
        when(repository.subjectExists(1)).thenReturn(true);
        when(repository.findExactAssignment("SCHOOL-001", 700L, 22L, 1))
                .thenReturn(Optional.empty());
        when(repository.insertAssignment(700L, 22L, 1, "primary")).thenReturn(900L);
        when(repository.findAssignment("SCHOOL-001", 900L))
                .thenReturn(Optional.of(activeAssignment));

        service.assignTeacher(
                PRINCIPAL,
                new V3ClassAssignmentRequest(700L, 22L, 1, "primary"),
                METADATA
        );

        verify(notificationService).notifyClassAssignmentCreated(
                22L, 900L, "Grade 8", "Narra", "Science", "2025-2026", NOW
        );
    }

    @Test
    void duplicateActiveAssignmentDoesNotCreateAnotherNotification() {
        AssignmentContext activeAssignment = assignment("active");
        when(repository.findClass("SCHOOL-001", 700L))
                .thenReturn(Optional.of(classContext("active")));
        when(repository.activeTeacherExists("SCHOOL-001", 22L)).thenReturn(true);
        when(repository.subjectExists(1)).thenReturn(true);
        when(repository.findExactAssignment("SCHOOL-001", 700L, 22L, 1))
                .thenReturn(Optional.of(activeAssignment));

        service.assignTeacher(
                PRINCIPAL,
                new V3ClassAssignmentRequest(700L, 22L, 1, "primary"),
                METADATA
        );

        verifyNoInteractions(notificationService, auditService);
        verify(repository, never()).insertAssignment(anyLong(), anyLong(), anyInt(), any(String.class));
    }

    @Test
    void archivingAssignmentNotifiesTheAssignedTeacher() {
        AssignmentContext activeAssignment = assignment("active");
        AssignmentContext archivedAssignment = assignment("archived");
        when(repository.lockAssignment("SCHOOL-001", 900L))
                .thenReturn(Optional.of(activeAssignment));
        when(repository.archiveAssignment(
                900L, 11L, "Teacher changed assignment.", NOW
        )).thenReturn(1);
        when(repository.findAssignment("SCHOOL-001", 900L))
                .thenReturn(Optional.of(archivedAssignment));

        service.archiveAssignment(
                PRINCIPAL,
                900L,
                new V3ClassAssignmentStatusRequest("Teacher changed assignment."),
                METADATA
        );

        verify(notificationService).notifyClassAssignmentArchived(
                22L, 900L, "Grade 8", "Narra", "Science", "2025-2026", NOW
        );
    }

    @Test
    void reactivatingAssignmentNotifiesTheAssignedTeacher() {
        AssignmentContext archivedAssignment = assignment("archived");
        AssignmentContext activeAssignment = assignment("active");
        when(repository.lockAssignment("SCHOOL-001", 900L))
                .thenReturn(Optional.of(archivedAssignment));
        when(repository.findClass("SCHOOL-001", 700L))
                .thenReturn(Optional.of(classContext("active")));
        when(repository.activeTeacherExists("SCHOOL-001", 22L)).thenReturn(true);
        when(repository.reactivateAssignment(
                900L, 11L, "primary", "Teacher returned to assignment.", NOW
        )).thenReturn(1);
        when(repository.findAssignment("SCHOOL-001", 900L))
                .thenReturn(Optional.of(activeAssignment));

        service.reactivateAssignment(
                PRINCIPAL,
                900L,
                new V3ClassAssignmentStatusRequest("Teacher returned to assignment."),
                METADATA
        );

        verify(notificationService).notifyClassAssignmentReactivated(
                22L, 900L, "Grade 8", "Narra", "Science", "2025-2026", NOW
        );
    }

    @Test
    void teacherRosterRequiresAnActiveOwnedClassAssignment() {
        when(repository.teacherOwnsActiveClass("SCHOOL-001", 22L, 700L)).thenReturn(false);

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.getTeacherRoster(TEACHER, 700L, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("CLASS_ROSTER_FORBIDDEN", exception.getCode());
        verify(repository, never()).listRoster("SCHOOL-001", 700L, "enrolled");
    }

    @Test
    void teacherCanReadOnlyTheRosterOfAnActiveOwnedClass() {
        List<V3StudentRosterEntryResponse> roster = List.of();
        when(repository.teacherOwnsActiveClass("SCHOOL-001", 22L, 700L)).thenReturn(true);
        when(repository.listRoster("SCHOOL-001", 700L, "enrolled")).thenReturn(roster);

        assertEquals(roster, service.getTeacherRoster(TEACHER, 700L, null));
        verify(repository).listRoster("SCHOOL-001", 700L, "enrolled");
    }

    @Test
    void principalCannotReadAnotherSchoolsRoster() {
        when(repository.findClass("SCHOOL-001", 999L)).thenReturn(Optional.empty());

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.getPrincipalRoster(PRINCIPAL, 999L, null)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("CLASS_NOT_FOUND", exception.getCode());
        verify(repository, never()).listRoster("SCHOOL-001", 999L, "enrolled");
    }

    @Test
    void teacherCannotManuallyEnrollAStudent() {
        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.enrollStudentManually(TEACHER, 700L, enrollmentRequest(), METADATA)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("PRINCIPAL_ROLE_REQUIRED", exception.getCode());
        verifyNoInteractions(repository, auditService);
    }

    @Test
    void principalCanCreateAndManuallyEnrollAStudent() {
        V3StudentRosterEntryResponse rosterEntry = rosterEntry("enrolled", "manual");
        when(repository.findClass("SCHOOL-001", 700L)).thenReturn(Optional.of(classContext("active")));
        when(repository.genderExists(2)).thenReturn(true);
        when(repository.lockStudentByLrn("100000000001")).thenReturn(Optional.empty());
        when(repository.insertBlankManualAddress()).thenReturn(500L);
        when(repository.insertStudent(
                "SCHOOL-001", 500L, 2, "100000000001", "Juan", "Santos", "Dela Cruz",
                null, LocalDate.of(2012, 1, 2)
        )).thenReturn(600L);
        when(repository.lockActiveEnrollment("SCHOOL-001", 600L, 2026))
                .thenReturn(Optional.empty());
        when(repository.lockMembership("SCHOOL-001", 600L, 700L)).thenReturn(Optional.empty());
        when(repository.insertManualMembership(
                any(String.class), eq(700L), eq(600L), eq(2026), eq(11L),
                eq("Manually enrolled by an authorized principal.")
        )).thenReturn(800L);
        when(repository.findRosterEntry("SCHOOL-001", 800L)).thenReturn(Optional.of(rosterEntry));

        V3ManualStudentEnrollmentResponse response = service.enrollStudentManually(
                PRINCIPAL, 700L, enrollmentRequest(), METADATA
        );

        assertTrue(response.studentCreated());
        assertTrue(response.enrollmentCreated());
        assertFalse(response.enrollmentReactivated());
        assertEquals(rosterEntry, response.student());
        verify(auditService, times(2)).record(
                eq(11L), any(String.class), any(String.class), any(String.class),
                eq("success"), eq(METADATA), org.mockito.ArgumentMatchers.<Map<String, ?>>any(), eq(NOW)
        );
    }

    @Test
    void repeatedManualEnrollmentOfSameStudentAndClassIsIdempotent() {
        StudentContext student = student("SCHOOL-001", "active");
        EnrollmentContext enrollment = enrollment(800L, 700L, "enrolled");
        V3StudentRosterEntryResponse rosterEntry = rosterEntry("enrolled", "manual");
        prepareExistingStudent(student);
        when(repository.lockActiveEnrollment("SCHOOL-001", 600L, 2026))
                .thenReturn(Optional.of(enrollment));
        when(repository.findRosterEntry("SCHOOL-001", 800L)).thenReturn(Optional.of(rosterEntry));

        V3ManualStudentEnrollmentResponse response = service.enrollStudentManually(
                PRINCIPAL, 700L, enrollmentRequest(), METADATA
        );

        assertFalse(response.studentCreated());
        assertFalse(response.enrollmentCreated());
        assertFalse(response.enrollmentReactivated());
        verify(repository, never()).insertManualMembership(
                any(String.class), anyLong(), anyLong(), anyInt(), anyLong(), any(String.class)
        );
        verifyNoInteractions(auditService);
    }

    @Test
    void studentCannotBeActivelyEnrolledInTwoClassesInTheSameAcademicYear() {
        prepareExistingStudent(student("SCHOOL-001", "active"));
        when(repository.lockActiveEnrollment("SCHOOL-001", 600L, 2026))
                .thenReturn(Optional.of(enrollment(801L, 701L, "enrolled")));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.enrollStudentManually(PRINCIPAL, 700L, enrollmentRequest(), METADATA)
        );

        assertEquals("STUDENT_ALREADY_ENROLLED", exception.getCode());
        verify(repository, never()).insertManualMembership(
                any(String.class), anyLong(), anyLong(), anyInt(), anyLong(), any(String.class)
        );
        verifyNoInteractions(auditService);
    }

    @Test
    void lrnOwnedByAnotherSchoolIsRejected() {
        when(repository.findClass("SCHOOL-001", 700L)).thenReturn(Optional.of(classContext("active")));
        when(repository.genderExists(2)).thenReturn(true);
        when(repository.lockStudentByLrn("100000000001"))
                .thenReturn(Optional.of(student("SCHOOL-OTHER", "active")));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.enrollStudentManually(PRINCIPAL, 700L, enrollmentRequest(), METADATA)
        );

        assertEquals("STUDENT_LRN_OWNED_BY_ANOTHER_SCHOOL", exception.getCode());
        verifyNoInteractions(auditService);
    }

    @Test
    void existingLrnWithDifferentIdentityRequiresExplicitProfileReview() {
        StudentContext different = new StudentContext(
                600L, "SCHOOL-001", 500L, 2, "100000000001",
                "Pedro", "Santos", "Dela Cruz", null, LocalDate.of(2012, 1, 2), "active"
        );
        when(repository.findClass("SCHOOL-001", 700L)).thenReturn(Optional.of(classContext("active")));
        when(repository.genderExists(2)).thenReturn(true);
        when(repository.lockStudentByLrn("100000000001")).thenReturn(Optional.of(different));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.enrollStudentManually(PRINCIPAL, 700L, enrollmentRequest(), METADATA)
        );

        assertEquals("STUDENT_PROFILE_REVIEW_REQUIRED", exception.getCode());
        verify(repository, never()).updateStudentProfile(
                anyLong(), anyInt(), any(String.class), any(), any(String.class), any(), any()
        );
    }

    @Test
    void principalCanCorrectStudentProfileWithReasonAndAudit() {
        StudentContext student = student("SCHOOL-001", "active");
        EnrollmentContext membership = enrollment(800L, 700L, "enrolled");
        V3StudentRosterEntryResponse corrected = rosterEntry("enrolled", "manual");
        when(repository.findClass("SCHOOL-001", 700L)).thenReturn(Optional.of(classContext("active")));
        when(repository.genderExists(1)).thenReturn(true);
        when(repository.lockStudent("SCHOOL-001", 600L)).thenReturn(Optional.of(student));
        when(repository.lockMembership("SCHOOL-001", 600L, 700L)).thenReturn(Optional.of(membership));
        when(repository.updateStudentProfile(
                600L, 1, "Maria", null, "Dela Cruz", null, LocalDate.of(2012, 1, 2)
        )).thenReturn(1);
        when(repository.findRosterEntry("SCHOOL-001", 800L)).thenReturn(Optional.of(corrected));

        V3StudentRosterEntryResponse response = service.updateStudentProfile(
                PRINCIPAL,
                700L,
                600L,
                new V3StudentProfileUpdateRequest(
                        "Maria", null, "Dela Cruz", null, 1,
                        LocalDate.of(2012, 1, 2), "Corrected against the official SF1."
                ),
                METADATA
        );

        assertEquals(corrected, response);
        verify(auditService).record(
                eq(11L), eq("student.profile.correct"), eq("students"), eq("600"),
                eq("success"), eq(METADATA), org.mockito.ArgumentMatchers.<Map<String, ?>>any(), eq(NOW)
        );
    }

    @Test
    void principalCanDropEnrollmentWithoutDeletingHistory() {
        EnrollmentContext membership = enrollment(800L, 700L, "enrolled");
        V3StudentRosterEntryResponse dropped = rosterEntry("dropped", "manual");
        when(repository.lockMembership("SCHOOL-001", 800L)).thenReturn(Optional.of(membership));
        when(repository.updateMembershipStatus(
                800L, "enrolled", "dropped", 11L, "Learner officially transferred out.", NOW
        )).thenReturn(1);
        when(repository.findRosterEntry("SCHOOL-001", 800L)).thenReturn(Optional.of(dropped));

        V3StudentRosterEntryResponse response = service.updateEnrollmentStatus(
                PRINCIPAL,
                800L,
                new V3StudentEnrollmentStatusRequest(
                        "dropped", "Learner officially transferred out."
                ),
                METADATA
        );

        assertEquals("dropped", response.enrollmentStatus());
        verify(auditService).record(
                eq(11L), eq("class_list.status"), eq("class_lists"), eq("800"),
                eq("success"), eq(METADATA), org.mockito.ArgumentMatchers.<Map<String, ?>>any(), eq(NOW)
        );
    }

    @Test
    void reEnrollmentIsRejectedWhenAnotherActiveClassMembershipExists() {
        EnrollmentContext ended = enrollment(800L, 700L, "dropped");
        when(repository.lockMembership("SCHOOL-001", 800L)).thenReturn(Optional.of(ended));
        when(repository.findClass("SCHOOL-001", 700L)).thenReturn(Optional.of(classContext("active")));
        when(repository.lockStudent("SCHOOL-001", 600L))
                .thenReturn(Optional.of(student("SCHOOL-001", "active")));
        when(repository.lockActiveEnrollment("SCHOOL-001", 600L, 2026))
                .thenReturn(Optional.of(enrollment(801L, 701L, "enrolled")));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.updateEnrollmentStatus(
                        PRINCIPAL,
                        800L,
                        new V3StudentEnrollmentStatusRequest(
                                "enrolled", "Returning learner to the original class."
                        ),
                        METADATA
                )
        );

        assertEquals("STUDENT_ALREADY_ENROLLED", exception.getCode());
        verify(repository, never()).updateMembershipStatus(
                anyLong(), any(String.class), any(String.class), anyLong(), any(String.class), any(Instant.class)
        );
    }

    @Test
    void databaseConstraintConflictDoesNotReturnAFalseSuccess() {
        when(repository.findClass("SCHOOL-001", 700L)).thenReturn(Optional.of(classContext("active")));
        when(repository.genderExists(2)).thenReturn(true);
        when(repository.lockStudentByLrn("100000000001")).thenReturn(Optional.empty());
        when(repository.insertBlankManualAddress()).thenReturn(500L);
        when(repository.insertStudent(
                "SCHOOL-001", 500L, 2, "100000000001", "Juan", "Santos", "Dela Cruz",
                null, LocalDate.of(2012, 1, 2)
        )).thenThrow(new DataIntegrityViolationException("duplicate LRN"));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.enrollStudentManually(PRINCIPAL, 700L, enrollmentRequest(), METADATA)
        );

        assertEquals("STUDENT_ENROLLMENT_CONFLICT", exception.getCode());
    }

    private ClassContext classContext(String status) {
        return new ClassContext(
                700L, "SCHOOL-001", 2026, "2025-2026", 80, 8,
                "Grade 8", "Narra", status, 0
        );
    }

    private AssignmentContext assignment(String status) {
        return new AssignmentContext(
                900L, "SCHOOL-001", 700L, 22L, "Teacher One", 1, "Science",
                2026, "2025-2026", 8, "Grade 8", 80, "Narra", "primary",
                status, NOW.minusSeconds(3600), NOW.minusSeconds(60), "Previous archive"
        );
    }

    private V3ManualStudentEnrollmentRequest enrollmentRequest() {
        return new V3ManualStudentEnrollmentRequest(
                "100000000001", "Juan", "Santos", "Dela Cruz", null, 2,
                LocalDate.of(2012, 1, 2)
        );
    }

    private StudentContext student(String schoolId, String status) {
        return new StudentContext(
                600L, schoolId, 500L, 2, "100000000001",
                "Juan", "Santos", "Dela Cruz", null, LocalDate.of(2012, 1, 2), status
        );
    }

    private EnrollmentContext enrollment(long classListId, long classId, String status) {
        return new EnrollmentContext(
                classListId, "membership-uuid", classId, 600L, 2026, status, "manual",
                NOW.minusSeconds(3600), "enrolled".equals(status) ? null : NOW.minusSeconds(60),
                "Enrollment status reason", 11L
        );
    }

    private V3StudentRosterEntryResponse rosterEntry(String enrollmentStatus, String source) {
        return new V3StudentRosterEntryResponse(
                800L, "membership-uuid", 600L, "100000000001",
                "Juan", "Santos", "Dela Cruz", null, "Juan Santos Dela Cruz", "Male",
                LocalDate.of(2012, 1, 2), "active", enrollmentStatus, source,
                NOW.minusSeconds(3600), "enrolled".equals(enrollmentStatus) ? null : NOW,
                "Enrollment status reason", 11L, NOW
        );
    }

    private void prepareExistingStudent(StudentContext student) {
        when(repository.findClass("SCHOOL-001", 700L)).thenReturn(Optional.of(classContext("active")));
        when(repository.genderExists(2)).thenReturn(true);
        when(repository.lockStudentByLrn("100000000001")).thenReturn(Optional.of(student));
    }
}
