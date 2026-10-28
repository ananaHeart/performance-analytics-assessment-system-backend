package com.capstone.assessment.v3.school.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.school.dto.V3AcademicYearRequest;
import com.capstone.assessment.v3.school.dto.V3AcademicYearResponse;
import com.capstone.assessment.v3.school.dto.V3LifecycleReasonRequest;
import com.capstone.assessment.v3.school.dto.V3AcademicYearArchiveEligibilityResponse;
import com.capstone.assessment.v3.school.dto.V3TermDatesRequest;
import com.capstone.assessment.v3.school.dto.V3TermPeriodRequest;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarLayout;
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
    private static final Set<String> ACTIVATION_MODES = Set.of("automatic", "manual");
    /** SET-4 (D8): the panel's limit of changes per term. */
    static final int MAX_TERM_DATE_EDITS = 3;
    /** SET-5: late uploads, verification and corrections stay possible this long after the year ends. */
    static final int ARCHIVE_GRACE_MONTHS = 1;

    private final V3AcademicCalendarRepository repository;
    private final V3AuditService auditService;
    private final V3NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public V3AcademicCalendarService(
            V3AcademicCalendarRepository repository,
            V3AuditService auditService,
            V3NotificationService notificationService
    ) {
        this(repository, auditService, notificationService, Clock.systemUTC());
    }

    V3AcademicCalendarService(
            V3AcademicCalendarRepository repository,
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
    public List<V3AcademicYearResponse> listAcademicYears(V3AuthenticatedUser principal) {
        return listAcademicYears(principal, null);
    }

    /** SET-5: {@code view} is "current" (default) or "archived". */
    @Transactional(readOnly = true)
    public List<V3AcademicYearResponse> listAcademicYears(V3AuthenticatedUser principal, String view) {
        requirePrincipal(principal);
        String normalized = view == null || view.isBlank() ? "current" : view.trim().toLowerCase(Locale.ROOT);
        if (!"current".equals(normalized) && !"archived".equals(normalized)) {
            throw invalid("view", "Use current or archived.");
        }
        return repository.listAcademicYears(principal.schoolId(), "archived".equals(normalized)).stream()
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
        PreparedCalendar prepared = prepare(request, V3AcademicCalendarLayout.THREE_TERMS);
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
                    Map.of("yearName", prepared.yearName(), "termCount", prepared.terms().size()),
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
        V3AcademicCalendarLayout existingLayout = requireSupportedLayout(existingTerms);
        if (existingTerms.stream().anyMatch(term -> !"planned".equals(term.status()))) {
            throw conflict("TERM_PERIODS_LOCKED",
                    "All term periods must be planned before the calendar can be edited.");
        }

        PreparedCalendar prepared = prepare(request, existingLayout);
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
                        existingTerms.stream().filter(existingTerm -> existingTerm.termOrder() == term.termOrder())
                                .findFirst().orElseThrow().termName(),
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
                    Map.of("yearName", prepared.yearName(), "termCount", prepared.terms().size()),
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
        requireSupportedLayout(repository.listTermPeriods(academicYearId));
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
        List<TermPeriodRow> terms = repository.listTermPeriods(academicYearId);
        requireSupportedLayout(terms);
        if (terms.stream()
                .anyMatch(term -> !"completed".equals(term.status()))) {
            throw conflict("TERM_PERIODS_NOT_COMPLETED",
                    "Complete all term periods before completing the academic year.");
        }
        // SET-5: a year is completed only after its end date; archiving is a separate, later step.
        if (LocalDate.ofInstant(clock.instant(), SCHOOL_ZONE).isBefore(year.endDate())) {
            throw new V3AuthException("ACADEMIC_YEAR_NOT_ENDED",
                    "The school year ends on " + year.endDate() + ". It can be completed from that day.",
                    HttpStatus.CONFLICT, Map.of("endDate", year.endDate().toString()));
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
        requireSupportedLayout(repository.listTermPeriods(academicYearId));
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
        requireSupportedLayout(repository.listTermPeriods(academicYearId));
        if (repository.completeTermPeriod(termPeriodId, principal.userId(), reason, clock.instant()) != 1) {
            throw conflict("TERM_PERIOD_COMPLETION_CONFLICT", "The term changed before completion.");
        }
        recordTermLifecycle(principal, termPeriodId, "term_period.complete", reason, metadata);
        return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
    }

    private PreparedCalendar prepare(V3AcademicYearRequest request, V3AcademicCalendarLayout expectedLayout) {
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
        V3AcademicCalendarLayout requestedLayout = V3AcademicCalendarLayout.detect(
                requestedTerms, V3TermPeriodRequest::termName, V3TermPeriodRequest::termOrder
        ).orElseThrow(() -> invalid("termPeriods",
                "Provide a complete calendar with unique ordered Term 1–3 or legacy Quarter 1–4 names."));
        if (requestedLayout != expectedLayout) {
            throw invalid("termPeriods", expectedLayout == V3AcademicCalendarLayout.THREE_TERMS
                    ? "This calendar requires exactly Term 1, Term 2, and Term 3 in orders 1 through 3."
                    : "An existing four-quarter calendar must retain all four quarters and their orders.");
        }
        List<PreparedTerm> terms = new ArrayList<>();
        Instant calendarStart = startDate.atStartOfDay(SCHOOL_ZONE).toInstant();
        Instant calendarEndExclusive = endDate.plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant();
        for (V3TermPeriodRequest term : requestedTerms) {
            if (term.startAt() == null || term.endAt() == null || !term.endAt().isAfter(term.startAt())) {
                throw invalid("termPeriods.endAt", "Each term endAt must be later than startAt.");
            }
            if (term.startAt().isBefore(calendarStart) || term.endAt().isAfter(calendarEndExclusive)) {
                throw invalid("termPeriods", "Every term must stay inside the academic-year dates.");
            }
            String mode = normalizeMode(term.activationMode());
            terms.add(new PreparedTerm(
                    term.termName().trim(), term.termOrder(), term.startAt(), term.endAt(), mode
            ));
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

    private V3AcademicCalendarLayout requireSupportedLayout(List<TermPeriodRow> terms) {
        return V3AcademicCalendarLayout.detect(terms, TermPeriodRow::termName, TermPeriodRow::termOrder)
                .orElseThrow(() -> conflict("TERM_PERIODS_INCOMPLETE",
                        "A complete three-term or legacy four-quarter calendar is required."));
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

    /** SET-5: whether the school year can be archived now, with every reason it cannot. */
    @Transactional(readOnly = true)
    public V3AcademicYearArchiveEligibilityResponse getArchiveEligibility(
            V3AuthenticatedUser principal,
            int academicYearId
    ) {
        requirePrincipal(principal);
        return evaluateArchive(requireAcademicYear(principal.schoolId(), academicYearId));
    }

    /**
     * SET-5: the principal confirms the archive after the one-month grace period. Archived years
     * are hidden from normal lists, dropdowns and the phone download; nothing is deleted.
     */
    @Transactional
    public V3AcademicYearResponse archiveAcademicYear(
            V3AuthenticatedUser principal,
            int academicYearId,
            V3LifecycleReasonRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requireReason(request);
        repository.lockAcademicYear(principal.schoolId(), academicYearId);
        AcademicYearRow year = requireAcademicYear(principal.schoolId(), academicYearId);
        if ("archived".equals(year.status())) {
            return toResponse(year);
        }
        V3AcademicYearArchiveEligibilityResponse check = evaluateArchive(year);
        if (!"completed".equals(year.status())) {
            throw conflict("ACADEMIC_YEAR_NOT_COMPLETED",
                    "Only a completed school year can be archived. It is completed after its end date.");
        }
        if (!check.readyToArchive()) {
            throw new V3AuthException("ARCHIVE_GRACE_PERIOD_ACTIVE",
                    "Late uploads and corrections are still allowed until " + check.readyToArchiveOn()
                            + ". The school year can be archived from that day.",
                    HttpStatus.CONFLICT, Map.of("readyToArchiveOn", check.readyToArchiveOn().toString()));
        }
        if (check.openAssessmentCount() > 0) {
            throw new V3AuthException("ACADEMIC_YEAR_HAS_OPEN_ASSESSMENTS",
                    check.openAssessmentCount() + " assessment(s) are still open. Close or archive them first.",
                    HttpStatus.CONFLICT, Map.of("openAssessmentCount", check.openAssessmentCount()));
        }
        if (check.pendingSyncCount() > 0) {
            throw new V3AuthException("ACADEMIC_YEAR_HAS_PENDING_SYNCS",
                    check.pendingSyncCount() + " phone upload(s) have not finished syncing.",
                    HttpStatus.CONFLICT, Map.of("pendingSyncCount", check.pendingSyncCount()));
        }
        if (check.unfinalizedResultCount() > 0) {
            throw new V3AuthException("ACADEMIC_YEAR_HAS_UNFINALIZED_RESULTS",
                    check.unfinalizedResultCount() + " result(s) or scan(s) still need verification or finalizing.",
                    HttpStatus.CONFLICT, Map.of("unfinalizedResultCount", check.unfinalizedResultCount()));
        }
        if (repository.archiveAcademicYear(academicYearId) != 1) {
            throw conflict("ACADEMIC_YEAR_ARCHIVE_CONFLICT", "The school year changed before it could be archived.");
        }
        recordLifecycle(principal, academicYearId, "academic_year.archive", reason, metadata);
        return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
    }

    /**
     * SET-5: runs nightly. A year past its latest end becomes Completed (with its remaining
     * terms); when the grace month is over, the principals get one "ready to archive" notice.
     */
    @Transactional
    public int runYearLifecycle() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, SCHOOL_ZONE);
        int changed = 0;
        for (AcademicYearRow year : repository.listAcademicYearsInStatus("active")) {
            if (latestEndDate(year).isBefore(today)) {
                repository.completeRemainingTerms(year.academicYearId(), now);
                if (repository.completeAcademicYearAutomatically(year.academicYearId()) == 1) {
                    auditService.record(null, "academic_year.complete_automatic", "academic_years",
                            Integer.toString(year.academicYearId()), "success", null,
                            Map.of("latestEndDate", latestEndDate(year).toString()), now);
                    changed++;
                }
            }
        }
        for (AcademicYearRow year : repository.listAcademicYearsInStatus("completed")) {
            LocalDate readyOn = latestEndDate(year).plusMonths(ARCHIVE_GRACE_MONTHS);
            if (!today.isBefore(readyOn)) {
                notificationService.notifyPrincipalsYearReadyToArchive(year.schoolId(), year.academicYearId(),
                        year.yearName(), now);
            }
        }
        return changed;
    }

    private V3AcademicYearArchiveEligibilityResponse evaluateArchive(AcademicYearRow year) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SCHOOL_ZONE);
        LocalDate latestEnd = latestEndDate(year);
        LocalDate readyOn = latestEnd.plusMonths(ARCHIVE_GRACE_MONTHS);
        boolean endDateReached = !today.isBefore(year.endDate());
        boolean ready = !today.isBefore(readyOn);
        int incompleteTerms = (int) repository.listTermPeriods(year.academicYearId()).stream()
                .filter(term -> !"completed".equals(term.status()))
                .count();
        int openAssessments = repository.countOpenAssessments(year.academicYearId(), clock.instant());
        int pendingSyncs = repository.countPendingSyncs(year.academicYearId());
        int unfinalized = repository.countUnfinalizedResults(year.academicYearId());
        List<String> reasons = new java.util.ArrayList<>();
        if ("archived".equals(year.status())) {
            reasons.add("The school year is already archived.");
        } else if (!"completed".equals(year.status())) {
            reasons.add("The school year is not completed yet; it is completed after " + latestEnd + ".");
        }
        if (!"archived".equals(year.status()) && !ready) {
            reasons.add("Late uploads and corrections are allowed until " + readyOn + ".");
        }
        if (openAssessments > 0) {
            reasons.add(openAssessments + " assessment(s) are still open.");
        }
        if (pendingSyncs > 0) {
            reasons.add(pendingSyncs + " phone upload(s) have not finished syncing.");
        }
        if (unfinalized > 0) {
            reasons.add(unfinalized + " result(s) or scan(s) still need verification or finalizing.");
        }
        return new V3AcademicYearArchiveEligibilityResponse(year.academicYearId(), year.status(), reasons.isEmpty(),
                year.endDate(), endDateReached, incompleteTerms, openAssessments, reasons, latestEnd, readyOn, ready,
                pendingSyncs, unfinalized);
    }

    /** The later of the year's end date and its latest term end (an extension moves it). */
    private LocalDate latestEndDate(AcademicYearRow year) {
        LocalDate latest = year.endDate();
        java.util.Optional<Instant> termEnd = repository.findLatestTermEnd(year.academicYearId());
        if (termEnd.isPresent()) {
            // A term ending exactly at midnight belongs to the previous day.
            LocalDate termDay = termEnd.get().minusSeconds(1).atZone(SCHOOL_ZONE).toLocalDate();
            if (termDay.isAfter(latest)) {
                latest = termDay;
            }
        }
        return latest;
    }

    /**
     * SET-4 (D8): extend or correct a planned or active term in an active (or planned) school year.
     * The dates stay inside the year and do not overlap other terms; at most 3 changes per term;
     * no assessment may end up outside the term; every change is audited with the old and new dates.
     */
    @Transactional
    public V3AcademicYearResponse updateTermDates(
            V3AuthenticatedUser principal,
            int academicYearId,
            int termPeriodId,
            V3TermDatesRequest request,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String reason = requireReason(new V3LifecycleReasonRequest(request.reason()));
        repository.lockAcademicYear(principal.schoolId(), academicYearId);
        AcademicYearRow year = requireAcademicYear(principal.schoolId(), academicYearId);
        if ("completed".equals(year.status())) {
            throw conflict("ACADEMIC_YEAR_LOCKED", "An archived school year cannot change.");
        }
        TermPeriodRow term = requireTerm(academicYearId, termPeriodId);
        if ("completed".equals(term.status())) {
            throw conflict("TERM_PERIOD_LOCKED", "A completed term cannot change.");
        }
        Instant startAt = request.startAt();
        Instant endAt = request.endAt();
        Instant now = clock.instant();
        if (!endAt.isAfter(startAt)) {
            throw invalid("endAt", "The end must be later than the start.");
        }
        Instant yearStart = year.startDate().atStartOfDay(SCHOOL_ZONE).toInstant();
        Instant yearEnd = year.endDate().plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant();
        if (startAt.isBefore(yearStart) || endAt.isAfter(yearEnd)) {
            throw invalid("endAt", "The term must stay inside the school year (" + year.startDate()
                    + " to " + year.endDate() + ").");
        }
        if ("active".equals(term.status()) && !endAt.isAfter(now)) {
            throw invalid("endAt", "The current term must end in the future.");
        }
        for (TermPeriodRow other : repository.listTermPeriods(academicYearId)) {
            if (other.termPeriodId() != termPeriodId
                    && other.startAt().isBefore(endAt) && startAt.isBefore(other.endAt())) {
                throw invalid("endAt", "These dates overlap " + other.termName() + " ("
                        + other.startAt().atZone(SCHOOL_ZONE).toLocalDate() + " to "
                        + other.endAt().atZone(SCHOOL_ZONE).toLocalDate() + ").");
            }
        }
        int edits = repository.countTermDateEdits(termPeriodId);
        if (edits >= MAX_TERM_DATE_EDITS) {
            throw new V3AuthException("TERM_EDIT_LIMIT_REACHED",
                    "This term's dates were already changed " + edits + " times, the most allowed.",
                    HttpStatus.CONFLICT, Map.of("editCount", edits, "maxEdits", MAX_TERM_DATE_EDITS));
        }
        int outside = repository.countAssessmentsOutside(termPeriodId, startAt, endAt);
        if (outside > 0) {
            throw new V3AuthException("TERM_HAS_ASSESSMENTS_OUTSIDE",
                    outside + " assessment(s) of this term would fall outside the new dates.",
                    HttpStatus.CONFLICT, Map.of("assessmentCount", outside));
        }
        if (repository.updateTermDates(termPeriodId, startAt, endAt, principal.userId(), reason, now) != 1) {
            throw conflict("TERM_PERIOD_UPDATE_CONFLICT", "The term changed before it was saved.");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("academicYearId", academicYearId);
        details.put("previousStartAt", term.startAt().toString());
        details.put("previousEndAt", term.endAt().toString());
        details.put("startAt", startAt.toString());
        details.put("endAt", endAt.toString());
        details.put("reason", reason);
        details.put("editNumber", edits + 1);
        auditService.record(principal.userId(), "term_period.dates_update", "term_periods",
                Integer.toString(termPeriodId), "success", metadata, details, now);
        return toResponse(requireAcademicYear(principal.schoolId(), academicYearId));
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
