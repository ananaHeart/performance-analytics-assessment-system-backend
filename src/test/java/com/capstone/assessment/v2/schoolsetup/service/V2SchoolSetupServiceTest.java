package com.capstone.assessment.v2.schoolsetup.service;

import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
import com.capstone.assessment.v2.schoolsetup.dto.V2ClassAssignmentResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2CreateClassAssignmentRequest;
import com.capstone.assessment.v2.schoolsetup.model.V2ClassContext;
import com.capstone.assessment.v2.schoolsetup.repository.V2SchoolSetupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2SchoolSetupServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");
    private static final V2RequestMetadata METADATA = new V2RequestMetadata(
            "203.0.113.30",
            "JUnit",
            "principal-device"
    );
    private static final V2AuthenticatedUser PRINCIPAL = new V2AuthenticatedUser(
            10L,
            "SCHOOL-001",
            "principal@example.com",
            "principal",
            "active",
            "principal-session"
    );

    @Mock
    private V2SchoolSetupRepository schoolSetupRepository;

    @Mock
    private V2AuthRepository authRepository;

    private V2SchoolSetupService schoolSetupService;

    @BeforeEach
    void setUp() {
        schoolSetupService = new V2SchoolSetupService(
                schoolSetupRepository,
                authRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void principalCreatesPrimaryAssignmentForAvailableClass() {
        V2CreateClassAssignmentRequest request = request("primary");
        V2ClassAssignmentResponse expected = assignment("primary");
        allowAssignment(request);
        when(schoolSetupRepository.activeAssignmentExists(100L, 20L, 3)).thenReturn(false);
        when(schoolSetupRepository.activePrimaryAssignmentExists(100L, 3)).thenReturn(false);
        when(schoolSetupRepository.insertAssignment(100L, 20L, 3, "primary", NOW)).thenReturn(500L);
        when(schoolSetupRepository.findAssignment(500L, "SCHOOL-001")).thenReturn(Optional.of(expected));

        V2ClassAssignmentResponse response = schoolSetupService.createAssignment(PRINCIPAL, request, METADATA);

        assertEquals(expected, response);
        verify(schoolSetupRepository).lockClass(100L);
        verify(authRepository).recordAudit(
                any(String.class),
                eq(10L),
                eq("CREATE_CLASS_ASSIGNMENT"),
                eq("class_assignments"),
                eq("500"),
                eq("success"),
                eq("203.0.113.30"),
                eq("principal-device"),
                eq("JUnit"),
                eq("{\"classId\":100,\"teacherUserId\":20,\"subjectId\":3,\"assignmentRole\":\"primary\"}"),
                eq(NOW)
        );
    }

    @Test
    void nonPrincipalCannotCreateAssignment() {
        V2AuthenticatedUser teacher = new V2AuthenticatedUser(
                11L,
                "SCHOOL-001",
                "teacher@example.com",
                "teacher",
                "active",
                "teacher-session"
        );

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> schoolSetupService.createAssignment(teacher, request("primary"), METADATA)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        verify(schoolSetupRepository, never()).findClassContext(anyLong());
    }

    @Test
    void teacherOutsidePrincipalSchoolIsRejected() {
        when(schoolSetupRepository.findClassContext(100L)).thenReturn(Optional.of(classContext()));
        when(schoolSetupRepository.activeTeacherExistsInSchool(20L, "SCHOOL-001")).thenReturn(false);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> schoolSetupService.createAssignment(PRINCIPAL, request("primary"), METADATA)
        );

        assertEquals("TEACHER_NOT_AVAILABLE", exception.getCode());
        verify(schoolSetupRepository, never()).insertAssignment(
                anyLong(), anyLong(), anyInt(), anyString(), any(Instant.class)
        );
    }

    @Test
    void classWithoutEnrolledStudentsIsRejected() {
        V2CreateClassAssignmentRequest request = request("primary");
        when(schoolSetupRepository.findClassContext(100L)).thenReturn(Optional.of(classContext()));
        when(schoolSetupRepository.activeTeacherExistsInSchool(20L, "SCHOOL-001")).thenReturn(true);
        when(schoolSetupRepository.subjectExists(3)).thenReturn(true);
        when(schoolSetupRepository.classHasEnrolledStudents(100L, "SCHOOL-001")).thenReturn(false);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> schoolSetupService.createAssignment(PRINCIPAL, request, METADATA)
        );

        assertEquals("CLASS_HAS_NO_ENROLLED_STUDENTS", exception.getCode());
        verify(schoolSetupRepository, never()).lockClass(100L);
    }

    @Test
    void duplicateActiveAssignmentIsRejected() {
        V2CreateClassAssignmentRequest request = request("primary");
        allowAssignment(request);
        when(schoolSetupRepository.activeAssignmentExists(100L, 20L, 3)).thenReturn(true);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> schoolSetupService.createAssignment(PRINCIPAL, request, METADATA)
        );

        assertEquals("DUPLICATE_CLASS_ASSIGNMENT", exception.getCode());
        verify(schoolSetupRepository, never()).insertAssignment(
                anyLong(), anyLong(), anyInt(), anyString(), any(Instant.class)
        );
    }

    @Test
    void secondPrimaryTeacherForSameClassAndSubjectIsRejected() {
        V2CreateClassAssignmentRequest request = request("primary");
        allowAssignment(request);
        when(schoolSetupRepository.activeAssignmentExists(100L, 20L, 3)).thenReturn(false);
        when(schoolSetupRepository.activePrimaryAssignmentExists(100L, 3)).thenReturn(true);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> schoolSetupService.createAssignment(PRINCIPAL, request, METADATA)
        );

        assertEquals("PRIMARY_ASSIGNMENT_EXISTS", exception.getCode());
        verify(schoolSetupRepository, never()).insertAssignment(
                anyLong(), anyLong(), anyInt(), anyString(), any(Instant.class)
        );
    }

    @Test
    void coTeacherCanBeAddedWhenPrimaryTeacherAlreadyExists() {
        V2CreateClassAssignmentRequest request = request("co_teacher");
        V2ClassAssignmentResponse expected = assignment("co_teacher");
        allowAssignment(request);
        when(schoolSetupRepository.activeAssignmentExists(100L, 20L, 3)).thenReturn(false);
        when(schoolSetupRepository.insertAssignment(100L, 20L, 3, "co_teacher", NOW)).thenReturn(500L);
        when(schoolSetupRepository.findAssignment(500L, "SCHOOL-001")).thenReturn(Optional.of(expected));

        V2ClassAssignmentResponse response = schoolSetupService.createAssignment(PRINCIPAL, request, METADATA);

        assertEquals("co_teacher", response.assignmentRole());
        verify(schoolSetupRepository, never()).activePrimaryAssignmentExists(100L, 3);
    }

    private void allowAssignment(V2CreateClassAssignmentRequest request) {
        when(schoolSetupRepository.findClassContext(request.classId())).thenReturn(Optional.of(classContext()));
        when(schoolSetupRepository.activeTeacherExistsInSchool(request.teacherUserId(), "SCHOOL-001"))
                .thenReturn(true);
        when(schoolSetupRepository.subjectExists(request.subjectId())).thenReturn(true);
        when(schoolSetupRepository.classHasEnrolledStudents(request.classId(), "SCHOOL-001"))
                .thenReturn(true);
    }

    private V2CreateClassAssignmentRequest request(String role) {
        return new V2CreateClassAssignmentRequest(100L, 20L, 3, role);
    }

    private V2ClassContext classContext() {
        return new V2ClassContext(
                100L,
                1,
                "2026-2027",
                1,
                "Grade 7",
                10,
                "Rizal",
                "active"
        );
    }

    private V2ClassAssignmentResponse assignment(String role) {
        return new V2ClassAssignmentResponse(
                500L,
                100L,
                1,
                "2026-2027",
                1,
                "Grade 7",
                10,
                "Rizal",
                20L,
                "Maria Santos",
                3,
                "English",
                role,
                "active",
                NOW
        );
    }
}
