package com.capstone.assessment.v3.report.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.report.dto.V3LearningCompetencyReportResponse;
import com.capstone.assessment.v3.report.dto.V3ReportReferenceDataResponse;
import com.capstone.assessment.v3.report.dto.V3SyncActivityReportResponse;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentResultRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentScopeRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.CompetencyScopeRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentCompetencyTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentSkillAnswerTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentSkillItemTotalRow;
import com.capstone.assessment.v3.report.repository.V3ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V3ReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T02:00:00Z");
    private static final String SCHOOL_ID = "SCHOOL-001";
    private static final V3AuthenticatedUser PRINCIPAL = user(10L, "principal", SCHOOL_ID);
    private static final V3AuthenticatedUser TEACHER = user(20L, "teacher", SCHOOL_ID);

    @Mock
    private V3ReportRepository reportRepository;

    private V3ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new V3ReportService(
                reportRepository,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void teacherReferenceDataIsOwnershipScopedAndDoesNotExposeTeacherDirectory() {
        when(reportRepository.findSchool(SCHOOL_ID)).thenReturn(Optional.of(
                new V3ReportReferenceDataResponse.SchoolOption(SCHOOL_ID, "SMART School")
        ));

        var response = reportService.getReferenceData(TEACHER);

        assertEquals("teacher", response.role());
        assertEquals(SCHOOL_ID, response.school().schoolId());
        assertTrue(response.teachers().isEmpty());
        verify(reportRepository).listAcademicYears(SCHOOL_ID, 20L);
        verify(reportRepository).listTermPeriods(SCHOOL_ID, 20L);
        verify(reportRepository).listClasses(SCHOOL_ID, 20L);
        verify(reportRepository).listAssessments(SCHOOL_ID, 20L);
        verify(reportRepository, never()).listTeachers(SCHOOL_ID, null);
    }

    @Test
    void principalAssessmentReportUsesOnlyFinalizedBackendScoreSnapshots() {
        AssessmentScopeRow scope = scope(20L);
        when(reportRepository.findTestSchoolId(1001L)).thenReturn(Optional.of(SCHOOL_ID));
        when(reportRepository.findAssessmentScope(1001L, 3001L)).thenReturn(Optional.of(scope));
        when(reportRepository.listLatestAssessmentResults(scope)).thenReturn(List.of(
                finalizedResult(),
                pendingResult(),
                learnerWithoutResult()
        ));

        var response = reportService.getAssessmentResults(PRINCIPAL, 1001L, 3001L);

        assertEquals("assessment_results", response.reportType());
        assertEquals(NOW, response.generatedAt());
        assertEquals("partial", response.dataStatus());
        assertEquals(3, response.summary().studentCount());
        assertEquals(2, response.summary().submittedCount());
        assertEquals(1, response.summary().verifiedCount());
        assertEquals(1, response.summary().pendingCount());
        assertEquals(new BigDecimal("10.00"), response.summary().maximumPoints());
        assertEquals(new BigDecimal("8.00"), response.summary().classMeanPoints());
        assertEquals(new BigDecimal("80.00"), response.summary().classMeanPercentage());

        assertEquals("Maintain", response.rows().get(0).performanceStatusLabel());
        assertEquals(new BigDecimal("8.00"), response.rows().get(0).earnedPoints());
        assertNull(response.rows().get(1).earnedPoints());
        assertEquals(2, response.rows().get(1).pendingTeacherVerificationCount());
        assertNull(response.rows().get(2).testResultId());
        assertEquals(1, response.calculationPolicy().performanceRuleSets().size());
        assertTrue(response.warnings().stream().anyMatch(
                warning -> "PENDING_TEACHER_VERIFICATION".equals(warning.code())
        ));
        assertTrue(response.warnings().stream().anyMatch(
                warning -> "STUDENTS_WITHOUT_SUBMISSION".equals(warning.code())
        ));
    }

    @Test
    void finalizedMobileResultWithoutSubmissionTimestampMakesReportAvailable() {
        AssessmentScopeRow scope = scope(20L);
        when(reportRepository.findTestSchoolId(1001L)).thenReturn(Optional.of(SCHOOL_ID));
        when(reportRepository.findAssessmentScope(1001L, 3001L)).thenReturn(Optional.of(scope));
        when(reportRepository.listLatestAssessmentResults(scope))
                .thenReturn(List.of(finalizedResult(null)));

        var response = reportService.getAssessmentResults(PRINCIPAL, 1001L, 3001L);

        assertEquals("available", response.dataStatus());
        assertEquals(1, response.summary().submittedCount());
        assertEquals(1, response.summary().verifiedCount());
        assertEquals(0, response.summary().pendingCount());
        assertEquals(new BigDecimal("8.00"), response.summary().classMeanPoints());
        assertEquals(new BigDecimal("80.00"), response.summary().classMeanPercentage());
        assertNull(response.rows().get(0).submittedAt());
        assertEquals(NOW.minusSeconds(300), response.rows().get(0).verifiedAt());
        assertTrue(response.warnings().isEmpty());
    }

    @Test
    void finalizedMobileResultWithMissingLearnerRemainsPartial() {
        AssessmentScopeRow scope = scope(20L);
        when(reportRepository.findTestSchoolId(1001L)).thenReturn(Optional.of(SCHOOL_ID));
        when(reportRepository.findAssessmentScope(1001L, 3001L)).thenReturn(Optional.of(scope));
        when(reportRepository.listLatestAssessmentResults(scope))
                .thenReturn(List.of(finalizedResult(null), learnerWithoutResult()));

        var response = reportService.getAssessmentResults(PRINCIPAL, 1001L, 3001L);

        assertEquals("partial", response.dataStatus());
        assertEquals(2, response.summary().studentCount());
        assertEquals(1, response.summary().submittedCount());
        assertEquals(1, response.summary().verifiedCount());
        assertEquals(1, response.warnings().size());
        assertEquals("STUDENTS_WITHOUT_SUBMISSION", response.warnings().get(0).code());
        assertNull(response.rows().get(1).earnedPoints());
    }

    @Test
    void pendingResultWithoutSubmissionTimestampDoesNotBecomeAnOfficialSubmission() {
        AssessmentScopeRow scope = scope(20L);
        when(reportRepository.findTestSchoolId(1001L)).thenReturn(Optional.of(SCHOOL_ID));
        when(reportRepository.findAssessmentScope(1001L, 3001L)).thenReturn(Optional.of(scope));
        when(reportRepository.listLatestAssessmentResults(scope))
                .thenReturn(List.of(pendingResult(null)));

        var response = reportService.getAssessmentResults(PRINCIPAL, 1001L, 3001L);

        assertEquals("empty", response.dataStatus());
        assertEquals(0, response.summary().submittedCount());
        assertEquals(0, response.summary().verifiedCount());
        assertEquals(1, response.summary().pendingCount());
        assertNull(response.summary().classMeanPoints());
        assertNull(response.summary().classMeanPercentage());
        assertNull(response.rows().get(0).earnedPoints());
        assertEquals("NO_SUBMITTED_RESULTS", response.warnings().get(0).code());
    }

    @Test
    void teacherCannotReadAnotherTeachersClassAssignment() {
        AssessmentScopeRow otherTeacherScope = scope(21L);
        when(reportRepository.findTestSchoolId(1001L)).thenReturn(Optional.of(SCHOOL_ID));
        when(reportRepository.findAssessmentScope(1001L, 3001L))
                .thenReturn(Optional.of(otherTeacherScope));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> reportService.getAssessmentResults(TEACHER, 1001L, 3001L)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("REPORT_SCOPE_FORBIDDEN", exception.getCode());
        verify(reportRepository, never()).listLatestAssessmentResults(otherTeacherScope);
    }

    @Test
    void crossSchoolAssessmentIsRejectedBeforeReportRowsAreRead() {
        when(reportRepository.findTestSchoolId(1001L)).thenReturn(Optional.of("SCHOOL-002"));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> reportService.getAssessmentResults(PRINCIPAL, 1001L, 3001L)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("REPORT_SCOPE_FORBIDDEN", exception.getCode());
        verify(reportRepository, never()).findAssessmentScope(1001L, 3001L);
    }

    @Test
    void assessmentAndClassAssignmentMismatchReturnsFieldLevelError() {
        when(reportRepository.findTestSchoolId(1001L)).thenReturn(Optional.of(SCHOOL_ID));
        when(reportRepository.findAssessmentScope(1001L, 3001L)).thenReturn(Optional.empty());

        V3FieldValidationException exception = assertThrows(
                V3FieldValidationException.class,
                () -> reportService.getAssessmentResults(PRINCIPAL, 1001L, 3001L)
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals("REPORT_FILTER_MISMATCH", exception.getErrors().get("code"));
        assertTrue(exception.getErrors().containsKey("testId"));
        assertTrue(exception.getErrors().containsKey("classAssignmentId"));
    }

    @Test
    void emptyAssessmentReportKeepsUnknownMetricsNull() {
        AssessmentScopeRow scope = scope(20L);
        when(reportRepository.findTestSchoolId(1001L)).thenReturn(Optional.of(SCHOOL_ID));
        when(reportRepository.findAssessmentScope(1001L, 3001L)).thenReturn(Optional.of(scope));
        when(reportRepository.listLatestAssessmentResults(scope)).thenReturn(List.of(
                learnerWithoutResult()
        ));

        var response = reportService.getAssessmentResults(PRINCIPAL, 1001L, 3001L);

        assertEquals("empty", response.dataStatus());
        assertEquals(0, response.summary().submittedCount());
        assertNull(response.summary().classMeanPoints());
        assertNull(response.summary().classMeanPercentage());
        assertEquals("NO_SUBMITTED_RESULTS", response.warnings().get(0).code());
    }

    @Test
    void invalidIdentifiersAndUnknownAssessmentAreRejected() {
        V3FieldValidationException invalid = assertThrows(
                V3FieldValidationException.class,
                () -> reportService.getAssessmentResults(PRINCIPAL, 0L, -1L)
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, invalid.getStatus());

        when(reportRepository.findTestSchoolId(9999L)).thenReturn(Optional.empty());
        V3AuthException missing = assertThrows(
                V3AuthException.class,
                () -> reportService.getAssessmentResults(PRINCIPAL, 9999L, 3001L)
        );
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());
        assertEquals("ASSESSMENT_NOT_FOUND", missing.getCode());
    }

    /** The seeded "intervention" rule set, verbatim from performance_rule_sets (id 4). */
    private static final String INTERVENTION_RULE_SET = """
            {"metric":"skill_mastery_percentage","teacher_facing_only":true,"bands":[
             {"minimum_percentage":80,"maximum_percentage":100,"minimum_inclusive":true,"maximum_inclusive":true,
              "status":"maintain","label":"Maintain","recommendation_template":"Maintain {competency_name} through regular practice."},
             {"minimum_percentage":60,"maximum_percentage":80,"minimum_inclusive":true,"maximum_inclusive":false,
              "status":"review","label":"Review","recommendation_template":"Review {competency_name} with short guided practice."},
             {"minimum_percentage":40,"maximum_percentage":60,"minimum_inclusive":true,"maximum_inclusive":false,
              "status":"reteach","label":"Reteach","recommendation_template":"Reteach {competency_name} using focused examples and checking."},
             {"minimum_percentage":0,"maximum_percentage":40,"minimum_inclusive":true,"maximum_inclusive":false,
              "status":"priority_intervention","label":"Priority Intervention","recommendation_template":"Prioritize intervention for {competency_name} and monitor affected learners."}]}
            """;

    @Test
    void studentProfileInterventionsFollowRuleSetBandsAndSkipMaintainedSkills() {
        long studentId = 77L;
        List<Long> classListIds = List.of(501L);
        when(reportRepository.listClassListIdsForStudent(studentId, SCHOOL_ID, null)).thenReturn(classListIds);
        when(reportRepository.findStudentIdentity(studentId, SCHOOL_ID))
                .thenReturn(Optional.of(new V3ReportRepository.StudentIdentity("123456789012", "Juan Dela Cruz")));
        // 100 possible points per skill, so earned points read directly as the mastery %.
        when(reportRepository.listStudentSkillItemTotals(classListIds, null)).thenReturn(List.of(
                new StudentSkillItemTotalRow(1L, "Verb Tenses", 5, new BigDecimal("100")),
                new StudentSkillItemTotalRow(2L, "Parts of Speech", 5, new BigDecimal("100")),
                new StudentSkillItemTotalRow(3L, "Reading", 5, new BigDecimal("100")),
                new StudentSkillItemTotalRow(4L, "Spelling", 5, new BigDecimal("100")),
                new StudentSkillItemTotalRow(5L, "Grammar", 5, new BigDecimal("100")),
                new StudentSkillItemTotalRow(6L, "Vocabulary", 5, new BigDecimal("100"))
        ));
        when(reportRepository.listStudentSkillAnswerTotals(classListIds, null)).thenReturn(List.of(
                new StudentSkillAnswerTotalRow(1L, new BigDecimal("100")),   // maintain
                new StudentSkillAnswerTotalRow(2L, new BigDecimal("80")),    // maintain: 80 is inclusive
                new StudentSkillAnswerTotalRow(3L, new BigDecimal("79.99")), // review: 80 is exclusive above
                new StudentSkillAnswerTotalRow(4L, new BigDecimal("40")),    // reteach: 40 is inclusive
                new StudentSkillAnswerTotalRow(5L, new BigDecimal("39.99")), // priority
                new StudentSkillAnswerTotalRow(6L, BigDecimal.ZERO)          // priority
        ));
        when(reportRepository.findActiveRuleDefinition(SCHOOL_ID, "intervention"))
                .thenReturn(Optional.of(INTERVENTION_RULE_SET));

        var profile = reportService.getStudentPerformanceProfile(PRINCIPAL, studentId);

        var interventions = profile.interventions();
        assertEquals(4, interventions.size(), "maintained skills must not be listed");
        assertEquals(List.of("Vocabulary", "Grammar", "Spelling", "Reading"),
                interventions.stream().map(suggestion -> suggestion.skillName()).toList());
        assertEquals(List.of("priority_intervention", "priority_intervention", "reteach", "review"),
                interventions.stream().map(suggestion -> suggestion.recommendationCode()).toList());
        assertEquals("Priority Intervention", interventions.get(0).recommendationLabel());
        assertEquals("Prioritize intervention for Vocabulary and monitor affected learners.",
                interventions.get(0).suggestion());
        assertEquals("Review Reading with short guided practice.", interventions.get(3).suggestion());

        // The mastery pill must agree with the recommendation at the 80% boundary.
        var partsOfSpeech = profile.competencyPerformance().stream()
                .filter(skill -> skill.skillId() == 2L).findFirst().orElseThrow();
        assertEquals("mastered", partsOfSpeech.masteryStatusCode());
    }

    @Test
    void studentProfileWithoutInterventionRuleSetReturnsNoSuggestionsAndWarns() {
        long studentId = 78L;
        List<Long> classListIds = List.of(502L);
        when(reportRepository.listClassListIdsForStudent(studentId, SCHOOL_ID, null)).thenReturn(classListIds);
        when(reportRepository.findStudentIdentity(studentId, SCHOOL_ID))
                .thenReturn(Optional.of(new V3ReportRepository.StudentIdentity("123456789013", "Maria Santos")));
        when(reportRepository.listStudentSkillItemTotals(classListIds, null)).thenReturn(List.of(
                new StudentSkillItemTotalRow(1L, "Verb Tenses", 5, new BigDecimal("100"))));
        when(reportRepository.listStudentSkillAnswerTotals(classListIds, null)).thenReturn(List.of(
                new StudentSkillAnswerTotalRow(1L, BigDecimal.ZERO)));
        when(reportRepository.findActiveRuleDefinition(SCHOOL_ID, "intervention")).thenReturn(Optional.empty());

        var profile = reportService.getStudentPerformanceProfile(PRINCIPAL, studentId);

        assertTrue(profile.interventions().isEmpty());
        assertTrue(profile.warnings().stream()
                .anyMatch(warning -> "INTERVENTION_RULES_UNAVAILABLE".equals(warning.code())));
    }

    @Test
    void syncActivityListsMostRecentlySyncedTeachersFirstAndCountsOnlyUnresolvedFailures() {
        Instant older = Instant.parse("2026-09-21T02:40:00Z");
        Instant newer = Instant.parse("2026-09-22T12:03:00Z");
        when(reportRepository.listTeachers(SCHOOL_ID, null)).thenReturn(List.of(
                new V3ReportReferenceDataResponse.TeacherOption(1L, "Ana Cruz", "active"),
                new V3ReportReferenceDataResponse.TeacherOption(2L, "Ben Diaz", "active"),
                new V3ReportReferenceDataResponse.TeacherOption(3L, "Carla Reyes", "active")
        ));
        when(reportRepository.listSyncedAssessments(SCHOOL_ID, null, null, null)).thenReturn(List.of(
                new V3SyncActivityReportResponse.AssessmentSync(2L, "Ben Diaz", 11L, "Quiz Y", "Grade 7 - Rizal",
                        newer, 2, "Existing page decisions cannot be changed here."),
                new V3SyncActivityReportResponse.AssessmentSync(1L, "Ana Cruz", 10L, "Quiz X", "Grade 7 - Rizal",
                        older, 0, null),
                // Every upload attempt for Quiz Z failed, so it was never successfully synced.
                new V3SyncActivityReportResponse.AssessmentSync(2L, "Ben Diaz", 12L, "Quiz Z", "Grade 7 - Rizal",
                        null, 1, "Uncertain objective bubbles require teacher verification.")
        ));

        var report = reportService.getSyncActivityReport(PRINCIPAL, null, null, null, null, null);

        assertEquals("available", report.dataStatus());
        assertEquals(List.of("Ben Diaz", "Ana Cruz", "Carla Reyes"),
                report.teachers().stream().map(teacher -> teacher.teacherName()).toList());
        var ben = report.teachers().get(0);
        assertEquals(newer, ben.lastSyncedAt());
        assertEquals(1, ben.assessmentsSynced(), "Quiz Z never synced successfully");
        assertEquals(3, ben.resultsNotUploaded());
        var carla = report.teachers().get(2);
        assertNull(carla.lastSyncedAt(), "never synced");
        assertEquals(0, carla.assessmentsSynced());
        assertEquals(3, report.assessments().size());
    }

    @Test
    void syncActivityIgnoresATeachersRequestedTeacherFilterAndScopesToThemselves() {
        reportService.getSyncActivityReport(TEACHER, null, null, 999L, null, null);

        verify(reportRepository).listTeachers(SCHOOL_ID, 20L);
        verify(reportRepository).listSyncedAssessments(SCHOOL_ID, 20L, null, null);
    }

    private static final CompetencyScopeRow COMPETENCY_SCOPE = new CompetencyScopeRow(
            SCHOOL_ID, "SMART School", 1, "2026-2027", 11, "First Quarter", "Grade 7", "English");

    /** 10 possible points, so earned points x 10 reads directly as the mastery %. */
    private static StudentCompetencyTotalRow competencyTotal(
            long rootTagId, String rootName, long skillId, String skillName, long studentId, String studentName,
            String earnedOutOfTen
    ) {
        return new StudentCompetencyTotalRow(rootTagId, rootName, skillId, skillId + 100, skillName,
                studentId, studentName, "Rizal", 2, new BigDecimal("10"), new BigDecimal(earnedOutOfTen));
    }

    private void givenCompetencyData(Long teacherUserId, List<StudentCompetencyTotalRow> totals) {
        when(reportRepository.findCompetencyScope(SCHOOL_ID, 11, 7, 3)).thenReturn(Optional.of(COMPETENCY_SCOPE));
        when(reportRepository.listCompetencyAssessments(SCHOOL_ID, 11, 7, 3, teacherUserId)).thenReturn(List.of(
                new V3LearningCompetencyReportResponse.IncludedAssessment(1001L, "Quiz 1"),
                new V3LearningCompetencyReportResponse.IncludedAssessment(1002L, "Quarterly Exam")));
        when(reportRepository.listStudentCompetencyTotals(SCHOOL_ID, 11, 7, 3, teacherUserId)).thenReturn(totals);
        when(reportRepository.findActiveRuleDefinition(SCHOOL_ID, "intervention"))
                .thenReturn(Optional.of(INTERVENTION_RULE_SET));
    }

    @Test
    void learningCompetencyRanksLeastMasteredFirstAndListsOnlyStudentsBelowMastery() {
        givenCompetencyData(null, List.of(
                competencyTotal(1L, "Grammar", 10L, "Verb Tenses", 501L, "Ana Cruz", "9"),
                competencyTotal(1L, "Grammar", 10L, "Verb Tenses", 502L, "Ben Diaz", "8"),
                competencyTotal(1L, "Grammar", 11L, "Parts of Speech", 501L, "Ana Cruz", "5"),
                competencyTotal(1L, "Grammar", 11L, "Parts of Speech", 502L, "Ben Diaz", "2"),
                competencyTotal(2L, "Reading", 20L, "Main Idea", 501L, "Ana Cruz", "7"),
                competencyTotal(2L, "Reading", 20L, "Main Idea", 502L, "Ben Diaz", "3")
        ));

        var report = reportService.getLearningCompetencyReport(PRINCIPAL, 11, 7, 3, null, null);

        assertEquals("available", report.dataStatus());
        assertEquals(2, report.assessments().size());
        assertEquals("Grade 7", report.scope().gradeLevelName());
        // Reading 10/20 = 50% is weaker than Grammar 24/40 = 60%.
        assertEquals(List.of("Reading", "Grammar"),
                report.rootCompetencies().stream().map(root -> root.rootTagName()).toList());
        assertEquals(new BigDecimal("60.00"), report.rootCompetencies().get(1).masteryPercentage());

        var grammarSkills = report.rootCompetencies().get(1).skills();
        assertEquals(List.of("Parts of Speech", "Verb Tenses"),
                grammarSkills.stream().map(skill -> skill.competencyName()).toList());
        var partsOfSpeech = grammarSkills.get(0);
        assertEquals(new BigDecimal("35.00"), partsOfSpeech.masteryPercentage());
        // Class-level intervention for the skill, from the same rule set as the students'.
        assertEquals("priority_intervention", partsOfSpeech.recommendationCode());
        assertEquals("Priority Intervention", partsOfSpeech.recommendationLabel());
        assertEquals("Prioritize intervention for Parts of Speech and monitor affected learners.",
                partsOfSpeech.suggestion());
        assertEquals(List.of("Ben Diaz", "Ana Cruz"),
                partsOfSpeech.weakStudents().stream().map(student -> student.fullName()).toList());
        assertEquals("priority_intervention", partsOfSpeech.weakStudents().get(0).recommendationCode());
        assertEquals("reteach", partsOfSpeech.weakStudents().get(1).recommendationCode());

        // 90% and exactly 80% are both mastered, so nobody is listed as weak.
        var verbTenses = grammarSkills.get(1);
        assertEquals("maintain", verbTenses.recommendationCode());
        assertEquals(2, verbTenses.studentCount());
        assertEquals(0, verbTenses.weakStudentCount());
        assertTrue(verbTenses.weakStudents().isEmpty());
    }

    @Test
    void suggestionDropsTheTrailingPeriodOfACompetencyWrittenAsASentence() {
        givenCompetencyData(null, List.of(competencyTotal(1L, "English Language Competencies", 10L,
                "Use correct subject-verb agreement in sentences.", 501L, "Ana Cruz", "2")));

        var skill = reportService.getLearningCompetencyReport(PRINCIPAL, 11, 7, 3, null, null)
                .rootCompetencies().get(0).skills().get(0);

        assertEquals("Prioritize intervention for Use correct subject-verb agreement in sentences"
                + " and monitor affected learners.", skill.suggestion());
    }

    @Test
    void learningCompetencyForATeacherOnlyReadsTheirOwnClasses() {
        givenCompetencyData(20L, List.of(
                competencyTotal(1L, "Grammar", 10L, "Verb Tenses", 501L, "Ana Cruz", "4")));

        var report = reportService.getLearningCompetencyReport(TEACHER, 11, 7, 3, null, null);

        verify(reportRepository).listStudentCompetencyTotals(SCHOOL_ID, 11, 7, 3, 20L);
        assertEquals(1, report.rootCompetencies().get(0).skills().get(0).weakStudentCount());
    }

    @Test
    void learningCompetencySkillFilterKeepsOnlyThatSkillUnderItsRoot() {
        givenCompetencyData(null, List.of(
                competencyTotal(1L, "Grammar", 10L, "Verb Tenses", 501L, "Ana Cruz", "4"),
                competencyTotal(1L, "Grammar", 11L, "Parts of Speech", 501L, "Ana Cruz", "5"),
                competencyTotal(2L, "Reading", 20L, "Main Idea", 501L, "Ana Cruz", "3")
        ));

        var report = reportService.getLearningCompetencyReport(PRINCIPAL, 11, 7, 3, null, 11L);

        assertEquals(1, report.rootCompetencies().size());
        assertEquals("Grammar", report.rootCompetencies().get(0).rootTagName());
        assertEquals(List.of(11L),
                report.rootCompetencies().get(0).skills().stream().map(skill -> skill.skillId()).toList());
    }

    @Test
    void learningCompetencyWithoutFinalizedResultsIsEmptyAndWarns() {
        when(reportRepository.findCompetencyScope(SCHOOL_ID, 11, 7, 3)).thenReturn(Optional.of(COMPETENCY_SCOPE));
        when(reportRepository.findActiveRuleDefinition(SCHOOL_ID, "intervention"))
                .thenReturn(Optional.of(INTERVENTION_RULE_SET));

        var report = reportService.getLearningCompetencyReport(PRINCIPAL, 11, 7, 3, null, null);

        assertEquals("empty", report.dataStatus());
        assertTrue(report.rootCompetencies().isEmpty());
        assertTrue(report.warnings().stream().anyMatch(warning -> "NO_SUBMITTED_RESULTS".equals(warning.code())));
    }

    @Test
    void learningCompetencyRejectsATermOrGradeLevelThatDoesNotExist() {
        when(reportRepository.findCompetencyScope(SCHOOL_ID, 99, 7, 3)).thenReturn(Optional.empty());
        when(reportRepository.findCompetencyScope(SCHOOL_ID, 11, 99, 3)).thenReturn(Optional.of(
                new CompetencyScopeRow(SCHOOL_ID, "SMART School", 1, "2026-2027", 11, "First Quarter",
                        null, "English")));

        var unknownTerm = assertThrows(V3FieldValidationException.class,
                () -> reportService.getLearningCompetencyReport(PRINCIPAL, 99, 7, 3, null, null));
        var unknownGrade = assertThrows(V3FieldValidationException.class,
                () -> reportService.getLearningCompetencyReport(PRINCIPAL, 11, 99, 3, null, null));

        assertEquals(HttpStatus.NOT_FOUND, unknownTerm.getStatus());
        assertEquals(HttpStatus.NOT_FOUND, unknownGrade.getStatus());
        verify(reportRepository, never()).listStudentCompetencyTotals(SCHOOL_ID, 11, 99, 3, null);
    }

    private static AssessmentScopeRow scope(long teacherUserId) {
        return new AssessmentScopeRow(
                SCHOOL_ID,
                "SMART School",
                1,
                "2026-2027",
                11,
                "First Quarter",
                2001L,
                7,
                "Grade 7",
                71,
                "Rizal",
                3001L,
                teacherUserId,
                "Teacher One",
                5,
                "English",
                1001L,
                4001L,
                "English Quiz 1",
                "quiz",
                "active",
                "active",
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                new BigDecimal("10")
        );
    }

    private static AssessmentResultRow finalizedResult() {
        return finalizedResult(NOW.minusSeconds(600));
    }

    private static AssessmentResultRow finalizedResult(Instant submittedAt) {
        return new AssessmentResultRow(
                5001L,
                6001L,
                "100000000001",
                "Dela Cruz, Juan",
                "enrolled",
                7001L,
                "aaaaaaaa-0000-4000-8000-000000007001",
                "finalized",
                submittedAt,
                NOW.minusSeconds(300),
                new BigDecimal("8.00"),
                new BigDecimal("10.00"),
                new BigDecimal("80.00"),
                "maintain",
                8001L,
                "Default performance rules",
                "1.0",
                "{\"bands\":[{\"status\":\"maintain\",\"label\":\"Maintain\"}]}",
                0
        );
    }

    private static AssessmentResultRow pendingResult() {
        return pendingResult(NOW.minusSeconds(500));
    }

    private static AssessmentResultRow pendingResult(Instant submittedAt) {
        return new AssessmentResultRow(
                5002L,
                6002L,
                "100000000002",
                "Reyes, Ana",
                "enrolled",
                7002L,
                "aaaaaaaa-0000-4000-8000-000000007002",
                "pending_verification",
                submittedAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                2
        );
    }

    private static AssessmentResultRow learnerWithoutResult() {
        return new AssessmentResultRow(
                5003L,
                6003L,
                "100000000003",
                "Santos, Maria",
                "enrolled",
                null,
                null,
                "not_submitted",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0
        );
    }

    private static V3AuthenticatedUser user(long userId, String role, String schoolId) {
        return new V3AuthenticatedUser(
                userId,
                schoolId,
                role + "@example.com",
                role,
                "active",
                "session"
        );
    }
}
