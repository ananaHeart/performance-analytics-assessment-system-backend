package com.capstone.assessment.v3.report.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse.CalculationPolicy;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse.PerformanceRuleSetReference;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse.ReportWarning;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse.Scope;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse.StudentResultRow;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse.Summary;
import com.capstone.assessment.v3.report.dto.V3ReportReferenceDataResponse;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentResultRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentScopeRow;
import com.capstone.assessment.v3.report.repository.V3ReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Profile("v3")
@Service
public class V3ReportService {

    private static final Set<String> ALLOWED_ROLES = Set.of("principal", "teacher");
    private static final String FINALIZED = "finalized";
    private static final int METRIC_SCALE = 2;

    private final V3ReportRepository reportRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public V3ReportService(V3ReportRepository reportRepository, ObjectMapper objectMapper) {
        this(reportRepository, objectMapper, Clock.systemUTC());
    }

    V3ReportService(V3ReportRepository reportRepository, ObjectMapper objectMapper, Clock clock) {
        this.reportRepository = reportRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public V3ReportReferenceDataResponse getReferenceData(V3AuthenticatedUser user) {
        AccessContext access = requireActiveUser(user);
        Long teacherUserId = access.isTeacher() ? access.userId() : null;
        V3ReportReferenceDataResponse.SchoolOption school = reportRepository.findSchool(access.schoolId())
                .orElseThrow(() -> new V3AuthException(
                        "REPORT_SCHOOL_NOT_FOUND",
                        "The authenticated account's school was not found.",
                        HttpStatus.NOT_FOUND
                ));

        return new V3ReportReferenceDataResponse(
                access.role(),
                school,
                reportRepository.listAcademicYears(access.schoolId(), teacherUserId),
                reportRepository.listTermPeriods(access.schoolId(), teacherUserId),
                reportRepository.listGradeLevels(access.schoolId(), teacherUserId),
                reportRepository.listClasses(access.schoolId(), teacherUserId),
                access.isTeacher()
                        ? List.of()
                        : reportRepository.listTeachers(access.schoolId(), null),
                reportRepository.listSubjects(access.schoolId(), teacherUserId),
                reportRepository.listClassAssignments(access.schoolId(), teacherUserId),
                reportRepository.listAssessments(access.schoolId(), teacherUserId),
                reportRepository.listStudents(access.schoolId(), teacherUserId)
        );
    }

    @Transactional(readOnly = true)
    public V3AssessmentResultsReportResponse getAssessmentResults(
            V3AuthenticatedUser user,
            long testId,
            long classAssignmentId
    ) {
        AccessContext access = requireActiveUser(user);
        validateIdentifiers(testId, classAssignmentId);
        requireVisibleTest(access, testId);

        AssessmentScopeRow scope = reportRepository.findAssessmentScope(testId, classAssignmentId)
                .orElseThrow(() -> new V3FieldValidationException(
                        "The selected assessment and class assignment do not belong together.",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        Map.of(
                                "code", "REPORT_FILTER_MISMATCH",
                                "testId", "The assessment is not assigned through this classAssignmentId.",
                                "classAssignmentId", "The class assignment does not own this assessment assignment."
                        )
                ));
        requireScopeAccess(access, scope);

        List<AssessmentResultRow> resultRows = reportRepository.listLatestAssessmentResults(scope);
        List<ReportWarning> warnings = new ArrayList<>();
        Set<String> warningCodes = new LinkedHashSet<>();
        List<StudentResultRow> rows = mapRows(resultRows, warnings, warningCodes);
        Summary summary = summarize(scope, resultRows);
        addCompletenessWarnings(summary, warnings, warningCodes);
        addMaximumSnapshotWarning(scope, resultRows, warnings, warningCodes);

        return new V3AssessmentResultsReportResponse(
                "assessment_results",
                clock.instant(),
                mapScope(scope),
                calculationPolicy(resultRows),
                dataStatus(summary),
                List.copyOf(warnings),
                summary,
                rows
        );
    }

    private List<StudentResultRow> mapRows(
            List<AssessmentResultRow> sourceRows,
            List<ReportWarning> warnings,
            Set<String> warningCodes
    ) {
        List<StudentResultRow> rows = new ArrayList<>(sourceRows.size());
        for (AssessmentResultRow source : sourceRows) {
            rows.add(new StudentResultRow(
                    source.studentId(),
                    source.classListId(),
                    source.studentLrn(),
                    source.fullName(),
                    source.enrollmentStatus(),
                    source.testResultId(),
                    source.resultStatus(),
                    source.submittedAt(),
                    source.verifiedAt(),
                    source.earnedPoints(),
                    source.maximumPoints(),
                    source.percentage(),
                    source.performanceStatusCode(),
                    performanceStatusLabel(source, warnings, warningCodes),
                    source.testResultId() == null ? null : source.pendingTeacherVerificationCount()
            ));
        }
        return List.copyOf(rows);
    }

    private String performanceStatusLabel(
            AssessmentResultRow row,
            List<ReportWarning> warnings,
            Set<String> warningCodes
    ) {
        if (row.performanceStatusCode() == null || row.performanceStatusCode().isBlank()) {
            return null;
        }
        if (row.performanceRuleDefinition() != null && !row.performanceRuleDefinition().isBlank()) {
            try {
                JsonNode bands = objectMapper.readTree(row.performanceRuleDefinition()).path("bands");
                if (bands.isArray()) {
                    for (JsonNode band : bands) {
                        if (row.performanceStatusCode().equalsIgnoreCase(band.path("status").asText())) {
                            String label = band.path("label").asText(null);
                            if (label != null && !label.isBlank()) {
                                return label;
                            }
                        }
                    }
                }
            } catch (JsonProcessingException exception) {
                addWarning(
                        warnings,
                        warningCodes,
                        "PERFORMANCE_RULE_LABEL_UNAVAILABLE",
                        "A stored performance rule label could not be read; a normalized status label was used."
                );
            }
        }
        return humanize(row.performanceStatusCode());
    }

    private CalculationPolicy calculationPolicy(List<AssessmentResultRow> rows) {
        Map<Long, PerformanceRuleSetReference> references = new LinkedHashMap<>();
        for (AssessmentResultRow row : rows) {
            if (isFinalized(row) && row.performanceRuleSetId() != null) {
                references.putIfAbsent(
                        row.performanceRuleSetId(),
                        new PerformanceRuleSetReference(
                                row.performanceRuleSetId(),
                                row.performanceRuleSetName(),
                                row.performanceRuleVersion()
                        )
                );
            }
        }
        return new CalculationPolicy(
                "Finalized test_results snapshots computed by the V3 backend from finalized student_answers.",
                "One latest non-superseded result per class-list enrollment; score metrics are exposed only for finalized results.",
                "Class mean percentage is weighted: sum(earnedPoints) / sum(maximumPoints) * 100 for finalized results.",
                "HALF_UP to 2 decimal places.",
                List.copyOf(references.values())
        );
    }

    private Summary summarize(AssessmentScopeRow scope, List<AssessmentResultRow> rows) {
        int submittedCount = 0;
        int verifiedCount = 0;
        int pendingCount = 0;
        BigDecimal totalEarned = BigDecimal.ZERO;
        BigDecimal totalMaximum = BigDecimal.ZERO;

        for (AssessmentResultRow row : rows) {
            if (row.testResultId() != null) {
                // Mobile finalization can produce an official snapshot without submitted_at.
                if (row.submittedAt() != null || isFinalized(row)) {
                    submittedCount++;
                }
                if (isFinalized(row)) {
                    verifiedCount++;
                    totalEarned = totalEarned.add(row.earnedPoints());
                    totalMaximum = totalMaximum.add(row.maximumPoints());
                } else {
                    pendingCount++;
                }
            }
        }

        BigDecimal classMeanPoints = verifiedCount == 0
                ? null
                : totalEarned.divide(BigDecimal.valueOf(verifiedCount), METRIC_SCALE, RoundingMode.HALF_UP);
        BigDecimal classMeanPercentage = verifiedCount == 0 || totalMaximum.signum() == 0
                ? null
                : totalEarned.multiply(BigDecimal.valueOf(100))
                        .divide(totalMaximum, METRIC_SCALE, RoundingMode.HALF_UP);

        return new Summary(
                rows.size(),
                submittedCount,
                verifiedCount,
                pendingCount,
                scale(scope.configuredMaximumPoints()),
                classMeanPoints,
                classMeanPercentage
        );
    }

    private void addCompletenessWarnings(
            Summary summary,
            List<ReportWarning> warnings,
            Set<String> warningCodes
    ) {
        if (summary.submittedCount() == 0) {
            addWarning(
                    warnings,
                    warningCodes,
                    "NO_SUBMITTED_RESULTS",
                    "No student result has been submitted for this assessment and class assignment."
            );
            return;
        }
        if (summary.pendingCount() > 0) {
            addWarning(
                    warnings,
                    warningCodes,
                    "PENDING_TEACHER_VERIFICATION",
                    "Some submitted results are still pending teacher verification and are excluded from score metrics."
            );
        }
        if (summary.submittedCount() < summary.studentCount()) {
            addWarning(
                    warnings,
                    warningCodes,
                    "STUDENTS_WITHOUT_SUBMISSION",
                    "Some learners in the class roster do not have a submitted result for this assessment."
            );
        }
    }

    private void addMaximumSnapshotWarning(
            AssessmentScopeRow scope,
            List<AssessmentResultRow> rows,
            List<ReportWarning> warnings,
            Set<String> warningCodes
    ) {
        BigDecimal configuredMaximum = scale(scope.configuredMaximumPoints());
        boolean mismatch = rows.stream()
                .filter(this::isFinalized)
                .map(AssessmentResultRow::maximumPoints)
                .map(this::scale)
                .anyMatch(maximum -> maximum.compareTo(configuredMaximum) != 0);
        if (mismatch) {
            addWarning(
                    warnings,
                    warningCodes,
                    "RESULT_MAXIMUM_SNAPSHOT_DIFFERS",
                    "A finalized result uses a different historical maximum-score snapshot; its stored snapshot was preserved."
            );
        }
    }

    private String dataStatus(Summary summary) {
        if (summary.submittedCount() == 0) {
            return "empty";
        }
        if (summary.studentCount() > 0 && summary.verifiedCount() == summary.studentCount()) {
            return "available";
        }
        return "partial";
    }

    private Scope mapScope(AssessmentScopeRow scope) {
        return new Scope(
                scope.schoolId(),
                scope.schoolName(),
                scope.academicYearId(),
                scope.academicYearName(),
                scope.termPeriodId(),
                scope.termName(),
                scope.classId(),
                scope.gradeLevelId(),
                scope.gradeLevelName(),
                scope.sectionId(),
                scope.sectionName(),
                scope.classAssignmentId(),
                scope.teacherUserId(),
                scope.teacherName(),
                scope.subjectId(),
                scope.subjectName(),
                scope.testId(),
                scope.testAssignmentId(),
                scope.testName(),
                scope.testType(),
                scope.testStatus(),
                scope.assignmentStatus(),
                scope.openAt(),
                scope.closeAt()
        );
    }

    private void validateIdentifiers(long testId, long classAssignmentId) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("code", "INVALID_REPORT_FILTERS");
        if (testId <= 0) {
            errors.put("testId", "testId must be a positive identifier.");
        }
        if (classAssignmentId <= 0) {
            errors.put("classAssignmentId", "classAssignmentId must be a positive identifier.");
        }
        if (errors.size() > 1) {
            throw new V3FieldValidationException(
                    "The report filters are invalid.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    errors
            );
        }
    }

    private void requireVisibleTest(AccessContext access, long testId) {
        String testSchoolId = reportRepository.findTestSchoolId(testId)
                .orElseThrow(() -> new V3AuthException(
                        "ASSESSMENT_NOT_FOUND",
                        "The requested assessment was not found.",
                        HttpStatus.NOT_FOUND
                ));
        if (!access.schoolId().equals(testSchoolId)) {
            throw forbidden();
        }
    }

    private void requireScopeAccess(AccessContext access, AssessmentScopeRow scope) {
        if (!access.schoolId().equals(scope.schoolId())) {
            throw forbidden();
        }
        if (access.isTeacher() && access.userId() != scope.teacherUserId()) {
            throw forbidden();
        }
    }

    private AccessContext requireActiveUser(V3AuthenticatedUser user) {
        if (user == null) {
            throw new V3AuthException(
                    "AUTHENTICATION_REQUIRED",
                    "Authentication is required.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        String role = user.role() == null ? "" : user.role().trim().toLowerCase(Locale.ROOT);
        if (!"active".equalsIgnoreCase(user.status())
                || user.schoolId() == null
                || user.schoolId().isBlank()
                || !ALLOWED_ROLES.contains(role)) {
            throw new V3AuthException(
                    "ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active principal or teacher account associated with a school is required.",
                    HttpStatus.FORBIDDEN
            );
        }
        return new AccessContext(user.userId(), user.schoolId(), role);
    }

    private V3AuthException forbidden() {
        return new V3AuthException(
                "REPORT_SCOPE_FORBIDDEN",
                "The authenticated user cannot access the selected report scope.",
                HttpStatus.FORBIDDEN
        );
    }

    private boolean isFinalized(AssessmentResultRow row) {
        return FINALIZED.equalsIgnoreCase(row.resultStatus())
                && row.earnedPoints() != null
                && row.maximumPoints() != null;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(METRIC_SCALE) :
                value.setScale(METRIC_SCALE, RoundingMode.HALF_UP);
    }

    private void addWarning(
            List<ReportWarning> warnings,
            Set<String> warningCodes,
            String code,
            String message
    ) {
        if (warningCodes.add(code)) {
            warnings.add(new ReportWarning(code, message));
        }
    }

    private String humanize(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    private record AccessContext(long userId, String schoolId, String role) {
        boolean isTeacher() {
            return "teacher".equals(role);
        }
    }
}
