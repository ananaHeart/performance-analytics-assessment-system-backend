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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void principalCreatesOneSchoolScopedYearWithExactlyThreeTermsAndAccurateAuditCount() {
        when(repository.curriculumExists(1)).thenReturn(true);
        when(repository.insertAcademicYear(
                "SCHOOL-001", 1, "2026-2027",
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 3, 31)
        )).thenReturn(100L);
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("planned")));
        when(repository.listTermPeriods(100)).thenReturn(threeTermRows("planned", "automatic"));

        var response = service.createAcademicYear(PRINCIPAL, validRequest(), null);

        assertEquals(100, response.academicYearId());
        assertEquals(3, response.termPeriods().size());
        verify(repository, times(3)).insertTermPeriod(
                eq(100), anyString(), anyInt(), any(Instant.class), any(Instant.class), eq("automatic")
        );
        verify(auditService).record(eq(7L), eq("academic_year.create"), eq("academic_years"), eq("100"),
                eq("success"), any(), eq(Map.of("yearName", "2026-2027", "termCount", 3)), eq(NOW));
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
        assertTrue(exception.getErrors().containsKey("termPeriods"));
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
        when(repository.listTermPeriods(100)).thenReturn(termRows("planned", "automatic"));

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
        return request(List.of(
                term("Term 1", 1, "2026-05-31T16:00:00Z", "2026-09-15T16:00:00Z"),
                term("Term 2", 2, "2026-09-15T16:00:00Z", "2026-12-18T16:00:00Z"),
                term("Term 3", 3, "2027-01-03T16:00:00Z", "2027-03-31T16:00:00Z")
        ));
    }

    private V3AcademicYearRequest legacyRequest() {
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

    @Test
    void legacyPlannedUpdateRetainsFourRowsIdsLabelsAndAuditCount() {
        stubPlannedUpdate(termRows("planned", "automatic"));
        var response = service.updateAcademicYear(PRINCIPAL, 100, legacyRequest(), null);
        assertEquals(List.of(11, 12, 13, 14), response.termPeriods().stream().map(t -> t.termPeriodId()).toList());
        assertEquals("Fourth Quarter", response.termPeriods().get(3).termName());
        verify(repository).updateTermPeriod(eq(100), eq(4), eq("Fourth Quarter"), any(), any(), eq("automatic"));
        verify(repository, never()).insertTermPeriod(anyInt(), anyString(), anyInt(), any(), any(), anyString());
        verify(auditService).record(eq(7L), eq("academic_year.update"), eq("academic_years"), eq("100"),
                eq("success"), any(), eq(Map.of("yearName", "2026-2027", "termCount", 4)), eq(NOW));
    }

    @Test
    void threeTermPlannedUpdateRetainsThreeRowsAndAuditCount() {
        stubPlannedUpdate(threeTermRows("planned", "automatic"));
        var response = service.updateAcademicYear(PRINCIPAL, 100, validRequest(), null);
        assertEquals(3, response.termPeriods().size());
        verify(repository, times(3)).updateTermPeriod(eq(100), anyInt(), anyString(), any(), any(), eq("automatic"));
        verify(auditService).record(eq(7L), eq("academic_year.update"), eq("academic_years"), eq("100"),
                eq("success"), any(), eq(Map.of("yearName", "2026-2027", "termCount", 3)), eq(NOW));
    }

    @Test
    void updatingCannotConvertLegacyYearIntoThreeTerms() {
        stubPlannedUpdate(termRows("planned", "automatic"));
        assertThrows(V3FieldValidationException.class,
                () -> service.updateAcademicYear(PRINCIPAL, 100, validRequest(), null));
        verify(repository, never()).updateAcademicYear(anyInt(), anyInt(), anyString(), any(), any());
    }

    @Test
    void updatingCannotConvertThreeTermYearIntoQuarters() {
        stubPlannedUpdate(threeTermRows("planned", "automatic"));
        assertThrows(V3FieldValidationException.class,
                () -> service.updateAcademicYear(PRINCIPAL, 100, legacyRequest(), null));
        verify(repository, never()).updateAcademicYear(anyInt(), anyInt(), anyString(), any(), any());
    }

    @Test
    void newYearCannotBeCreatedUsingOldFourQuarterFormat() {
        when(repository.curriculumExists(1)).thenReturn(true);
        assertThrows(V3FieldValidationException.class, () -> service.createAcademicYear(PRINCIPAL, legacyRequest(), null));
        verify(repository, never()).insertAcademicYear(anyString(), anyInt(), anyString(), any(), any());
    }

    @Test
    void missingFourthQuarterIsNotAcceptedAsThreeTermYearDuringActivationOrCompletion() {
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("planned")));
        when(repository.listTermPeriods(100)).thenReturn(termRows("planned", "automatic").subList(0, 3));
        assertEquals("TERM_PERIODS_INCOMPLETE", assertThrows(V3AuthException.class,
                () -> service.activateAcademicYear(PRINCIPAL, 100, reason(), null)).getCode());
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("active")));
        when(repository.listTermPeriods(100)).thenReturn(termRows("completed", "automatic").subList(0, 3));
        assertEquals("TERM_PERIODS_INCOMPLETE", assertThrows(V3AuthException.class,
                () -> service.completeAcademicYear(PRINCIPAL, 100, reason(), null)).getCode());
        verify(repository, never()).activateAcademicYear(anyInt());
        verify(repository, never()).completeAcademicYear(anyInt());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothLayoutsCanActivateAndCompleteWhenEveryTermIsComplete(boolean legacy) {
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("planned")));
        when(repository.listTermPeriods(100)).thenReturn(legacy ? termRows("planned", "automatic") : threeTermRows("planned", "automatic"));
        when(repository.activateAcademicYear(100)).thenReturn(1);
        service.activateAcademicYear(PRINCIPAL, 100, reason(), null);
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("active")));
        when(repository.listTermPeriods(100)).thenReturn(legacy ? termRows("completed", "automatic") : threeTermRows("completed", "automatic"));
        when(repository.completeAcademicYear(100)).thenReturn(1);
        service.completeAcademicYear(PRINCIPAL, 100, reason(), null);
        verify(repository).activateAcademicYear(100);
        verify(repository).completeAcademicYear(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "completed"})
    void activeAndCompletedYearsCannotBeEdited(String status) {
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year(status)));
        assertEquals("ACADEMIC_YEAR_LOCKED", assertThrows(V3AuthException.class,
                () -> service.updateAcademicYear(PRINCIPAL, 100, validRequest(), null)).getCode());
        verify(repository, never()).updateAcademicYear(anyInt(), anyInt(), anyString(), any(), any());
    }

    @Test
    void anotherActiveYearOrTermStillBlocksActivation() {
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("planned")));
        when(repository.listTermPeriods(100)).thenReturn(threeTermRows("planned", "automatic"));
        when(repository.anotherActiveAcademicYearExists("SCHOOL-001", 100)).thenReturn(true);
        assertEquals("ACTIVE_ACADEMIC_YEAR_EXISTS", assertThrows(V3AuthException.class,
                () -> service.activateAcademicYear(PRINCIPAL, 100, reason(), null)).getCode());
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("active")));
        when(repository.findTermPeriod(100, 12)).thenReturn(Optional.of(threeTermRows("planned", "automatic").get(1)));
        when(repository.anotherActiveTermExists(100, 12)).thenReturn(true);
        assertEquals("ACTIVE_TERM_PERIOD_EXISTS", assertThrows(V3AuthException.class,
                () -> service.activateTermPeriod(PRINCIPAL, 100, 12, reason(), null)).getCode());
        verify(repository, never()).activateAcademicYear(anyInt());
        verify(repository, never()).activateTermPeriod(anyInt(), anyLong(), anyString(), any());
    }

    @Test
    void principalCannotManageAnotherSchoolsCalendar() {
        when(repository.findAcademicYear("SCHOOL-001", 999)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(V3AuthException.class,
                () -> service.activateAcademicYear(PRINCIPAL, 999, reason(), null)).getStatus());
        verify(repository, never()).activateAcademicYear(anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"overlap", "reversed", "zero", "before-year", "after-year"})
    void rejectsInvalidTermDatesBeforePersistence(String scenario) {
        when(repository.curriculumExists(1)).thenReturn(true);
        List<V3TermPeriodRequest> terms = new ArrayList<>(validRequest().termPeriods());
        V3TermPeriodRequest old = terms.get(1);
        Instant start = switch (scenario) {
            case "overlap" -> terms.get(0).endAt().minusSeconds(1);
            case "before-year" -> terms.get(0).startAt().minusSeconds(1);
            default -> old.startAt();
        };
        Instant end = switch (scenario) {
            case "reversed" -> start.minusSeconds(1);
            case "zero" -> start;
            case "after-year" -> terms.get(2).endAt().plusSeconds(1);
            default -> old.endAt();
        };
        terms.set(1, new V3TermPeriodRequest("Term 2", 2, start, end, "automatic"));
        assertThrows(V3FieldValidationException.class, () -> service.createAcademicYear(PRINCIPAL, request(terms), null));
        verify(repository, never()).insertAcademicYear(anyString(), anyInt(), anyString(), any(), any());
    }

    private void stubPlannedUpdate(List<TermPeriodRow> terms) {
        when(repository.findAcademicYear("SCHOOL-001", 100)).thenReturn(Optional.of(year("planned")));
        when(repository.listTermPeriods(100)).thenReturn(terms);
        when(repository.curriculumExists(1)).thenReturn(true);
        when(repository.updateAcademicYear(anyInt(), anyInt(), anyString(), any(), any())).thenReturn(1);
        when(repository.updateTermPeriod(anyInt(), anyInt(), anyString(), any(), any(), anyString())).thenReturn(1);
    }

    private V3LifecycleReasonRequest reason() {
        return new V3LifecycleReasonRequest("Principal reviewed calendar.");
    }

    private V3AcademicYearRequest request(List<V3TermPeriodRequest> terms) {
        return new V3AcademicYearRequest(1, "2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 3, 31), terms);
    }

    private List<TermPeriodRow> threeTermRows(String status, String mode) {
        return validRequest().termPeriods().stream().map(term -> new TermPeriodRow(
                10 + term.termOrder(), 100, term.termName(), term.termOrder(), term.startAt(), term.endAt(),
                status, mode, null, null, null, null, null)).toList();
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
