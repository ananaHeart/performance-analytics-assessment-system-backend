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
                term(11, 1, "active", "manual", "2026-06-01T00:00:00Z", "2026-08-01T00:00:00Z")
        );
        when(repository.findAcademicYear(SCHOOL_ID, 100)).thenReturn(Optional.of(active));
        when(repository.listTermPeriods(100)).thenReturn(terms);

        service.reconcileAcademicYear(SCHOOL_ID, 100, NOW);

        verify(repository, never()).activateTermPeriodAutomatically(anyInt(), any(Instant.class));
        verify(repository, never()).completeTermPeriodAutomatically(anyInt(), any(Instant.class));
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
