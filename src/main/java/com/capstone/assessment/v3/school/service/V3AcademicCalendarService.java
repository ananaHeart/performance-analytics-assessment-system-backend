package com.capstone.assessment.v3.school.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.school.dto.V3AcademicYearRequest;
import com.capstone.assessment.v3.school.dto.V3AcademicYearResponse;
import com.capstone.assessment.v3.school.dto.V3LifecycleReasonRequest;
import com.capstone.assessment.v3.school.dto.V3TermPeriodRequest;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.AcademicYearRow;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.TermPeriodRow;
import com.capstone.assessment.v3.school.repository.V3AcademicCalendarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Profile("v3")
@Service
public class V3AcademicCalendarService {

    private static final String PRINCIPAL_ROLE = "principal";
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Manila");
    private static final List<String> TERM_NAMES = List.of(
            "First Quarter", "Second Quarter", "Third Quarter", "Fourth Quarter"
    );
    private static final Set<String> ACTIVATION_MODES = Set.of("automatic", "manual");

    private final V3AcademicCalendarRepository repository;
    private final V3AuditService auditService;
    private final Clock clock;

    @Autowired
    public V3AcademicCalendarService(
            V3AcademicCalendarRepository repository,
            V3AuditService auditService
    ) {
        this(repository, auditService, Clock.systemUTC());
    }

    V3AcademicCalendarService(
            V3AcademicCalendarRepository repository,
            V3AuditService auditService,
            Clock clock
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<V3AcademicYearResponse> listAcademicYears(V3AuthenticatedUser principal) {
        requirePrincipal(principal);
        return repository.listAcademicYears(principal.schoolId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public V3AcademicYearResponse getAcademicYear(V3AuthenticatedUser principal, int academicYearId) {
        requirePrincipal(principal);
        return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
    }

    @Transactional
    public V3AcademicYearResponse createAcademicYear(
            V3AuthenticatedUser principal,
            V3AcademicYearRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        PreparedCalendar prepared = prepare(request);
        try {
            long academicYearId = repository.insertAcademicYear(
                    principal.schoolId(),
                    prepared.curriculumId(),
                    prepared.yearName(),
                    prepared.startDate(),
                    prepared.endDate()
            );
            for (PreparedTerm term : prepared.terms()) {
                repository.insertTermPeriod(
                        Math.toIntExact(academicYearId),
                        term.termName(),
                        term.termOrder(),
                        term.startAt(),
                        term.endAt(),
                        term.activationMode()
                );
            }
            auditService.record(
                    principal.userId(),
                    "academic_year.create",
                    "academic_years",
                    Long.toString(academicYearId),
                    "success",
                    metadata,
                    Map.of("yearName", prepared.yearName(), "termCount", 4),
                    clock.instant()
            );
            return toResponse(requireAcademicYear(principal.schoolId(), Math.toIntExact(academicYearId)));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ACADEMIC_YEAR_CONFLICT",
                    "The academic year conflicts with this school's existing calendar.");
        }
    }

    @Transactional
    public V3AcademicYearResponse updateAcademicYear(
            V3AuthenticatedUser principal,
            int academicYearId,
            V3AcademicYearRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        repository.lockAcademicYear(principal.schoolId(), academicYearId);
        AcademicYearRow existing = requireAcademicYear(principal.schoolId(), academicYearId);
        if (!"planned".equals(existing.status())) {
            throw conflict("ACADEMIC_YEAR_LOCKED", "Only a planned academic year may be edited.");
        }
        List<TermPeriodRow> existingTerms = repository.listTermPeriods(academicYearId);
        if (existingTerms.size() != 4 || existingTerms.stream().anyMatch(term -> !"planned".equals(term.status()))) {
            throw conflict("TERM_PERIODS_LOCKED",
                    "The four term periods must all be planned before the calendar can be edited.");
        }

        PreparedCalendar prepared = prepare(request);
        try {
            if (repository.updateAcademicYear(
                    academicYearId,
                    prepared.curriculumId(),
                    prepared.yearName(),
                    prepared.startDate(),
                    prepared.endDate()
            ) != 1) {
                throw conflict("ACADEMIC_YEAR_UPDATE_CONFLICT", "The academic year changed before it was saved.");
            }
            for (PreparedTerm term : prepared.terms()) {
                if (repository.updateTermPeriod(
                        academicYearId,
                        term.termOrder(),
                        term.termName(),
                        term.startAt(),
                        term.endAt(),
                        term.activationMode()
                ) != 1) {
                    throw conflict("TERM_PERIOD_UPDATE_CONFLICT",
                            "A term period changed before the calendar was saved.");
                }
            }
            auditService.record(
                    principal.userId(),
                    "academic_year.update",
                    "academic_years",
                    Integer.toString(academicYearId),
                    "success",
                    metadata,
                    Map.of("yearName", prepared.yearName(), "termCount", 4),
                    clock.instant()
            );
            return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ACADEMIC_YEAR_CONFLICT",
                    "The academic year conflicts with this school's existing calendar.");
        }
    }

    @Transactional
    public V3AcademicYearResponse activateAcademicYear(
            V3AuthenticatedUser principal,
            int academicYearId,
            V3LifecycleReasonRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requireReason(request);
        repository.lockAcademicYear(principal.schoolId(), academicYearId);
        AcademicYearRow year = requireAcademicYear(principal.schoolId(), academicYearId);
        if ("active".equals(year.status())) {
            return toResponse(year);
        }
        if (!"planned".equals(year.status())) {
            throw conflict("ACADEMIC_YEAR_NOT_ACTIVATABLE", "Only a planned academic year may be activated.");
        }
        if (repository.listTermPeriods(academicYearId).size() != 4) {
            throw conflict("TERM_PERIODS_INCOMPLETE", "Exactly four term periods are required before activation.");
        }
        if (repository.anotherActiveAcademicYearExists(principal.schoolId(), academicYearId)) {
            throw conflict("ACTIVE_ACADEMIC_YEAR_EXISTS",
                    "Complete the school's current active academic year before activating another one.");
        }
        try {
            if (repository.activateAcademicYear(academicYearId) != 1) {
                throw conflict("ACADEMIC_YEAR_ACTIVATION_CONFLICT",
                        "The academic year changed before activation.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ACTIVE_ACADEMIC_YEAR_EXISTS",
                    "Only one academic year may be active for a school.");
        }
        recordLifecycle(principal, academicYearId, "academic_year.activate", reason, metadata);
        return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
    }

    @Transactional
    public V3AcademicYearResponse completeAcademicYear(
            V3AuthenticatedUser principal,
            int academicYearId,
            V3LifecycleReasonRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requireReason(request);
        repository.lockAcademicYear(principal.schoolId(), academicYearId);
        AcademicYearRow year = requireAcademicYear(principal.schoolId(), academicYearId);
        if ("completed".equals(year.status())) {
            return toResponse(year);
        }
        if (!"active".equals(year.status())) {
            throw conflict("ACADEMIC_YEAR_NOT_COMPLETABLE", "Only an active academic year may be completed.");
        }
        if (repository.listTermPeriods(academicYearId).stream()
                .anyMatch(term -> !"completed".equals(term.status()))) {
            throw conflict("TERM_PERIODS_NOT_COMPLETED",
                    "Complete all four term periods before completing the academic year.");
        }
        if (repository.completeAcademicYear(academicYearId) != 1) {
            throw conflict("ACADEMIC_YEAR_COMPLETION_CONFLICT",
                    "The academic year changed before completion.");
        }
        recordLifecycle(principal, academicYearId, "academic_year.complete", reason, metadata);
        return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
    }

    @Transactional
    public V3AcademicYearResponse activateTermPeriod(
            V3AuthenticatedUser principal,
            int academicYearId,
            int termPeriodId,
            V3LifecycleReasonRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requireReason(request);
        repository.lockAcademicYear(principal.schoolId(), academicYearId);
        AcademicYearRow year = requireAcademicYear(principal.schoolId(), academicYearId);
        if (!"active".equals(year.status())) {
            throw conflict("ACADEMIC_YEAR_NOT_ACTIVE", "Activate the academic year before activating a term.");
        }
        TermPeriodRow term = requireTerm(academicYearId, termPeriodId);
        if ("active".equals(term.status())) {
            return toResponse(year);
        }
        if (!"planned".equals(term.status())) {
            throw conflict("TERM_PERIOD_NOT_ACTIVATABLE", "Only a planned term period may be activated.");
        }
        if (repository.anotherActiveTermExists(academicYearId, termPeriodId)) {
            throw conflict("ACTIVE_TERM_PERIOD_EXISTS", "Complete the active term before activating another one.");
        }
        if (repository.priorIncompleteTermExists(academicYearId, term.termOrder())) {
            throw conflict("PRIOR_TERM_INCOMPLETE", "Earlier term periods must be completed in order.");
        }
        try {
            if (repository.activateTermPeriod(termPeriodId, principal.userId(), reason, clock.instant()) != 1) {
                throw conflict("TERM_PERIOD_ACTIVATION_CONFLICT", "The term changed before activation.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ACTIVE_TERM_PERIOD_EXISTS", "Only one term may be active in an academic year.");
        }
        recordTermLifecycle(principal, termPeriodId, "term_period.activate", reason, metadata);
        return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
    }

    @Transactional
    public V3AcademicYearResponse completeTermPeriod(
            V3AuthenticatedUser principal,
            int academicYearId,
            int termPeriodId,
            V3LifecycleReasonRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requireReason(request);
        repository.lockAcademicYear(principal.schoolId(), academicYearId);
        requireAcademicYear(principal.schoolId(), academicYearId);
        TermPeriodRow term = requireTerm(academicYearId, termPeriodId);
        if ("completed".equals(term.status())) {
            return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
        }
        if (!"active".equals(term.status())) {
            throw conflict("TERM_PERIOD_NOT_COMPLETABLE", "Only an active term period may be completed.");
        }
        if (repository.completeTermPeriod(termPeriodId, principal.userId(), reason, clock.instant()) != 1) {
            throw conflict("TERM_PERIOD_COMPLETION_CONFLICT", "The term changed before completion.");
        }
        recordTermLifecycle(principal, termPeriodId, "term_period.complete", reason, metadata);
        return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
    }

    private PreparedCalendar prepare(V3AcademicYearRequest request) {
        if (request == null) {
            throw invalid("request", "Academic-year request is required.");
        }
        if (!repository.curriculumExists(request.curriculumId())) {
            throw invalid("curriculumId", "The selected curriculum does not exist.");
        }
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw invalid("endDate", "endDate must be later than startDate.");
        }
        if (endDate.getYear() != startDate.getYear() + 1) {
            throw invalid("endDate", "An academic year must end in the calendar year after it starts.");
        }
        String expectedName = startDate.getYear() + "-" + endDate.getYear();
        String yearName = request.yearName() == null ? "" : request.yearName().trim();
        if (!expectedName.equals(yearName)) {
            throw invalid("yearName", "yearName must match the start and end years: " + expectedName + ".");
        }

        List<V3TermPeriodRequest> requestedTerms = request.termPeriods() == null
                ? List.of()
                : request.termPeriods();
        if (requestedTerms.size() != 4) {
            throw invalid("termPeriods", "Exactly four term periods are required.");
        }
        List<PreparedTerm> terms = new ArrayList<>();
        Set<Integer> orders = new HashSet<>();
        Instant calendarStart = startDate.atStartOfDay(SCHOOL_ZONE).toInstant();
        Instant calendarEndExclusive = endDate.plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant();
        for (V3TermPeriodRequest term : requestedTerms) {
            if (term == null) {
                throw invalid("termPeriods", "Term periods cannot contain null entries.");
            }
            if (term.termOrder() < 1 || term.termOrder() > 4) {
                throw invalid("termPeriods.termOrder", "Term orders must be values from 1 through 4.");
            }
            if (!orders.add(term.termOrder())) {
                throw invalid("termPeriods.termOrder", "Term orders must be unique values from 1 through 4.");
            }
            String canonicalName = TERM_NAMES.get(term.termOrder() - 1);
            if (!canonicalName.equals(term.termName() == null ? null : term.termName().trim())) {
                throw invalid("termPeriods.termName",
                        "Term " + term.termOrder() + " must be named " + canonicalName + ".");
            }
            if (term.startAt() == null || term.endAt() == null || !term.endAt().isAfter(term.startAt())) {
                throw invalid("termPeriods.endAt", "Each term endAt must be later than startAt.");
            }
            if (term.startAt().isBefore(calendarStart) || term.endAt().isAfter(calendarEndExclusive)) {
                throw invalid("termPeriods", "Every term must stay inside the academic-year dates.");
            }
            String mode = normalizeMode(term.activationMode());
            terms.add(new PreparedTerm(
                    canonicalName, term.termOrder(), term.startAt(), term.endAt(), mode
            ));
        }
        if (orders.size() != 4 || !orders.containsAll(Set.of(1, 2, 3, 4))) {
            throw invalid("termPeriods.termOrder", "Term orders must contain 1, 2, 3, and 4.");
        }
        terms.sort(Comparator.comparingInt(PreparedTerm::termOrder));
        Instant previousEnd = null;
        for (PreparedTerm term : terms) {
            if (previousEnd != null && term.startAt().isBefore(previousEnd)) {
                throw invalid("termPeriods", "Term periods cannot overlap and must follow chronological order.");
            }
            previousEnd = term.endAt();
        }
        return new PreparedCalendar(
                request.curriculumId(), yearName, startDate, endDate, List.copyOf(terms)
        );
    }

    private V3AcademicYearResponse toResponse(AcademicYearRow year) {
        List<V3AcademicYearResponse.TermPeriod> terms = repository.listTermPeriods(year.academicYearId())
                .stream()
                .map(term -> new V3AcademicYearResponse.TermPeriod(
                        term.termPeriodId(),
                        term.termName(),
                        term.termOrder(),
                        term.startAt(),
                        term.endAt(),
                        term.status(),
                        term.activationMode(),
                        term.activatedAt(),
                        term.completedAt(),
                        term.overriddenByUserId(),
                        term.overrideReason(),
                        term.overriddenAt()
                ))
                .toList();
        return new V3AcademicYearResponse(
                year.academicYearId(),
                year.curriculumId(),
                year.curriculumName(),
                year.yearName(),
                year.startDate(),
                year.endDate(),
                year.status(),
                terms,
                year.createdAt(),
                year.updatedAt()
        );
    }

    private AcademicYearRow requireAcademicYear(String schoolId, int academicYearId) {
        return repository.findAcademicYear(schoolId, academicYearId)
                .orElseThrow(() -> new V3AuthException(
                        "ACADEMIC_YEAR_NOT_FOUND",
                        "The academic year was not found in the authenticated principal's school.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private TermPeriodRow requireTerm(int academicYearId, int termPeriodId) {
        return repository.findTermPeriod(academicYearId, termPeriodId)
                .orElseThrow(() -> new V3AuthException(
                        "TERM_PERIOD_NOT_FOUND",
                        "The term period was not found in the selected academic year.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private void requirePrincipal(V3AuthenticatedUser user) {
        if (user == null) {
            throw new V3AuthException("AUTHENTICATION_REQUIRED", "Authentication is required.",
                    HttpStatus.UNAUTHORIZED);
        }
        if (!PRINCIPAL_ROLE.equalsIgnoreCase(user.role())) {
            throw new V3AuthException("PRINCIPAL_ROLE_REQUIRED",
                    "Only an authenticated principal may manage the academic calendar.",
                    HttpStatus.FORBIDDEN);
        }
        if (!"active".equalsIgnoreCase(user.status())
                || user.schoolId() == null || user.schoolId().isBlank()) {
            throw new V3AuthException("ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active principal account associated with a school is required.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private String requireReason(V3LifecycleReasonRequest request) {
        String reason = request == null ? null : request.reason();
        if (reason == null || reason.trim().length() < 5) {
            throw invalid("reason", "A reason with at least 5 characters is required.");
        }
        return reason.trim();
    }

    private String normalizeMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!ACTIVATION_MODES.contains(normalized)) {
            throw invalid("termPeriods.activationMode", "activationMode must be automatic or manual.");
        }
        return normalized;
    }

    private void recordLifecycle(
            V3AuthenticatedUser principal,
            int academicYearId,
            String action,
            String reason,
            V3RequestMetadata metadata
    ) {
        auditService.record(
                principal.userId(), action, "academic_years", Integer.toString(academicYearId),
                "success", metadata, Map.of("reason", reason), clock.instant()
        );
    }

    private void recordTermLifecycle(
            V3AuthenticatedUser principal,
            int termPeriodId,
            String action,
            String reason,
            V3RequestMetadata metadata
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        auditService.record(
                principal.userId(), action, "term_periods", Integer.toString(termPeriodId),
                "success", metadata, details, clock.instant()
        );
    }

    private V3FieldValidationException invalid(String field, String message) {
        return new V3FieldValidationException(
                "Academic calendar validation failed.",
                HttpStatus.BAD_REQUEST,
                Map.of("code", "VALIDATION_FAILED", field, message)
        );
    }

    private V3AuthException conflict(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.CONFLICT);
    }

    private record PreparedCalendar(
            int curriculumId,
            String yearName,
            LocalDate startDate,
            LocalDate endDate,
            List<PreparedTerm> terms
    ) {
    }

    private record PreparedTerm(
            String termName,
            int termOrder,
            Instant startAt,
            Instant endAt,
            String activationMode
    ) {
    }
}
