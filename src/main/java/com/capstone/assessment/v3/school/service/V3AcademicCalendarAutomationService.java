package com.capstone.assessment.v3.school.service;

import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.AcademicYearRow;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.TermPeriodRow;
import com.capstone.assessment.v3.school.repository.V3AcademicCalendarRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Profile("v3")
@Service
public class V3AcademicCalendarAutomationService {

    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Manila");
    private static final V3RequestMetadata SYSTEM_METADATA =
            new V3RequestMetadata(null, null, "v3-calendar-automation");

    private final V3AcademicCalendarRepository repository;
    private final V3AuditService auditService;

    public V3AcademicCalendarAutomationService(
            V3AcademicCalendarRepository repository,
            V3AuditService auditService
    ) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public void reconcileAcademicYear(String schoolId, int academicYearId, Instant now) {
        LocalDate schoolDate = now.atZone(SCHOOL_ZONE).toLocalDate();
        repository.lockAcademicYear(schoolId, academicYearId);
        AcademicYearRow year = repository.findAcademicYear(schoolId, academicYearId).orElse(null);
        if (year == null || "completed".equals(year.status())) {
            return;
        }

        if ("planned".equals(year.status()) && !year.startDate().isAfter(schoolDate)) {
            List<TermPeriodRow> terms = repository.listTermPeriods(academicYearId);
            if (terms.size() != 4
                    || repository.anotherActiveAcademicYearExists(schoolId, academicYearId)) {
                return;
            }
            if (repository.activateAcademicYearAutomatically(academicYearId) == 1) {
                record("academic_year.auto_activate", "academic_years", academicYearId, now);
            }
            year = repository.findAcademicYear(schoolId, academicYearId).orElse(year);
        }

        if (!"active".equals(year.status())) {
            return;
        }

        reconcileTerms(academicYearId, now);
        List<TermPeriodRow> refreshedTerms = repository.listTermPeriods(academicYearId);
        if (schoolDate.isAfter(year.endDate())
                && refreshedTerms.size() == 4
                && refreshedTerms.stream().allMatch(term -> "completed".equals(term.status()))
                && repository.completeAcademicYearAutomatically(academicYearId) == 1) {
            record("academic_year.auto_complete", "academic_years", academicYearId, now);
        }
    }

    private void reconcileTerms(int academicYearId, Instant now) {
        for (TermPeriodRow term : repository.listTermPeriods(academicYearId)) {
            if ("completed".equals(term.status())) {
                continue;
            }
            if (!"automatic".equals(term.activationMode())) {
                return;
            }
            if ("active".equals(term.status())) {
                if (now.isBefore(term.endAt())) {
                    return;
                }
                if (repository.completeTermPeriodAutomatically(term.termPeriodId(), now) == 1) {
                    record("term_period.auto_complete", "term_periods", term.termPeriodId(), now);
                }
                continue;
            }
            if (!"planned".equals(term.status()) || now.isBefore(term.startAt())) {
                return;
            }
            if (repository.anotherActiveTermExists(academicYearId, term.termPeriodId())
                    || repository.priorIncompleteTermExists(academicYearId, term.termOrder())) {
                return;
            }
            if (repository.activateTermPeriodAutomatically(term.termPeriodId(), now) == 1) {
                record("term_period.auto_activate", "term_periods", term.termPeriodId(), now);
            }
            if (now.isBefore(term.endAt())) {
                return;
            }
            if (repository.completeTermPeriodAutomatically(term.termPeriodId(), now) == 1) {
                record("term_period.auto_complete", "term_periods", term.termPeriodId(), now);
            }
        }
    }

    private void record(String action, String entityType, long entityId, Instant now) {
        auditService.record(
                null,
                action,
                entityType,
                Long.toString(entityId),
                "success",
                SYSTEM_METADATA,
                Map.of("trigger", "scheduled_calendar", "effectiveAt", now.toString()),
                now
        );
    }
}
