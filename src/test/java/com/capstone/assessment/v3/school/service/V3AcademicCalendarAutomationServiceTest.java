package com.capstone.assessment.v3.school.service;

import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.AcademicYearRow;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.TermPeriodRow;
import com.capstone.assessment.v3.school.repository.V3AcademicCalendarRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V3AcademicCalendarAutomationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final String SCHOOL_ID = "SCHOOL-001";

    private final V3AcademicCalendarRepository repository = mock(V3AcademicCalendarRepository.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private final V3AcademicCalendarAutomationService service =
            new V3AcademicCalendarAutomationService(repository, auditService);

    @Test
    void automaticallyActivatesDueAcademicYearAndCurrentTerm() {
        AcademicYearRow planned = year("planned", LocalDate.of(2027, 3, 31));
        AcademicYearRow active = year("active", LocalDate.of(2027, 3, 31));
        List<TermPeriodRow> terms = List.of(
                term(11, 1, "planned", "automatic", "2026-06-01T00:00:00Z", "2026-10-01T00:00:00Z"),
                term(12, 2, "planned", "automatic", "2026-10-01T00:00:00Z", "2026-12-01T00:00:00Z"),
                term(13, 3, "planned", "automatic", "2026-12-01T00:00:00Z", "2027-02-01T00:00:00Z"),
                term(14, 4, "planned", "automatic", "2027-02-01T00:00:00Z", "2027-03-31T00:00:00Z")
        );
        when(repository.findAcademicYear(SCHOOL_ID, 100))
                .thenReturn(Optional.of(planned))
                .thenReturn(Optional.of(active));
        when(repository.listTermPeriods(100)).thenReturn(terms);
        when(repository.activateAcademicYearAutomatically(100)).thenReturn(1);
        when(repository.activateTermPeriodAutomatically(11, NOW)).thenReturn(1);

        service.reconcileAcademicYear(SCHOOL_ID, 100, NOW);

        verify(repository).activateAcademicYearAutomatically(100);
        verify(repository).activateTermPeriodAutomatically(11, NOW);
        verify(auditService).record(
                isNull(), eq("academic_year.auto_activate"), eq("academic_years"), eq("100"),
                eq("success"), any(), any(), eq(NOW)
        );
        verify(auditService).record(
                isNull(), eq("term_period.auto_activate"), eq("term_periods"), eq("11"),
                eq("success"), any(), any(), eq(NOW)
        );
    }

    @Test
    void automaticallyCompletesExpiredFinalTermAndAcademicYear() {
        AcademicYearRow active = year("active", LocalDate.of(2026, 8, 31));
        List<TermPeriodRow> before = List.of(
                completedTerm(11, 1),
                completedTerm(12, 2),
                completedTerm(13, 3),
                term(14, 4, "active", "automatic", "2026-07-01T00:00:00Z", "2026-09-01T00:00:00Z")
        );
        List<TermPeriodRow> after = List.of(
                completedTerm(11, 1), completedTerm(12, 2), completedTerm(13, 3), completedTerm(14, 4)
        );
        when(repository.findAcademicYear(SCHOOL_ID, 100)).thenReturn(Optional.of(active));
        when(repository.listTermPeriods(100))
                .thenReturn(before)
                .thenReturn(after);
        when(repository.completeTermPeriodAutomatically(14, NOW)).thenReturn(1);
        when(repository.completeAcademicYearAutomatically(100)).thenReturn(1);

        service.reconcileAcademicYear(SCHOOL_ID, 100, NOW);

        verify(repository).completeTermPeriodAutomatically(14, NOW);
        verify(repository).completeAcademicYearAutomatically(100);
    }

    @Test
    void manualTermIsNeverChangedByAutomation() {
        AcademicYearRow active = year("active", LocalDate.of(2027, 3, 31));
        List<TermPeriodRow> terms = List.of(
                term(11, 1, "active", "manual", "2026-06-01T00:00:00Z", "2026-08-01T00:00:00Z"),
                term(12, 2, "planned", "automatic", "2026-08-01T00:00:00Z", "2026-10-01T00:00:00Z"),
                term(13, 3, "planned", "automatic", "2026-10-01T00:00:00Z", "2027-01-01T00:00:00Z"),
                term(14, 4, "planned", "automatic", "2027-01-01T00:00:00Z", "2027-03-31T00:00:00Z")
        );
        when(repository.findAcademicYear(SCHOOL_ID, 100)).thenReturn(Optional.of(active));
        when(repository.listTermPeriods(100)).thenReturn(terms);

        service.reconcileAcademicYear(SCHOOL_ID, 100, NOW);

        verify(repository, never()).activateTermPeriodAutomatically(anyInt(), any(Instant.class));
        verify(repository, never()).completeTermPeriodAutomatically(anyInt(), any(Instant.class));
    }

    @Test
    void threeTermBoundaryCompletesFirstAndActivatesSecondAtManilaMidnight() {
        Instant boundary = Instant.parse("2026-09-15T16:00:00Z");
        when(repository.findAcademicYear(SCHOOL_ID, 100)).thenReturn(Optional.of(year("active", LocalDate.of(2027, 4, 8))));
        when(repository.listTermPeriods(100)).thenReturn(threeTerms("active", "planned", "planned"));
        when(repository.completeTermPeriodAutomatically(11, boundary)).thenReturn(1);
        when(repository.activateTermPeriodAutomatically(12, boundary)).thenReturn(1);

        service.reconcileAcademicYear(SCHOOL_ID, 100, boundary);

        verify(repository).completeTermPeriodAutomatically(11, boundary);
        verify(repository).activateTermPeriodAutomatically(12, boundary);
        verify(repository, never()).completeTermPeriodAutomatically(eq(12), any());
        verify(repository, never()).activateTermPeriodAutomatically(eq(13), any());
    }

    @Test
    void holidayGapCompletesSecondButDoesNotOpenThirdEarly() {
        Instant boundary = Instant.parse("2026-12-18T16:00:00Z");
        when(repository.findAcademicYear(SCHOOL_ID, 100)).thenReturn(Optional.of(year("active", LocalDate.of(2027, 4, 8))));
        when(repository.listTermPeriods(100)).thenReturn(threeTerms("completed", "active", "planned"));
        when(repository.completeTermPeriodAutomatically(12, boundary)).thenReturn(1);

        service.reconcileAcademicYear(SCHOOL_ID, 100, boundary);

        verify(repository).completeTermPeriodAutomatically(12, boundary);
        verify(repository, never()).activateTermPeriodAutomatically(eq(13), any());
    }

    @Test
    void finalThirdTermCompletesTheYearAfterItsSchoolLocalFinalDay() {
        Instant boundary = Instant.parse("2027-04-08T16:00:00Z");
        when(repository.findAcademicYear(SCHOOL_ID, 100)).thenReturn(Optional.of(year("active", LocalDate.of(2027, 4, 8))));
        when(repository.listTermPeriods(100)).thenReturn(threeTerms("completed", "completed", "active"))
                .thenReturn(threeTerms("completed", "completed", "completed"));
        when(repository.completeTermPeriodAutomatically(13, boundary)).thenReturn(1);
        when(repository.completeAcademicYearAutomatically(100)).thenReturn(1);

        service.reconcileAcademicYear(SCHOOL_ID, 100, boundary);

        verify(repository).completeTermPeriodAutomatically(13, boundary);
        verify(repository).completeAcademicYearAutomatically(100);
    }

    @Test
    void incompleteLegacyYearIsNotAutomaticallyActivatedOrProgressed() {
        when(repository.findAcademicYear(SCHOOL_ID, 100)).thenReturn(Optional.of(year("planned", LocalDate.of(2027, 3, 31))));
        when(repository.listTermPeriods(100)).thenReturn(List.of(completedTerm(11, 1), completedTerm(12, 2), completedTerm(13, 3)));
        service.reconcileAcademicYear(SCHOOL_ID, 100, NOW);
        when(repository.findAcademicYear(SCHOOL_ID, 100)).thenReturn(Optional.of(year("active", LocalDate.of(2027, 3, 31))));
        service.reconcileAcademicYear(SCHOOL_ID, 100, NOW);
        verify(repository, never()).activateAcademicYearAutomatically(anyInt());
        verify(repository, never()).activateTermPeriodAutomatically(anyInt(), any());
        verify(repository, never()).completeTermPeriodAutomatically(anyInt(), any());
        verify(repository, never()).completeAcademicYearAutomatically(anyInt());
    }

    @Test
    void automaticThreeTermCreationHonorsOtherActiveYear() {
        when(repository.findAcademicYear(SCHOOL_ID, 100)).thenReturn(Optional.of(year("planned", LocalDate.of(2027, 4, 8))));
        when(repository.listTermPeriods(100)).thenReturn(threeTerms("planned", "planned", "planned"));
        when(repository.anotherActiveAcademicYearExists(SCHOOL_ID, 100)).thenReturn(true);
        service.reconcileAcademicYear(SCHOOL_ID, 100, NOW);
        verify(repository, never()).activateAcademicYearAutomatically(anyInt());
        verify(repository, never()).activateTermPeriodAutomatically(anyInt(), any());
    }

    private List<TermPeriodRow> threeTerms(String first, String second, String third) {
        return List.of(
                namedTerm(11, 1, first, "2026-06-07T16:00:00Z", "2026-09-15T16:00:00Z"),
                namedTerm(12, 2, second, "2026-09-15T16:00:00Z", "2026-12-18T16:00:00Z"),
                namedTerm(13, 3, third, "2027-01-03T16:00:00Z", "2027-04-08T16:00:00Z")
        );
    }

    private TermPeriodRow namedTerm(int id, int order, String status, String start, String end) {
        return new TermPeriodRow(id, 100, "Term " + order, order, Instant.parse(start), Instant.parse(end),
                status, "automatic", null, null, null, null, null);
    }

    private AcademicYearRow year(String status, LocalDate endDate) {
        return new AcademicYearRow(
                100, SCHOOL_ID, 1, "K to 12", "2026-2027",
                LocalDate.of(2026, 6, 1), endDate, status, NOW, NOW
        );
    }

    private TermPeriodRow completedTerm(int id, int order) {
        return term(id, order, "completed", "automatic", "2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z");
    }

    private TermPeriodRow term(
            int id,
            int order,
            String status,
            String mode,
            String start,
            String end
    ) {
        return new TermPeriodRow(
                id, 100, "Quarter " + order, order, Instant.parse(start), Instant.parse(end),
                status, mode, null, null, null, null, null
        );
    }
}
