package com.capstone.assessment.v3.scoring.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.PerformanceRuleSet;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ResultContext;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.RubricScoreSummary;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ScoringRow;
import com.capstone.assessment.v3.scoring.repository.V3ScoringRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V3ScoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
    private static final V3AuthenticatedUser TEACHER = new V3AuthenticatedUser(
            42L, "SCHOOL-001", "teacher@example.com", "teacher", "active", "session"
    );
    private static final V3RequestMetadata METADATA = new V3RequestMetadata(
            "127.0.0.1", "JUnit", "scoring-test"
    );

    private final V3ScoringRepository repository = mock(V3ScoringRepository.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private V3ScoringService service;

    @BeforeEach
    void setUp() {
        service = new V3ScoringService(
                repository,
                auditService,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(com.capstone.assessment.v3.mobile.service.V3MobileFinalizationService.class)
        );
    }

    @Test
    void computesVariablePointsAcrossMultiplePartsFromVerifiedAnswers() {
        ResultContext context = pendingContext(42L);
        List<ScoringRow> rows = List.of(
                objectiveRow(11L, 1, "Part I", 101L, 1, "multiple_choice", "2.00", 1001L, 1001L),
                objectiveRow(11L, 1, "Part I", 102L, 2, "true_false", "3.00", 1002L, 1003L),
                writtenRow(12L, 2, "Part II", 103L, 1, "identification", "2.00", "1.50")
        );
        stubReadyResult(context, rows);
        when(repository.updateResultScore(
                eq(100L), eq(1), eq(new BigDecimal("3.50")), eq(new BigDecimal("7.00")),
                eq(3), eq(new BigDecimal("50.00")), eq("reteach"), eq(501L), eq(1), eq(NOW)
        )).thenReturn(1);

        V3ScoredResultResponse response = service.finalizeResult(TEACHER, 100L, METADATA);

        assertEquals(new BigDecimal("3.50"), response.totalScore());
        assertEquals(new BigDecimal("7.00"), response.maxScore());
        assertEquals(new BigDecimal("50.00"), response.percentage());
        assertEquals("reteach", response.performanceStatus());
        assertEquals("Reteach", response.performanceLabel());
        assertEquals("finalized", response.resultStatus());
        assertEquals(3, response.itemsEvaluated());
        assertEquals(1, response.scoreVersion());
        assertEquals(NOW, response.scoredAt());
        assertTrue(response.scoreChanged());
        assertEquals(2, response.parts().size());
        assertEquals(new BigDecimal("2.00"), response.parts().get(0).totalScore());
        assertEquals(new BigDecimal("5.00"), response.parts().get(0).maxScore());
        assertEquals(new BigDecimal("40.00"), response.parts().get(0).percentage());
        assertEquals(new BigDecimal("1.50"), response.parts().get(1).totalScore());
        assertEquals(new BigDecimal("2.00"), response.parts().get(1).maxScore());
        assertEquals(new BigDecimal("75.00"), response.parts().get(1).percentage());
        verify(repository, times(2)).updateObjectiveScore(anyLong(), anyBoolean(), any(BigDecimal.class));
        verify(auditService).record(
                eq(42L), eq("result.score.finalize"), eq("test_results"), eq("100"),
                eq("success"), eq(METADATA), any(), eq(NOW)
        );
    }

    @Test
    void uncertainObjectiveAnswerRequiresRescanAndCannotBeScored() {
        ScoringRow uncertain = objectiveRow(
                11L, 1, "Part I", 101L, 1, "multiple_choice", "2.00", 1001L, 1001L,
                "uncertain", null
        );
        stubReadyResult(pendingContext(42L), List.of(uncertain));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.finalizeResult(TEACHER, 100L, METADATA)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("OBJECTIVE_RESCAN_REQUIRED", exception.getCode());
        verify(repository, never()).updateObjectiveScore(anyLong(), anyBoolean(), any(BigDecimal.class));
        verify(repository, never()).updateResultScore(
                anyLong(), anyInt(), any(), any(), anyInt(), any(), anyString(),
                anyLong(), anyInt(), any()
        );
        verifyNoInteractions(auditService);
    }

    @Test
    void manualScoreCannotExceedQuestionMaximumPoints() {
        ScoringRow invalid = writtenRow(
                12L, 2, "Part II", 103L, 1, "identification", "2.00", "2.50"
        );
        stubReadyResult(pendingContext(42L), List.of(invalid));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.finalizeResult(TEACHER, 100L, METADATA)
        );

        assertEquals("MANUAL_SCORE_OUT_OF_RANGE", exception.getCode());
        verify(repository, never()).updateResultScore(
                anyLong(), anyInt(), any(), any(), anyInt(), any(), anyString(),
                anyLong(), anyInt(), any()
        );
        verifyNoInteractions(auditService);
    }

    @Test
    void retainedWrittenEvidenceAllowsManualScoringWithoutOcrText() {
        ScoringRow attachmentOnly = new ScoringRow(
                12L, 2, "Part II", 103L, 1, "identification", new BigDecimal("2.00"), null,
                3103L, "manual", null, null,
                2003L, "answer-2003", null, null, null, 1L,
                "answered", "finalized", 42L, NOW, null, new BigDecimal("1.00"), NOW, 1
        );
        stubReadyResult(pendingContext(42L), List.of(attachmentOnly));
        when(repository.updateResultScore(
                eq(100L), eq(1), eq(new BigDecimal("1.00")), eq(new BigDecimal("2.00")),
                eq(1), eq(new BigDecimal("50.00")), eq("reteach"), eq(501L), eq(1), eq(NOW)
        )).thenReturn(1);

        V3ScoredResultResponse response = service.finalizeResult(TEACHER, 100L, METADATA);

        assertEquals(new BigDecimal("1.00"), response.totalScore());
        assertEquals(new BigDecimal("2.00"), response.maxScore());
        assertTrue(response.scoreChanged());
    }

    @Test
    void writtenAnswerWithoutTextOrEvidenceIsRejected() {
        ScoringRow emptyWritten = new ScoringRow(
                12L, 2, "Part II", 103L, 1, "identification", new BigDecimal("2.00"), null,
                3103L, "manual", null, null,
                2003L, "answer-2003", null, null, null, 0L,
                "answered", "finalized", 42L, NOW, null, BigDecimal.ZERO, NOW, 1
        );
        stubReadyResult(pendingContext(42L), List.of(emptyWritten));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.finalizeResult(TEACHER, 100L, METADATA)
        );

        assertEquals("WRITTEN_RESPONSE_MISSING", exception.getCode());
        verify(repository, never()).updateResultScore(
                anyLong(), anyInt(), any(), any(), anyInt(), any(), anyString(),
                anyLong(), anyInt(), any()
        );
    }

    @Test
    void essayRequiresCompleteCurrentVersionRubricScores() {
        ScoringRow essay = new ScoringRow(
                13L, 3, "Part III", 104L, 1, "essay", new BigDecimal("5.00"), 901L,
                3004L, "rubric", null, 901L,
                2004L, "answer-2004", null, null, "Written essay", 1L, "answered", "finalized",
                42L, NOW, null, new BigDecimal("4.00"), NOW, 2
        );
        stubReadyResult(pendingContext(42L), List.of(essay));
        when(repository.summarizeRubricScores(2004L, 2, 901L)).thenReturn(
                new RubricScoreSummary(2, 2, 1, 1, 0, new BigDecimal("2.00"))
        );

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.finalizeResult(TEACHER, 100L, METADATA)
        );

        assertEquals("RUBRIC_SCORE_INCOMPLETE", exception.getCode());
        verify(repository, never()).updateResultScore(
                anyLong(), anyInt(), any(), any(), anyInt(), any(), anyString(),
                anyLong(), anyInt(), any()
        );
        verifyNoInteractions(auditService);
    }

    @Test
    void unchangedFinalizedResultIsIdempotent() {
        Instant originalScoredAt = Instant.parse("2026-09-04T10:00:00Z");
        ResultContext context = finalizedContext(originalScoredAt);
        ScoringRow row = objectiveRow(
                11L, 1, "Part I", 101L, 1, "multiple_choice", "2.00", 1001L, 1001L
        );
        stubReadyResult(context, List.of(row));

        V3ScoredResultResponse response = service.finalizeResult(TEACHER, 100L, METADATA);

        assertFalse(response.scoreChanged());
        assertEquals(3, response.scoreVersion());
        assertEquals(originalScoredAt, response.scoredAt());
        assertEquals(new BigDecimal("2.00"), response.totalScore());
        assertEquals(new BigDecimal("100.00"), response.percentage());
        verify(repository).updateObjectiveScore(2001L, true, new BigDecimal("2.00"));
        verify(repository, never()).updateResultScore(
                anyLong(), anyInt(), any(), any(), anyInt(), any(), anyString(),
                anyLong(), anyInt(), any()
        );
        verifyNoInteractions(auditService);
    }

    @Test
    void anotherTeacherCannotFinalizeTheResult() {
        when(repository.findResultContextForUpdate(100L)).thenReturn(Optional.of(pendingContext(99L)));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.finalizeResult(TEACHER, 100L, METADATA)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("RESULT_ACCESS_DENIED", exception.getCode());
        verify(repository, never()).findScoringRowsForUpdate(anyLong(), anyLong());
        verifyNoInteractions(auditService);
    }

    @Test
    void missingVerifiedAnswerBlocksWholeResultFinalization() {
        ScoringRow missing = new ScoringRow(
                11L, 1, "Part I", 101L, 1, "multiple_choice", new BigDecimal("1.00"), null,
                3001L, "option", 1001L, null,
                null, null, null, null, null, 0L, null, null, null,
                null, null, null, null, 0
        );
        stubReadyResult(pendingContext(42L), List.of(missing));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.finalizeResult(TEACHER, 100L, METADATA)
        );

        assertEquals("ANSWER_MISSING", exception.getCode());
        verify(repository, never()).updateResultScore(
                anyLong(), anyInt(), any(), any(), anyInt(), any(), anyString(),
                anyLong(), anyInt(), any()
        );
    }

    private void stubReadyResult(ResultContext context, List<ScoringRow> rows) {
        when(repository.findResultContextForUpdate(100L)).thenReturn(Optional.of(context));
        when(repository.findScoringRowsForUpdate(300L, 100L)).thenReturn(rows);
        when(repository.findActiveStudentScoreRuleSet("SCHOOL-001", NOW))
                .thenReturn(Optional.of(studentScoreRule()));
    }

    private ResultContext pendingContext(long teacherUserId) {
        return new ResultContext(
                100L, "result-uuid", 200L, 300L, 400L, 500L, "Learner One", 1,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, "pending_verification", null, null, null,
                1, null, teacherUserId, "SCHOOL-001", "SCHOOL-001", 600L, 600L,
                "active", "closed"
        );
    }

    private ResultContext finalizedContext(Instant scoredAt) {
        return new ResultContext(
                100L, "result-uuid", 200L, 300L, 400L, 500L, "Learner One", 1,
                new BigDecimal("2.00"), new BigDecimal("2.00"), 1, "finalized",
                new BigDecimal("100.00"), "maintain", 501L, 3, scoredAt,
                42L, "SCHOOL-001", "SCHOOL-001", 600L, 600L, "active", "closed"
        );
    }

    private ScoringRow objectiveRow(
            long partId,
            int partOrder,
            String partName,
            long questionId,
            int itemNumber,
            String questionType,
            String maximumPoints,
            long selectedOptionId,
            long correctOptionId
    ) {
        return objectiveRow(
                partId, partOrder, partName, questionId, itemNumber, questionType,
                maximumPoints, selectedOptionId, correctOptionId, "answered", questionId
        );
    }

    private ScoringRow objectiveRow(
            long partId,
            int partOrder,
            String partName,
            long questionId,
            int itemNumber,
            String questionType,
            String maximumPoints,
            long selectedOptionId,
            long correctOptionId,
            String answerStatus,
            Long selectedOptionQuestionId
    ) {
        return new ScoringRow(
                partId, partOrder, partName, questionId, itemNumber, questionType,
                new BigDecimal(maximumPoints), null,
                3000L + questionId, "option", correctOptionId, null,
                1900L + questionId, "answer-" + questionId, selectedOptionId,
                selectedOptionQuestionId, null, 0L, answerStatus, "finalized", 42L, NOW,
                null, BigDecimal.ZERO, NOW, 1
        );
    }

    private ScoringRow writtenRow(
            long partId,
            int partOrder,
            String partName,
            long questionId,
            int itemNumber,
            String questionType,
            String maximumPoints,
            String pointsEarned
    ) {
        return new ScoringRow(
                partId, partOrder, partName, questionId, itemNumber, questionType,
                new BigDecimal(maximumPoints), null,
                3000L + questionId, "accepted_text", null, null,
                1900L + questionId, "answer-" + questionId, null, null,
                "Written response", 0L, "answered", "finalized", 42L, NOW,
                null, new BigDecimal(pointsEarned), NOW, 1
        );
    }

    private PerformanceRuleSet studentScoreRule() {
        return new PerformanceRuleSet(
                501L,
                """
                {
                  "metric": "percentage",
                  "rounding_scale": 2,
                  "bands": [
                    {"minimum_percentage": 80, "maximum_percentage": 100,
                     "minimum_inclusive": true, "maximum_inclusive": true,
                     "status": "maintain", "label": "Maintain"},
                    {"minimum_percentage": 60, "maximum_percentage": 80,
                     "minimum_inclusive": true, "maximum_inclusive": false,
                     "status": "review", "label": "Review"},
                    {"minimum_percentage": 40, "maximum_percentage": 60,
                     "minimum_inclusive": true, "maximum_inclusive": false,
                     "status": "reteach", "label": "Reteach"},
                    {"minimum_percentage": 0, "maximum_percentage": 40,
                     "minimum_inclusive": true, "maximum_inclusive": false,
                     "status": "priority_intervention", "label": "Priority Intervention"}
                  ]
                }
                """
        );
    }
}
