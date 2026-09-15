package com.capstone.assessment.v3.schedule.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.schedule.dto.V3ClassScheduleRequest;
import com.capstone.assessment.v3.schedule.dto.V3ClassScheduleResponse;
import com.capstone.assessment.v3.schedule.model.V3ClassScheduleModels.AssignmentContext;
import com.capstone.assessment.v3.schedule.model.V3ClassScheduleModels.ScheduleRow;
import com.capstone.assessment.v3.schedule.repository.V3ClassScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V3ClassScheduleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final V3AuthenticatedUser TEACHER = new V3AuthenticatedUser(
            42L, "SCHOOL-001", "teacher@example.com", "teacher", "active", "session"
    );
    private static final AssignmentContext ASSIGNMENT = new AssignmentContext(
            77L,
            42L,
            "SCHOOL-001",
            "active",
            "active",
            2026,
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2027, 3, 31)
    );

    private final V3ClassScheduleRepository repository = mock(V3ClassScheduleRepository.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private V3ClassScheduleService service;

    @BeforeEach
    void setUp() {
        service = new V3ClassScheduleService(
                repository,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void teacherCreatesOwnedNonOverlappingSchedule() {
        when(repository.findAssignmentContext(77L)).thenReturn(Optional.of(ASSIGNMENT));
        when(repository.insertSchedule(
                anyString(), eq(77L), eq(1), eq(LocalTime.of(8, 0)),
                eq(LocalTime.of(9, 0)), eq("Asia/Manila"),
                eq(LocalDate.of(2026, 6, 1)), isNull(), eq(42L)
        )).thenReturn(300L);
        when(repository.findSchedule(77L, 300L)).thenReturn(Optional.of(scheduleRow()));

        V3ClassScheduleResponse response = service.createSchedule(
                TEACHER,
                77L,
                validRequest(),
                null
        );

        assertEquals(300L, response.classAssignmentScheduleId());
        assertEquals("Monday", response.dayName());
        verify(repository).lockTeacher(42L);
        verify(repository).hasConflictingActiveSchedule(
                42L,
                1,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                LocalDate.of(2026, 6, 1),
                null,
                null
        );
    }

    @Test
    void overlappingTeacherScheduleIsRejected() {
        when(repository.findAssignmentContext(77L)).thenReturn(Optional.of(ASSIGNMENT));
        when(repository.hasConflictingActiveSchedule(
                anyLong(), anyInt(), any(LocalTime.class), any(LocalTime.class),
                any(LocalDate.class), isNull(), isNull()
        )).thenReturn(true);

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.createSchedule(TEACHER, 77L, validRequest(), null)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("TEACHER_TIMETABLE_CONFLICT", exception.getCode());
        verify(repository, never()).insertSchedule(
                anyString(), anyLong(), anyInt(), any(LocalTime.class), any(LocalTime.class),
                anyString(), any(LocalDate.class), any(), anyLong()
        );
    }

    @Test
    void teacherCannotManageAnotherTeachersSchedule() {
        AssignmentContext foreignAssignment = new AssignmentContext(
                77L,
                99L,
                "SCHOOL-001",
                "active",
                "active",
                2026,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2027, 3, 31)
        );
        when(repository.findAssignmentContext(77L)).thenReturn(Optional.of(foreignAssignment));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.createSchedule(TEACHER, 77L, validRequest(), null)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("CLASS_ASSIGNMENT_OWNERSHIP_REQUIRED", exception.getCode());
    }

    @Test
    void scheduleMustStayInsideAcademicYear() {
        when(repository.findAssignmentContext(77L)).thenReturn(Optional.of(ASSIGNMENT));
        V3ClassScheduleRequest request = new V3ClassScheduleRequest(
                1,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                "Asia/Manila",
                LocalDate.of(2026, 5, 31),
                null
        );

        V3FieldValidationException exception = assertThrows(
                V3FieldValidationException.class,
                () -> service.createSchedule(TEACHER, 77L, request, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(
                "The schedule must start inside the assignment academic year.",
                exception.getErrors().get("effectiveFrom")
        );
    }

    private V3ClassScheduleRequest validRequest() {
        return new V3ClassScheduleRequest(
                1,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                "Asia/Manila",
                LocalDate.of(2026, 6, 1),
                null
        );
    }

    private ScheduleRow scheduleRow() {
        return new ScheduleRow(
                300L,
                "schedule-uuid",
                77L,
                1,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                "Asia/Manila",
                LocalDate.of(2026, 6, 1),
                null,
                "active",
                null,
                null,
                null,
                NOW,
                NOW
        );
    }
}
