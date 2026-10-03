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
import com.capstone.assessment.v3.report.dto.V3ConsolidatedReportResponse;
import com.capstone.assessment.v3.report.dto.V3ItemAnalysisReportResponse;
import com.capstone.assessment.v3.report.dto.V3LearningCompetencyReportResponse;
import com.capstone.assessment.v3.report.dto.V3ReportReferenceDataResponse;
import com.capstone.assessment.v3.report.dto.V3StudentPerformanceProfileResponse;
import com.capstone.assessment.v3.report.dto.V3SyncActivityReportResponse;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentResultRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentScopeRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.CompetencyScopeRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ConsolidatedExportScope;
import com.capstone.assessment.v3.report.model.V3ReportModels.ConsolidatedGroupRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ConsolidatedSkillAnswerTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ConsolidatedSkillItemTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ConsolidatedStatusCountRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ItemAnalysisRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.SkillAnswerTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.SkillItemTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.SkillMasteryRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentAssessmentHistoryRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentClassContext;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentCompetencyTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentSkillAnswerTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentSkillItemTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentSkillMasteryRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.V3ReportGroupDimension;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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
    // Matches the "maintain" band of the seeded performance rule sets, so the mastery pill and
    // the intervention recommendation never disagree (e.g. "developing" next to "Maintain").
    private static final BigDecimal MASTERY_MAINTAIN_THRESHOLD = new BigDecimal("80");
    private static final BigDecimal MASTERY_DEVELOPING_THRESHOLD = new BigDecimal("60");
    private static final BigDecimal DIFFICULTY_EASY_THRESHOLD = new BigDecimal("75");
    private static final BigDecimal DIFFICULTY_MODERATE_THRESHOLD = new BigDecimal("40");
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Manila");

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

    @Transactional(readOnly = true)
    public V3ItemAnalysisReportResponse getItemAnalysisReport(
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

        List<ItemAnalysisRow> itemRows = reportRepository.listItemAnalysis(scope);
        List<SkillMasteryRow> masteryRows = mergeSkillMastery(
                reportRepository.listSkillItemTotals(scope.testId()),
                reportRepository.listSkillAnswerTotals(scope.testAssignmentId(), scope.testId())
        );

        Map<Long, List<StudentSkillMasteryRow>> studentsBySkill = reportRepository
                .listStudentSkillMastery(scope.testAssignmentId(), scope.testId())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(StudentSkillMasteryRow::skillId));

        List<ReportWarning> warnings = new ArrayList<>();
        Set<String> warningCodes = new LinkedHashSet<>();
        List<InterventionBand> interventionBands = loadInterventionBands(access.schoolId(), warnings, warningCodes);

        List<V3ItemAnalysisReportResponse.QuestionRow> rows = itemRows.stream()
                .map(this::mapItemAnalysisRow)
                .toList();
        List<V3ItemAnalysisReportResponse.CompetencyMasteryRow> competencyMastery = masteryRows.stream()
                .map(row -> mapSkillMasteryRow(
                        row, studentsBySkill.getOrDefault(row.skillId(), List.of()), interventionBands))
                .toList();

        boolean anyFinalized = itemRows.stream()
                .anyMatch(row -> row.correctCount() + row.incorrectCount() > 0);
        String dataStatus;
        if (!anyFinalized) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "NO_SUBMITTED_RESULTS",
                    "No finalized result exists yet for this assessment and class assignment.");
        } else {
            dataStatus = "available";
        }

        return new V3ItemAnalysisReportResponse(
                "item_analysis_competency_mastery",
                clock.instant(),
                mapScope(scope),
                dataStatus,
                warnings,
                rows,
                competencyMastery
        );
    }

    private List<SkillMasteryRow> mergeSkillMastery(
            List<SkillItemTotalRow> itemTotals,
            List<SkillAnswerTotalRow> answerTotals
    ) {
        Map<Long, SkillAnswerTotalRow> answersBySkill = answerTotals.stream()
                .collect(java.util.stream.Collectors.toMap(SkillAnswerTotalRow::skillId, row -> row));
        List<SkillMasteryRow> merged = new ArrayList<>(itemTotals.size());
        for (SkillItemTotalRow itemTotal : itemTotals) {
            SkillAnswerTotalRow answerTotal = answersBySkill.get(itemTotal.skillId());
            merged.add(new SkillMasteryRow(
                    itemTotal.skillId(),
                    itemTotal.competencyName(),
                    itemTotal.assessedItemCount(),
                    answerTotal == null ? 0 : answerTotal.studentCount(),
                    answerTotal == null ? BigDecimal.ZERO : answerTotal.earnedPoints(),
                    itemTotal.possiblePointsPerStudent()
            ));
        }
        return merged;
    }

    private V3ItemAnalysisReportResponse.QuestionRow mapItemAnalysisRow(ItemAnalysisRow row) {
        int graded = row.correctCount() + row.incorrectCount();
        BigDecimal difficultyIndex = graded == 0
                ? null
                : BigDecimal.valueOf(row.correctCount())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(graded), METRIC_SCALE, RoundingMode.HALF_UP);
        return new V3ItemAnalysisReportResponse.QuestionRow(
                row.questionId(),
                row.testPartId(),
                row.itemNumber(),
                row.questionTypeCode(),
                row.correctCount(),
                row.incorrectCount(),
                row.unansweredCount(),
                difficultyIndex,
                difficultyLabel(difficultyIndex),
                row.skillIds()
        );
    }

    private V3ItemAnalysisReportResponse.CompetencyMasteryRow mapSkillMasteryRow(
            SkillMasteryRow row,
            List<StudentSkillMasteryRow> studentRows,
            List<InterventionBand> interventionBands
    ) {
        BigDecimal possiblePerStudent = row.possiblePointsPerStudent();
        BigDecimal totalPossible = row.studentCount() == 0 || possiblePerStudent == null
                ? BigDecimal.ZERO
                : possiblePerStudent.multiply(BigDecimal.valueOf(row.studentCount()));
        BigDecimal masteryPercentage = row.studentCount() == 0 || totalPossible.signum() == 0
                ? null
                : row.earnedPoints().multiply(BigDecimal.valueOf(100))
                        .divide(totalPossible, METRIC_SCALE, RoundingMode.HALF_UP);

        List<V3ItemAnalysisReportResponse.StudentSkillMastery> students = studentRows.stream()
                .map(student -> {
                    BigDecimal studentMastery = possiblePerStudent == null || possiblePerStudent.signum() == 0
                            ? null
                            : student.earnedPoints().multiply(BigDecimal.valueOf(100))
                                    .divide(possiblePerStudent, METRIC_SCALE, RoundingMode.HALF_UP);
                    Recommendation recommendation =
                            recommend(interventionBands, studentMastery, row.competencyName());
                    return new V3ItemAnalysisReportResponse.StudentSkillMastery(
                            student.studentId(),
                            student.fullName(),
                            studentMastery,
                            masteryStatusCode(studentMastery),
                            recommendation == null ? null : recommendation.code(),
                            recommendation == null ? null : recommendation.label(),
                            recommendation == null ? null : recommendation.suggestion()
                    );
                })
                .sorted(Comparator
                        .comparing(V3ItemAnalysisReportResponse.StudentSkillMastery::masteryPercentage,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(V3ItemAnalysisReportResponse.StudentSkillMastery::fullName,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new V3ItemAnalysisReportResponse.CompetencyMasteryRow(
                row.skillId(),
                row.competencyName(),
                row.assessedItemCount(),
                row.studentCount(),
                masteryPercentage,
                masteryStatusCode(masteryPercentage),
                students
        );
    }

    private String masteryStatusCode(BigDecimal masteryPercentage) {
        if (masteryPercentage == null) {
            return "insufficient_data";
        }
        if (masteryPercentage.compareTo(MASTERY_MAINTAIN_THRESHOLD) >= 0) {
            return "mastered";
        }
        if (masteryPercentage.compareTo(MASTERY_DEVELOPING_THRESHOLD) >= 0) {
            return "developing";
        }
        return "needs_support";
    }

    /** One band of the "intervention" rule set, e.g. [40, 60) -> reteach. */
    private record InterventionBand(
            BigDecimal minimum,
            BigDecimal maximum,
            boolean minimumInclusive,
            boolean maximumInclusive,
            String status,
            String label,
            String template
    ) {
        boolean contains(BigDecimal value) {
            int lower = value.compareTo(minimum);
            int upper = value.compareTo(maximum);
            return (minimumInclusive ? lower >= 0 : lower > 0) && (maximumInclusive ? upper <= 0 : upper < 0);
        }
    }

    private record Recommendation(String code, String label, String suggestion) {
    }

    /** Thresholds, labels and suggestion wording are owned by the school's active
     *  "intervention" rule set (performance_rule_sets), not hardcoded here. */
    private List<InterventionBand> loadInterventionBands(
            String schoolId,
            List<ReportWarning> warnings,
            Set<String> warningCodes
    ) {
        String definition = reportRepository.findActiveRuleDefinition(schoolId, "intervention").orElse(null);
        if (definition == null) {
            addWarning(warnings, warningCodes, "INTERVENTION_RULES_UNAVAILABLE",
                    "No active intervention rule set is configured, so no recommendations were generated.");
            return List.of();
        }
        try {
            JsonNode bandNodes = objectMapper.readTree(definition).path("bands");
            List<InterventionBand> bands = new ArrayList<>();
            for (JsonNode band : bandNodes) {
                bands.add(new InterventionBand(
                        new BigDecimal(band.path("minimum_percentage").asText("0")),
                        new BigDecimal(band.path("maximum_percentage").asText("100")),
                        band.path("minimum_inclusive").asBoolean(true),
                        band.path("maximum_inclusive").asBoolean(false),
                        band.path("status").asText(null),
                        band.path("label").asText(null),
                        band.path("recommendation_template").asText(null)
                ));
            }
            return bands;
        } catch (JsonProcessingException | NumberFormatException exception) {
            addWarning(warnings, warningCodes, "INTERVENTION_RULES_UNAVAILABLE",
                    "The intervention rule set could not be read, so no recommendations were generated.");
            return List.of();
        }
    }

    private Recommendation recommend(List<InterventionBand> bands, BigDecimal masteryPercentage, String skillName) {
        if (masteryPercentage == null) {
            return null;
        }
        for (InterventionBand band : bands) {
            if (band.status() != null && band.contains(masteryPercentage)) {
                String label = band.label() == null || band.label().isBlank()
                        ? humanize(band.status())
                        : band.label();
                // Competency statements are often stored as full sentences ("Use ... sentences."),
                // so drop a trailing period before placing one mid-sentence in the template.
                String name = skillName == null ? "this skill" : skillName.strip().replaceAll("[.\\s]+$", "");
                String suggestion = band.template() == null
                        ? null
                        : band.template().replace("{competency_name}", name);
                return new Recommendation(band.status(), label, suggestion);
            }
        }
        return null;
    }

    private static final int LEAST_MASTERED_SKILLS_LIMIT = 5;

    @Transactional(readOnly = true)
    public V3ConsolidatedReportResponse getConsolidatedReport(
            V3AuthenticatedUser user,
            V3ReportGroupDimension groupBy,
            Integer academicYearId,
            Integer termPeriodId,
            Integer gradeLevelId,
            Long classId,
            Long teacherUserId,
            Integer subjectId,
            Long testId
    ) {
        AccessContext access = requireActiveUser(user);
        // Deliberately boxed: a bare `access.isTeacher() ? access.userId() : teacherUserId`
        // mixes a primitive long with a nullable Long, which forces Java to unbox teacherUserId
        // even on the branch that isn't taken - throwing NPE for the normal principal,
        // no-filter, school-wide case where teacherUserId is legitimately null.
        Long effectiveTeacherFilter = access.isTeacher() ? Long.valueOf(access.userId()) : teacherUserId;

        List<ConsolidatedGroupRow> groupRows = reportRepository.listConsolidatedGroups(
                groupBy, access.schoolId(), access.isTeacher() ? access.userId() : null,
                academicYearId, termPeriodId, gradeLevelId, classId, effectiveTeacherFilter, subjectId, testId);
        List<ConsolidatedStatusCountRow> statusRows = reportRepository.listConsolidatedStatusCounts(
                groupBy, access.schoolId(), access.isTeacher() ? access.userId() : null,
                academicYearId, termPeriodId, gradeLevelId, classId, effectiveTeacherFilter, subjectId, testId);
        List<ConsolidatedSkillItemTotalRow> skillItemRows = reportRepository.listConsolidatedSkillItemTotals(
                groupBy, access.schoolId(), access.isTeacher() ? access.userId() : null,
                academicYearId, termPeriodId, gradeLevelId, classId, effectiveTeacherFilter, subjectId, testId);
        List<ConsolidatedSkillAnswerTotalRow> skillAnswerRows = reportRepository.listConsolidatedSkillAnswerTotals(
                groupBy, access.schoolId(), access.isTeacher() ? access.userId() : null,
                academicYearId, termPeriodId, gradeLevelId, classId, effectiveTeacherFilter, subjectId, testId);

        Map<String, List<ConsolidatedStatusCountRow>> statusByGroup = statusRows.stream()
                .collect(java.util.stream.Collectors.groupingBy(ConsolidatedStatusCountRow::groupKey));
        Map<String, Map<Long, ConsolidatedSkillAnswerTotalRow>> answersByGroupAndSkill = skillAnswerRows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ConsolidatedSkillAnswerTotalRow::groupKey,
                        java.util.stream.Collectors.toMap(
                                ConsolidatedSkillAnswerTotalRow::skillId, row -> row)));
        Map<String, List<ConsolidatedSkillItemTotalRow>> itemsByGroup = skillItemRows.stream()
                .collect(java.util.stream.Collectors.groupingBy(ConsolidatedSkillItemTotalRow::groupKey));

        List<V3ConsolidatedReportResponse.GroupRow> groups = new ArrayList<>();
        for (ConsolidatedGroupRow row : groupRows) {
            BigDecimal meanPercentage = row.possiblePoints().signum() == 0
                    ? null
                    : row.earnedPoints().multiply(BigDecimal.valueOf(100))
                            .divide(row.possiblePoints(), METRIC_SCALE, RoundingMode.HALF_UP);

            List<V3ConsolidatedReportResponse.MasteryStatusCount> statusCounts =
                    statusByGroup.getOrDefault(row.groupKey(), List.of()).stream()
                            .map(status -> new V3ConsolidatedReportResponse.MasteryStatusCount(
                                    status.performanceStatus(), status.count()))
                            .toList();

            List<ConsolidatedSkillItemTotalRow> groupItems = itemsByGroup.getOrDefault(row.groupKey(), List.of());
            Map<Long, ConsolidatedSkillAnswerTotalRow> groupAnswers =
                    answersByGroupAndSkill.getOrDefault(row.groupKey(), Map.of());
            List<V3ConsolidatedReportResponse.LeastMasteredSkill> leastMastered = groupItems.stream()
                    .map(item -> {
                        ConsolidatedSkillAnswerTotalRow answer = groupAnswers.get(item.skillId());
                        int studentCount = answer == null ? 0 : answer.studentCount();
                        BigDecimal earned = answer == null ? BigDecimal.ZERO : answer.earnedPoints();
                        BigDecimal totalPossible = studentCount == 0
                                ? BigDecimal.ZERO
                                : item.possiblePointsPerStudent().multiply(BigDecimal.valueOf(studentCount));
                        BigDecimal masteryPercentage = studentCount == 0 || totalPossible.signum() == 0
                                ? null
                                : earned.multiply(BigDecimal.valueOf(100))
                                        .divide(totalPossible, METRIC_SCALE, RoundingMode.HALF_UP);
                        return new V3ConsolidatedReportResponse.LeastMasteredSkill(
                                item.skillId(), item.competencyName(), masteryPercentage,
                                masteryStatusCode(masteryPercentage));
                    })
                    .sorted(Comparator.comparing(
                            V3ConsolidatedReportResponse.LeastMasteredSkill::masteryPercentage,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(LEAST_MASTERED_SKILLS_LIMIT)
                    .toList();

            groups.add(new V3ConsolidatedReportResponse.GroupRow(
                    row.groupKey(), row.groupLabel(), row.studentCount(), meanPercentage,
                    statusCounts, leastMastered));
        }

        List<ReportWarning> warnings = new ArrayList<>();
        Set<String> warningCodes = new LinkedHashSet<>();
        String dataStatus;
        if (groups.isEmpty()) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "NO_GROUPS_MATCHED",
                    "No roster/assignment data matched the selected filters.");
        } else if (groups.stream().allMatch(group -> group.meanPercentage() == null)) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "NO_SUBMITTED_RESULTS",
                    "No finalized result exists yet for the selected scope.");
        } else if (groups.stream().anyMatch(group -> group.meanPercentage() == null)) {
            dataStatus = "partial";
        } else {
            dataStatus = "available";
        }

        BigDecimal earnedTotal = groupRows.stream().map(ConsolidatedGroupRow::earnedPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal possibleTotal = groupRows.stream().map(ConsolidatedGroupRow::possiblePoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overallMeanPercentage = possibleTotal.signum() == 0
                ? null
                : earnedTotal.multiply(BigDecimal.valueOf(100)).divide(possibleTotal, METRIC_SCALE, RoundingMode.HALF_UP);

        return new V3ConsolidatedReportResponse(
                "principal_consolidated",
                clock.instant(),
                groupBy.name(),
                dataStatus,
                warnings,
                groups,
                overallMeanPercentage
        );
    }

    /** Printed labels for a consolidated export; only the filters actually applied are looked up. */
    @Transactional(readOnly = true)
    public ConsolidatedExportScope describeConsolidatedScope(
            V3AuthenticatedUser user,
            Integer academicYearId,
            Integer termPeriodId,
            Integer gradeLevelId,
            Long classId,
            Long teacherUserId,
            Integer subjectId,
            Long testId
    ) {
        AccessContext access = requireActiveUser(user);
        String schoolId = access.schoolId();
        Long ownTeacher = access.isTeacher() ? access.userId() : null;
        String preparedBy = reportRepository.findUserFullName(access.userId()).orElse(null);
        String academicYear = academicYearId == null ? null : reportRepository.listAcademicYears(schoolId, ownTeacher)
                .stream().filter(option -> option.academicYearId() == academicYearId)
                .map(V3ReportReferenceDataResponse.AcademicYearOption::yearName).findFirst().orElse(null);
        String term = termPeriodId == null ? null : reportRepository.listTermPeriods(schoolId, ownTeacher)
                .stream().filter(option -> option.termPeriodId() == termPeriodId)
                .map(V3ReportReferenceDataResponse.TermPeriodOption::termName).findFirst().orElse(null);
        String gradeLevel = gradeLevelId == null ? null : reportRepository.listGradeLevels(schoolId, ownTeacher)
                .stream().filter(option -> option.gradeLevelId() == gradeLevelId)
                .map(V3ReportReferenceDataResponse.GradeLevelOption::gradeLevelName).findFirst().orElse(null);
        String classLabel = classId == null ? null : reportRepository.listClasses(schoolId, ownTeacher)
                .stream().filter(option -> option.classId() == classId)
                .map(option -> option.gradeLevelName() + " - " + option.sectionName()).findFirst().orElse(null);
        String teacher = access.isTeacher() ? preparedBy
                : teacherUserId == null ? null : reportRepository.listTeachers(schoolId, null)
                        .stream().filter(option -> option.teacherUserId() == teacherUserId)
                        .map(V3ReportReferenceDataResponse.TeacherOption::fullName).findFirst().orElse(null);
        String subject = subjectId == null ? null : reportRepository.listSubjects(schoolId, ownTeacher)
                .stream().filter(option -> option.subjectId() == subjectId)
                .map(V3ReportReferenceDataResponse.SubjectOption::subjectName).findFirst().orElse(null);
        String assessment = testId == null ? null : reportRepository.listAssessments(schoolId, ownTeacher)
                .stream().filter(option -> option.testId() == testId)
                .map(V3ReportReferenceDataResponse.AssessmentOption::testName).findFirst().orElse(null);
        return new ConsolidatedExportScope(
                reportRepository.findSchool(schoolId).map(V3ReportReferenceDataResponse.SchoolOption::schoolName)
                        .orElse(null),
                preparedBy,
                access.isTeacher() ? "Teacher" : "Principal",
                academicYear, term, gradeLevel, classLabel, teacher, subject, assessment);
    }

    @Transactional(readOnly = true)
    public V3StudentPerformanceProfileResponse getStudentPerformanceProfile(
            V3AuthenticatedUser user,
            long studentId
    ) {
        AccessContext access = requireActiveUser(user);
        Long scopeTeacherId = access.isTeacher() ? access.userId() : null;

        List<Long> classListIds = reportRepository.listClassListIdsForStudent(
                studentId, access.schoolId(), scopeTeacherId);
        if (classListIds.isEmpty()) {
            throw new V3AuthException(
                    "STUDENT_NOT_FOUND",
                    "No visible student roster entry was found for this student.",
                    HttpStatus.NOT_FOUND
            );
        }
        V3ReportRepository.StudentIdentity identity = reportRepository
                .findStudentIdentity(studentId, access.schoolId())
                .orElseThrow(() -> new V3AuthException(
                        "STUDENT_NOT_FOUND",
                        "No visible student roster entry was found for this student.",
                        HttpStatus.NOT_FOUND
                ));
        var classContext = reportRepository.findCurrentClassContext(studentId, access.schoolId());

        // Class roster access alone must not expose results from another teacher's subject assignment.
        List<StudentAssessmentHistoryRow> historyRows =
                reportRepository.listStudentAssessmentHistory(classListIds, scopeTeacherId);
        List<StudentSkillItemTotalRow> itemTotals =
                reportRepository.listStudentSkillItemTotals(classListIds, scopeTeacherId);
        List<StudentSkillAnswerTotalRow> answerTotals =
                reportRepository.listStudentSkillAnswerTotals(classListIds, scopeTeacherId);
        Map<Long, StudentSkillAnswerTotalRow> answersBySkill = answerTotals.stream()
                .collect(java.util.stream.Collectors.toMap(StudentSkillAnswerTotalRow::skillId, row -> row));

        List<V3StudentPerformanceProfileResponse.AssessmentResult> assessmentResults = historyRows.stream()
                .map(row -> new V3StudentPerformanceProfileResponse.AssessmentResult(
                        row.testId(), row.testName(), row.termPeriodId(), row.termName(), row.percentage(),
                        row.resultStatus(), row.completedAt(), row.subjectName(), row.earnedPoints(),
                        row.maximumPoints(), row.performanceStatusCode(), row.openAt(), row.closeAt()))
                .toList();

        List<V3StudentPerformanceProfileResponse.CompetencyPerformance> competencyPerformance = itemTotals.stream()
                .map(item -> {
                    StudentSkillAnswerTotalRow answer = answersBySkill.get(item.skillId());
                    BigDecimal earned = answer == null ? BigDecimal.ZERO : answer.earnedPoints();
                    BigDecimal masteryPercentage = item.possiblePoints().signum() == 0
                            ? null
                            : earned.multiply(BigDecimal.valueOf(100))
                                    .divide(item.possiblePoints(), METRIC_SCALE, RoundingMode.HALF_UP);
                    return new V3StudentPerformanceProfileResponse.CompetencyPerformance(
                            item.skillId(), item.competencyName(), masteryPercentage,
                            masteryStatusCode(masteryPercentage));
                })
                .toList();

        List<ReportWarning> warnings = new ArrayList<>();
        Set<String> warningCodes = new LinkedHashSet<>();
        boolean anyFinalized = historyRows.stream().anyMatch(row -> "finalized".equals(row.resultStatus()));
        String dataStatus;
        if (historyRows.isEmpty()) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "NO_SUBMITTED_RESULTS",
                    "No assessment result exists yet for this student.");
        } else if (!anyFinalized) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "PENDING_TEACHER_VERIFICATION",
                    "This student's results are still pending teacher verification.");
        } else if (historyRows.stream().anyMatch(row -> !"finalized".equals(row.resultStatus()))) {
            dataStatus = "partial";
            addWarning(warnings, warningCodes, "PENDING_TEACHER_VERIFICATION",
                    "Some of this student's results are still pending teacher verification.");
        } else {
            dataStatus = "available";
        }

        List<InterventionBand> interventionBands = loadInterventionBands(access.schoolId(), warnings, warningCodes);
        List<V3StudentPerformanceProfileResponse.InterventionSuggestion> interventions = new ArrayList<>();
        for (V3StudentPerformanceProfileResponse.CompetencyPerformance skill : competencyPerformance) {
            Recommendation recommendation =
                    recommend(interventionBands, skill.masteryPercentage(), skill.skillName());
            if (recommendation != null && !"maintain".equals(recommendation.code())) {
                interventions.add(new V3StudentPerformanceProfileResponse.InterventionSuggestion(
                        skill.skillId(), skill.skillName(), skill.masteryPercentage(),
                        recommendation.code(), recommendation.label(), recommendation.suggestion()));
            }
        }
        // Lowest mastery first: the bands are percentage-ordered, so this is also most urgent first.
        interventions.sort(Comparator
                .comparing(V3StudentPerformanceProfileResponse.InterventionSuggestion::masteryPercentage)
                .thenComparing(V3StudentPerformanceProfileResponse.InterventionSuggestion::skillName,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        return new V3StudentPerformanceProfileResponse(
                "student_performance_profile",
                clock.instant(),
                studentId,
                identity.studentLrn(),
                identity.fullName(),
                classContext.map(StudentClassContext::gradeLevelName).orElse(null),
                classContext.map(StudentClassContext::sectionName).orElse(null),
                dataStatus,
                warnings,
                assessmentResults,
                competencyPerformance,
                interventions,
                reportRepository.findSchool(access.schoolId())
                        .map(V3ReportReferenceDataResponse.SchoolOption::schoolName).orElse(null),
                classContext.map(StudentClassContext::academicYearName).orElse(null),
                reportRepository.findUserFullName(access.userId()).orElse(null),
                access.isTeacher() ? "Teacher" : "Principal"
        );
    }

    /**
     * "Identify the learning competency": Term -> Grade Level -> Subject gives every root
     * competency, its specific competencies (skills) least mastered first, and the students
     * below the mastery line on each - across every finalized assessment in that term.
     * rootTagId / skillId only narrow the returned tree (used by the PDF/Excel downloads).
     */
    @Transactional(readOnly = true)
    public V3LearningCompetencyReportResponse getLearningCompetencyReport(
            V3AuthenticatedUser user,
            int termPeriodId,
            int gradeLevelId,
            int subjectId,
            Long rootTagId,
            Long skillId
    ) {
        AccessContext access = requireActiveUser(user);
        Long teacherUserId = access.isTeacher() ? Long.valueOf(access.userId()) : null;

        CompetencyScopeRow scope = reportRepository
                .findCompetencyScope(access.schoolId(), termPeriodId, gradeLevelId, subjectId)
                .orElseThrow(() -> filterNotFound("termPeriodId", "The term does not exist in this school."));
        if (scope.gradeLevelName() == null) {
            throw filterNotFound("gradeLevelId", "The grade level does not exist.");
        }
        if (scope.subjectName() == null) {
            throw filterNotFound("subjectId", "The subject does not exist.");
        }

        List<V3LearningCompetencyReportResponse.IncludedAssessment> assessments = reportRepository
                .listCompetencyAssessments(access.schoolId(), termPeriodId, gradeLevelId, subjectId, teacherUserId);
        List<StudentCompetencyTotalRow> totals = reportRepository
                .listStudentCompetencyTotals(access.schoolId(), termPeriodId, gradeLevelId, subjectId, teacherUserId);

        List<ReportWarning> warnings = new ArrayList<>();
        Set<String> warningCodes = new LinkedHashSet<>();
        List<InterventionBand> interventionBands = loadInterventionBands(access.schoolId(), warnings, warningCodes);

        Map<Long, List<StudentCompetencyTotalRow>> byRoot = totals.stream()
                .filter(row -> rootTagId == null || row.rootTagId() == rootTagId)
                .filter(row -> skillId == null || row.skillId() == skillId)
                .collect(java.util.stream.Collectors.groupingBy(
                        StudentCompetencyTotalRow::rootTagId, LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<V3LearningCompetencyReportResponse.RootCompetency> roots = new ArrayList<>();
        for (List<StudentCompetencyTotalRow> rootRows : byRoot.values()) {
            Map<Long, List<StudentCompetencyTotalRow>> bySkill = rootRows.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            StudentCompetencyTotalRow::skillId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
            List<V3LearningCompetencyReportResponse.Skill> skills = bySkill.values().stream()
                    .map(skillRows -> buildCompetencySkill(skillRows, interventionBands))
                    .sorted(Comparator
                            .comparing(V3LearningCompetencyReportResponse.Skill::masteryPercentage,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(V3LearningCompetencyReportResponse.Skill::competencyName,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            // Weighted by points, like every other mastery figure. A question mapped to two
            // skills of the same root counts once per skill here, same as in the skill rows.
            BigDecimal rootMastery = percentage(
                    sumOf(rootRows, StudentCompetencyTotalRow::earnedPoints),
                    sumOf(rootRows, StudentCompetencyTotalRow::possiblePoints));
            StudentCompetencyTotalRow first = rootRows.get(0);
            roots.add(new V3LearningCompetencyReportResponse.RootCompetency(
                    first.rootTagId(), first.rootTagName(), rootMastery, masteryStatusCode(rootMastery), skills));
        }
        roots.sort(Comparator
                .comparing(V3LearningCompetencyReportResponse.RootCompetency::masteryPercentage,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(V3LearningCompetencyReportResponse.RootCompetency::rootTagName,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        String dataStatus;
        if (assessments.isEmpty()) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "NO_SUBMITTED_RESULTS",
                    "No finalized result exists yet for this term, grade level and subject.");
        } else if (totals.isEmpty()) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "NO_COMPETENCY_MAPPING",
                    "The assessments in this term have no competencies mapped to their items.");
        } else if (roots.isEmpty()) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "NO_MATCHING_COMPETENCY",
                    "The selected competency was not assessed in this term.");
        } else {
            dataStatus = "available";
        }

        return new V3LearningCompetencyReportResponse(
                "learning_competency",
                clock.instant(),
                new V3LearningCompetencyReportResponse.Scope(
                        scope.schoolId(), scope.schoolName(), scope.academicYearId(), scope.academicYearName(),
                        scope.termPeriodId(), scope.termName(), gradeLevelId, scope.gradeLevelName(),
                        subjectId, scope.subjectName()),
                dataStatus,
                warnings,
                assessments,
                roots,
                reportRepository.findUserFullName(access.userId()).orElse(null),
                access.isTeacher() ? "Teacher" : "Principal"
        );
    }

    private V3LearningCompetencyReportResponse.Skill buildCompetencySkill(
            List<StudentCompetencyTotalRow> skillRows,
            List<InterventionBand> interventionBands
    ) {
        StudentCompetencyTotalRow first = skillRows.get(0);
        BigDecimal skillMastery = percentage(
                sumOf(skillRows, StudentCompetencyTotalRow::earnedPoints),
                sumOf(skillRows, StudentCompetencyTotalRow::possiblePoints));
        List<V3LearningCompetencyReportResponse.WeakStudent> weakStudents = new ArrayList<>();
        for (StudentCompetencyTotalRow row : skillRows) {
            BigDecimal studentMastery = percentage(row.earnedPoints(), row.possiblePoints());
            if (studentMastery == null || studentMastery.compareTo(MASTERY_MAINTAIN_THRESHOLD) >= 0) {
                continue;
            }
            Recommendation recommendation = recommend(interventionBands, studentMastery, row.competencyName());
            weakStudents.add(new V3LearningCompetencyReportResponse.WeakStudent(
                    row.studentId(),
                    row.fullName(),
                    row.sectionName(),
                    row.assessmentCount(),
                    studentMastery,
                    masteryStatusCode(studentMastery),
                    recommendation == null ? null : recommendation.code(),
                    recommendation == null ? null : recommendation.label(),
                    recommendation == null ? null : recommendation.suggestion()
            ));
        }
        weakStudents.sort(Comparator
                .comparing(V3LearningCompetencyReportResponse.WeakStudent::masteryPercentage)
                .thenComparing(V3LearningCompetencyReportResponse.WeakStudent::fullName,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        // Class-level intervention for the whole skill, from the same rule set as the students'.
        Recommendation classRecommendation = recommend(interventionBands, skillMastery, first.competencyName());
        return new V3LearningCompetencyReportResponse.Skill(
                first.skillId(),
                first.competencyId(),
                first.competencyName(),
                skillRows.size(),
                skillMastery,
                masteryStatusCode(skillMastery),
                classRecommendation == null ? null : classRecommendation.code(),
                classRecommendation == null ? null : classRecommendation.label(),
                classRecommendation == null ? null : classRecommendation.suggestion(),
                weakStudents.size(),
                weakStudents
        );
    }

    private static BigDecimal sumOf(
            List<StudentCompetencyTotalRow> rows,
            java.util.function.Function<StudentCompetencyTotalRow, BigDecimal> field
    ) {
        return rows.stream().map(field).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal percentage(BigDecimal earned, BigDecimal possible) {
        if (earned == null || possible == null || possible.signum() == 0) {
            return null;
        }
        return earned.multiply(BigDecimal.valueOf(100)).divide(possible, METRIC_SCALE, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public V3SyncActivityReportResponse getSyncActivityReport(
            V3AuthenticatedUser user,
            Integer academicYearId,
            Integer termPeriodId,
            Long teacherUserId,
            LocalDate from,
            LocalDate to
    ) {
        AccessContext access = requireActiveUser(user);
        // Boxed on purpose - see getConsolidatedReport. A teacher's teacherUserId is ignored:
        // they always see only their own activity.
        Long effectiveTeacherFilter = access.isTeacher() ? Long.valueOf(access.userId()) : teacherUserId;
        if (from != null && to != null && from.isAfter(to)) {
            throw new V3FieldValidationException(
                    "The start date must not be after the end date.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("code", "REPORT_FILTER_INVALID", "from", "Must be on or before 'to'.")
            );
        }

        // Every supplied filter narrows the same window; they intersect rather than override.
        Instant windowStart = null;
        Instant windowEnd = null;
        if (academicYearId != null) {
            V3ReportRepository.TimeWindow window = reportRepository
                    .findAcademicYearWindow(academicYearId, access.schoolId(), REPORT_ZONE)
                    .orElseThrow(() -> filterNotFound("academicYearId",
                            "The academic year does not exist in this school."));
            windowStart = later(windowStart, window.start());
            windowEnd = earlier(windowEnd, window.endExclusive());
        }
        if (termPeriodId != null) {
            V3ReportRepository.TimeWindow window = reportRepository
                    .findTermPeriodWindow(termPeriodId, access.schoolId())
                    .orElseThrow(() -> filterNotFound("termPeriodId",
                            "The term period does not exist in this school."));
            windowStart = later(windowStart, window.start());
            windowEnd = earlier(windowEnd, window.endExclusive());
        }
        if (from != null) {
            windowStart = later(windowStart, from.atStartOfDay(REPORT_ZONE).toInstant());
        }
        if (to != null) {
            windowEnd = earlier(windowEnd, to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant());
        }

        List<V3SyncActivityReportResponse.AssessmentSync> assessments = reportRepository.listSyncedAssessments(
                access.schoolId(), effectiveTeacherFilter, windowStart, windowEnd);

        // Built from the same assessment rows, so the two sections can never disagree.
        Map<Long, List<V3SyncActivityReportResponse.AssessmentSync>> byTeacher = assessments.stream()
                .collect(java.util.stream.Collectors.groupingBy(V3SyncActivityReportResponse.AssessmentSync::teacherUserId));
        List<V3SyncActivityReportResponse.TeacherSummary> teachers = reportRepository
                .listTeachers(access.schoolId(), effectiveTeacherFilter)
                .stream()
                .map(teacher -> {
                    List<V3SyncActivityReportResponse.AssessmentSync> synced =
                            byTeacher.getOrDefault(teacher.teacherUserId(), List.of());
                    Instant lastSyncedAt = synced.stream()
                            .map(V3SyncActivityReportResponse.AssessmentSync::lastSyncedAt)
                            .filter(java.util.Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    return new V3SyncActivityReportResponse.TeacherSummary(
                            teacher.teacherUserId(),
                            teacher.fullName(),
                            lastSyncedAt,
                            (int) synced.stream().filter(assessment -> assessment.lastSyncedAt() != null).count(),
                            synced.stream().mapToInt(V3SyncActivityReportResponse.AssessmentSync::resultsNotUploaded).sum()
                    );
                })
                // Most up-to-date first; teachers who never synced go last.
                .sorted(Comparator
                        .comparing(V3SyncActivityReportResponse.TeacherSummary::lastSyncedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(V3SyncActivityReportResponse.TeacherSummary::teacherName,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<ReportWarning> warnings = new ArrayList<>();
        Set<String> warningCodes = new LinkedHashSet<>();
        String dataStatus;
        if (teachers.isEmpty()) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "NO_TEACHERS_MATCHED",
                    "No teacher matched the selected filters.");
        } else if (assessments.isEmpty()) {
            dataStatus = "empty";
            addWarning(warnings, warningCodes, "NO_SYNC_ACTIVITY",
                    "No synchronization activity was recorded in the selected period.");
        } else {
            dataStatus = "available";
        }

        return new V3SyncActivityReportResponse(
                "teacher_sync_activity",
                clock.instant(),
                windowStart,
                windowEnd,
                dataStatus,
                warnings,
                teachers,
                assessments,
                reportRepository.findSchool(access.schoolId())
                        .map(V3ReportReferenceDataResponse.SchoolOption::schoolName).orElse(null),
                reportRepository.findUserFullName(access.userId()).orElse(null),
                access.isTeacher() ? "Teacher" : "Principal"
        );
    }

    private static Instant later(Instant current, Instant candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static Instant earlier(Instant current, Instant candidate) {
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private V3FieldValidationException filterNotFound(String field, String message) {
        return new V3FieldValidationException(
                message,
                HttpStatus.NOT_FOUND,
                Map.of("code", "REPORT_FILTER_NOT_FOUND", field, message)
        );
    }

    private String difficultyLabel(BigDecimal difficultyIndex) {
        if (difficultyIndex == null) {
            return null;
        }
        if (difficultyIndex.compareTo(DIFFICULTY_EASY_THRESHOLD) >= 0) {
            return "Easy";
        }
        if (difficultyIndex.compareTo(DIFFICULTY_MODERATE_THRESHOLD) >= 0) {
            return "Moderate";
        }
        return "Difficult";
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
                    source.resultUuid(),
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
