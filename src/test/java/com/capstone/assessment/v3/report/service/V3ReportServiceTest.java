package com.capstone.assessment.v3.report.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.report.dto.V3ReportReferenceDataResponse;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentResultRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentScopeRow;
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
