package com.capstone.assessment.v3.school.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.school.dto.V3AcademicYearRequest;
import com.capstone.assessment.v3.school.dto.V3LifecycleReasonRequest;
import com.capstone.assessment.v3.school.dto.V3TermPeriodRequest;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.AcademicYearRow;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.TermPeriodRow;
import com.capstone.assessment.v3.school.repository.V3AcademicCalendarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V3AcademicCalendarServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final V3AuthenticatedUser PRINCIPAL = new V3AuthenticatedUser(
            7L, "SCHOOL-001", "principal@example.com", "principal", "active", "session"
    );

    private final V3AcademicCalendarRepository repository = mock(V3AcademicCalendarRepository.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private V3AcademicCalendarService service;

    @BeforeEach
    void setUp() {
        service = new V3AcademicCalendarService(
                repository,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void principalCreatesOneSchoolScopedYearWithExactlyFourTerms() {
        when(repository.curriculumExists(1)).thenReturn(true);
        when(repository.insertAcademicYear(
                "SCHOOL-001", 1, "2026-2027",
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 3, 31)
        )).thenReturn(100L);
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("planned")));
        when(repository.listTermPeriods(100)).thenReturn(termRows("planned", "automatic"));

        var response = service.createAcademicYear(PRINCIPAL, validRequest(), null);

        assertEquals(100, response.academicYearId());
        assertEquals(4, response.termPeriods().size());
        verify(repository, times(4)).insertTermPeriod(
                eq(100), anyString(), anyInt(), any(Instant.class), any(Instant.class), eq("automatic")
        );
    }

    @Test
    void invalidTermOrderReturnsFieldValidationInsteadOfServerError() {
        when(repository.curriculumExists(1)).thenReturn(true);
        List<V3TermPeriodRequest> terms = List.of(
                term("First Quarter", 0, "2026-06-01T00:00:00Z", "2026-08-01T00:00:00Z"),
                term("Second Quarter", 2, "2026-08-01T00:00:00Z", "2026-10-01T00:00:00Z"),
                term("Third Quarter", 3, "2026-10-01T00:00:00Z", "2027-01-01T00:00:00Z"),
                term("Fourth Quarter", 4, "2027-01-01T00:00:00Z", "2027-03-31T00:00:00Z")
        );
        V3AcademicYearRequest request = new V3AcademicYearRequest(
                1, "2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 3, 31), terms
        );

        V3FieldValidationException exception = assertThrows(
                V3FieldValidationException.class,
                () -> service.createAcademicYear(PRINCIPAL, request, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(
                "Term orders must be values from 1 through 4.",
                exception.getErrors().get("termPeriods.termOrder")
        );
        verify(repository, never()).insertAcademicYear(
                anyString(), anyInt(), anyString(), any(LocalDate.class), any(LocalDate.class)
        );
    }

    @Test
    void principalCannotActivateSecondTermBeforeFirstIsComplete() {
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("active")));
        when(repository.findTermPeriod(100, 12)).thenReturn(Optional.of(
                termRow(12, 2, "planned", "automatic")
        ));
        when(repository.priorIncompleteTermExists(100, 2)).thenReturn(true);

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.activateTermPeriod(
                        PRINCIPAL, 100, 12, new V3LifecycleReasonRequest("Early manual opening"), null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("PRIOR_TERM_INCOMPLETE", exception.getCode());
        verify(repository, never()).activateTermPeriod(anyInt(), anyLong(), anyString(), any(Instant.class));
    }

    @Test
    void teacherCannotManageAcademicCalendar() {
        V3AuthenticatedUser teacher = new V3AuthenticatedUser(
                8L, "SCHOOL-001", "teacher@example.com", "teacher", "active", "session"
        );

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.listAcademicYears(teacher)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("PRINCIPAL_ROLE_REQUIRED", exception.getCode());
    }

    private V3AcademicYearRequest validRequest() {
        return new V3AcademicYearRequest(
                1,
                "2026-2027",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2027, 3, 31),
                List.of(
                        term("First Quarter", 1, "2026-06-01T00:00:00Z", "2026-08-01T00:00:00Z"),
                        term("Second Quarter", 2, "2026-08-01T00:00:00Z", "2026-10-01T00:00:00Z"),
                        term("Third Quarter", 3, "2026-10-01T00:00:00Z", "2027-01-01T00:00:00Z"),
                        term("Fourth Quarter", 4, "2027-01-01T00:00:00Z", "2027-03-31T00:00:00Z")
                )
        );
    }

    private V3TermPeriodRequest term(String name, int order, String start, String end) {
        return new V3TermPeriodRequest(name, order, Instant.parse(start), Instant.parse(end), "automatic");
    }

    private AcademicYearRow year(String status) {
        return new AcademicYearRow(
                100, "SCHOOL-001", 1, "K to 12", "2026-2027",
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 3, 31), status, NOW, NOW
        );
    }

    private List<TermPeriodRow> termRows(String status, String mode) {
        return List.of(
                termRow(11, 1, status, mode),
                termRow(12, 2, status, mode),
                termRow(13, 3, status, mode),
                termRow(14, 4, status, mode)
        );
    }

    private TermPeriodRow termRow(int id, int order, String status, String mode) {
        Instant start = Instant.parse("2026-06-01T00:00:00Z").plusSeconds((long) (order - 1) * 7_776_000L);
        return new TermPeriodRow(
                id, 100, switch (order) {
                    case 1 -> "First Quarter";
                    case 2 -> "Second Quarter";
                    case 3 -> "Third Quarter";
                    default -> "Fourth Quarter";
                }, order, start, start.plusSeconds(7_776_000L), status, mode,
                null, null, null, null, null
        );
    }
}
