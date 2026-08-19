package com.capstone.assessment.v2.assessment.service;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentPartRequest;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentQuestionRequest;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentRequest;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.assessment.dto.V2SkillRangeMappingRequest;
import com.capstone.assessment.v2.assessment.model.V2AssessmentAssignmentContext;
import com.capstone.assessment.v2.assessment.model.V2AssessmentHeader;
import com.capstone.assessment.v2.assessment.model.V2AssessmentPartRow;
import com.capstone.assessment.v2.assessment.model.V2AssessmentQuestionRow;
import com.capstone.assessment.v2.assessment.repository.V2AssessmentRepository;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2AssessmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T02:00:00Z");
    private static final V2RequestMetadata METADATA = new V2RequestMetadata(
            "203.0.113.40",
            "JUnit",
            "teacher-device"
    );
    private static final V2AuthenticatedUser TEACHER = new V2AuthenticatedUser(
            20L,
            "SCHOOL-001",
            "teacher@example.com",
            "teacher",
            "active",
            "teacher-session"
    );

    @Mock
    private V2AssessmentRepository assessmentRepository;

    @Mock
    private V2AuthRepository authRepository;

    private V2AssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        assessmentService = new V2AssessmentService(
                assessmentRepository,
                authRepository,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void teacherCreatesValidDraftAssessmentWithMultiplePartsAndRangeMappings() {
        V2AssessmentRequest request = validRequest();
        allowAssignment(request);
        when(assessmentRepository.findValidSkillIds(Set.of(100L, 101L), 11, 1, 3))
                .thenReturn(Set.of(100L, 101L));
        when(assessmentRepository.insertTest(
                500L,
                11,
                "Quiz 1",
                "quiz",
                LocalDate.of(2026, 8, 20),
                "Shade only one answer.",
                3
        )).thenReturn(900L);
        when(assessmentRepository.insertTestPart(900L, 1, "Part I", "multiple_choice", 2, BigDecimal.ONE))
                .thenReturn(1001L);
        when(assessmentRepository.insertTestPart(900L, 2, "Part II", "true_false", 1, BigDecimal.ONE))
                .thenReturn(1002L);
        when(assessmentRepository.insertQuestion(
                1001L, 1, "What is 1 + 1?", "1", "2", "3", "4", null
        )).thenReturn(2001L);
        when(assessmentRepository.insertQuestion(
                1001L, 2, "What is 2 + 2?", "2", "3", "4", "5", null
        )).thenReturn(2002L);
        when(assessmentRepository.insertQuestion(
                1002L, 1, "Java is strongly typed.", "True", "False", "", "", null
        )).thenReturn(2003L);
        stubAssessmentDetail("draft");

        V2AssessmentResponse response = assessmentService.createAssessment(TEACHER, request, METADATA);

        assertEquals(900L, response.testId());
        assertEquals("draft", response.status());
        assertEquals(3, response.totalItems());
        assertEquals(2, response.parts().size());
        verify(assessmentRepository).insertAnswerKey(2003L, "A");
        verify(assessmentRepository).insertMapping(2001L, 100L);
        verify(assessmentRepository).insertMapping(2002L, 100L);
        verify(assessmentRepository).insertMapping(2003L, 101L);
        verify(authRepository).recordAudit(
                any(String.class),
                eq(20L),
                eq("CREATE_ASSESSMENT"),
                eq("tests"),
                eq("900"),
                eq("success"),
                eq("203.0.113.40"),
                eq("teacher-device"),
                eq("JUnit"),
                argThat(details -> details != null
                        && details.contains("\"classAssignmentId\":500")
                        && details.contains("\"totalItems\":3")),
                eq(NOW)
        );
    }

    @Test
    void teacherCannotCreateAssessmentForAnotherTeachersAssignment() {
        when(assessmentRepository.findAssignmentContext(500L))
                .thenReturn(Optional.of(assignment(99L, "SCHOOL-001", "active", "active")));

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.createAssessment(TEACHER, validRequest(), METADATA)
        );

        assertEquals("CLASS_ASSIGNMENT_FORBIDDEN", exception.getCode());
        verify(assessmentRepository, never()).insertTest(
                anyLong(), anyInt(), any(), any(), any(), any(), anyInt()
        );
    }

    @Test
    void principalCannotCreateAssessment() {
        V2AuthenticatedUser principal = new V2AuthenticatedUser(
                10L,
                "SCHOOL-001",
                "principal@example.com",
                "principal",
                "active",
                "principal-session"
        );

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.createAssessment(principal, validRequest(), METADATA)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        verify(assessmentRepository, never()).findAssignmentContext(anyLong());
    }

    @Test
    void unauthenticatedUserCannotCreateAssessment() {
        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.createAssessment(null, validRequest(), METADATA)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        verify(assessmentRepository, never()).findAssignmentContext(anyLong());
    }

    @Test
    void duplicatePartOrderIsRejected() {
        V2AssessmentRequest request = new V2AssessmentRequest(
                500L,
                11,
                "Quiz 1",
                "quiz",
                LocalDate.of(2026, 8, 20),
                null,
                List.of(
                        multipleChoicePart(1),
                        new V2AssessmentPartRequest(
                                1,
                                "Duplicate Part",
                                "multiple_choice",
                                BigDecimal.ONE,
                                List.of(question(1, "A", List.of(100L))),
                                null
                        )
                )
        );
        allowAssignment(request);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.createAssessment(TEACHER, request, METADATA)
        );

        assertEquals("DUPLICATE_PART_ORDER", exception.getCode());
        verify(assessmentRepository, never()).insertTest(
                anyLong(), anyInt(), any(), any(), any(), any(), anyInt()
        );
    }

    @Test
    void duplicateItemNumberWithinPartIsRejected() {
        V2AssessmentRequest request = new V2AssessmentRequest(
                500L,
                11,
                "Quiz 1",
                "quiz",
                LocalDate.of(2026, 8, 20),
                null,
                List.of(new V2AssessmentPartRequest(
                        1,
                        "Part I",
                        "multiple_choice",
                        BigDecimal.ONE,
                        List.of(
                                question(1, "A", List.of(100L)),
                                question(1, "B", List.of(100L))
                        ),
                        null
                ))
        );
        allowAssignment(request);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.createAssessment(TEACHER, request, METADATA)
        );

        assertEquals("DUPLICATE_ITEM_NUMBER", exception.getCode());
        verify(assessmentRepository, never()).insertTest(
                anyLong(), anyInt(), any(), any(), any(), any(), anyInt()
        );
    }

    @Test
    void termPeriodFromAnotherAcademicYearIsRejected() {
        V2AssessmentRequest request = validRequest();
        when(assessmentRepository.findAssignmentContext(500L))
                .thenReturn(Optional.of(assignment(20L, "SCHOOL-001", "active", "active")));
        when(assessmentRepository.termPeriodBelongsToAcademicYear(11, 1)).thenReturn(false);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.createAssessment(TEACHER, request, METADATA)
        );

        assertEquals("TERM_PERIOD_YEAR_MISMATCH", exception.getCode());
        verify(assessmentRepository, never()).insertTest(
                anyLong(), anyInt(), any(), any(), any(), any(), anyInt()
        );
    }

    @Test
    void trueFalseRejectsAnswersOutsideAOrB() {
        V2AssessmentRequest request = new V2AssessmentRequest(
                500L,
                11,
                "Quiz 1",
                "quiz",
                LocalDate.of(2026, 8, 20),
                null,
                List.of(new V2AssessmentPartRequest(
                        1,
                        "Part I",
                        "true_false",
                        BigDecimal.ONE,
                        List.of(new V2AssessmentQuestionRequest(
                                1,
                                "Java runs on the JVM.",
                                null,
                                null,
                                null,
                                null,
                                null,
                                "C",
                                List.of(100L)
                        )),
                        null
                ))
        );
        allowAssignment(request);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.createAssessment(TEACHER, request, METADATA)
        );

        assertEquals("INVALID_TRUE_FALSE_ANSWER", exception.getCode());
        verify(assessmentRepository, never()).insertTest(
                anyLong(), anyInt(), any(), any(), any(), any(), anyInt()
        );
    }

    @Test
    void missingAnswerKeysBlockActivation() {
        stubDraftHeaderForActivation();
        when(assessmentRepository.countParts(900L)).thenReturn(1);
        when(assessmentRepository.countQuestions(900L)).thenReturn(2);
        when(assessmentRepository.countQuestionsMissingAnswerKey(900L)).thenReturn(1);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.activateAssessment(TEACHER, 900L, METADATA)
        );

        assertEquals("MISSING_ANSWER_KEYS", exception.getCode());
        verify(assessmentRepository, never()).updateStatus(900L, "active");
    }

    @Test
    void missingSkillMappingsBlockActivation() {
        stubDraftHeaderForActivation();
        when(assessmentRepository.countParts(900L)).thenReturn(1);
        when(assessmentRepository.countQuestions(900L)).thenReturn(2);
        when(assessmentRepository.countQuestionsMissingAnswerKey(900L)).thenReturn(0);
        when(assessmentRepository.countQuestionsMissingMappings(900L)).thenReturn(1);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.activateAssessment(TEACHER, 900L, METADATA)
        );

        assertEquals("MISSING_SKILL_MAPPINGS", exception.getCode());
        verify(assessmentRepository, never()).updateStatus(900L, "active");
    }

    @Test
    void validDraftCanBeActivated() {
        when(assessmentRepository.findHeader(900L))
                .thenReturn(Optional.of(header("draft")))
                .thenReturn(Optional.of(header("draft")))
                .thenReturn(Optional.of(header("active")));
        when(assessmentRepository.countParts(900L)).thenReturn(1);
        when(assessmentRepository.countQuestions(900L)).thenReturn(2);
        when(assessmentRepository.countQuestionsMissingAnswerKey(900L)).thenReturn(0);
        when(assessmentRepository.countQuestionsMissingMappings(900L)).thenReturn(0);
        when(assessmentRepository.countInvalidTrueFalseAnswerKeys(900L)).thenReturn(0);
        when(assessmentRepository.countSnapshotMismatches(900L)).thenReturn(0);
        when(assessmentRepository.updateStatus(900L, "active")).thenReturn(1);
        when(assessmentRepository.findParts(900L)).thenReturn(List.of());
        when(assessmentRepository.findQuestions(900L)).thenReturn(List.of());

        V2AssessmentResponse response = assessmentService.activateAssessment(TEACHER, 900L, METADATA);

        assertEquals("active", response.status());
        verify(assessmentRepository).lockTest(900L);
        verify(authRepository).recordAudit(
                any(String.class),
                eq(20L),
                eq("ACTIVATE_ASSESSMENT"),
                eq("tests"),
                eq("900"),
                eq("success"),
                eq("203.0.113.40"),
                eq("teacher-device"),
                eq("JUnit"),
                argThat(details -> details != null && details.contains("\"newStatus\":\"active\"")),
                eq(NOW)
        );
    }

    @Test
    void repositoryFailureDuringSaveDoesNotRecordSuccessAudit() {
        V2AssessmentRequest request = validRequest();
        allowAssignment(request);
        when(assessmentRepository.findValidSkillIds(Set.of(100L, 101L), 11, 1, 3))
                .thenReturn(Set.of(100L, 101L));
        when(assessmentRepository.insertTest(
                500L,
                11,
                "Quiz 1",
                "quiz",
                LocalDate.of(2026, 8, 20),
                "Shade only one answer.",
                3
        )).thenReturn(900L);
        when(assessmentRepository.insertTestPart(900L, 1, "Part I", "multiple_choice", 2, BigDecimal.ONE))
                .thenThrow(new DataIntegrityViolationException("duplicate part"));

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> assessmentService.createAssessment(TEACHER, request, METADATA)
        );

        assertEquals("ASSESSMENT_SAVE_CONFLICT", exception.getCode());
        verify(authRepository, never()).recordAudit(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    private void allowAssignment(V2AssessmentRequest request) {
        when(assessmentRepository.findAssignmentContext(request.classAssignmentId()))
                .thenReturn(Optional.of(assignment(20L, "SCHOOL-001", "active", "active")));
        when(assessmentRepository.termPeriodBelongsToAcademicYear(request.termPeriodId(), 1))
                .thenReturn(true);
    }

    private void stubDraftHeaderForActivation() {
        when(assessmentRepository.findHeader(900L))
                .thenReturn(Optional.of(header("draft")))
                .thenReturn(Optional.of(header("draft")));
    }

    private void stubAssessmentDetail(String status) {
        when(assessmentRepository.findHeader(900L)).thenReturn(Optional.of(header(status)));
        when(assessmentRepository.findParts(900L)).thenReturn(List.of(
                new V2AssessmentPartRow(1001L, 1, "Part I", "multiple_choice", 2, BigDecimal.ONE),
                new V2AssessmentPartRow(1002L, 2, "Part II", "true_false", 1, BigDecimal.ONE)
        ));
        when(assessmentRepository.findQuestions(900L)).thenReturn(List.of(
                new V2AssessmentQuestionRow(
                        2001L, 1001L, 1, "What is 1 + 1?", "1", "2", "3", "4", null, "B", List.of(100L)
                ),
                new V2AssessmentQuestionRow(
                        2002L, 1001L, 2, "What is 2 + 2?", "2", "3", "4", "5", null, "C", List.of(100L)
                ),
                new V2AssessmentQuestionRow(
                        2003L, 1002L, 1, "Java is strongly typed.", "True", "False", "", "", null, "A", List.of(101L)
                )
        ));
    }

    private V2AssessmentRequest validRequest() {
        return new V2AssessmentRequest(
                500L,
                11,
                " Quiz 1 ",
                "quiz",
                LocalDate.of(2026, 8, 20),
                " Shade only one answer. ",
                List.of(
                        multipleChoicePart(1),
                        new V2AssessmentPartRequest(
                                2,
                                "Part II",
                                "true_false",
                                BigDecimal.ONE,
                                List.of(new V2AssessmentQuestionRequest(
                                        1,
                                        "Java is strongly typed.",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        "A",
                                        List.of(101L)
                                )),
                                null
                        )
                )
        );
    }

    private V2AssessmentPartRequest multipleChoicePart(int partOrder) {
        return new V2AssessmentPartRequest(
                partOrder,
                "Part I",
                "multiple_choice",
                BigDecimal.ONE,
                List.of(
                        question(1, "B", List.of()),
                        question(2, "C", List.of())
                ),
                List.of(new V2SkillRangeMappingRequest(1, 2, List.of(100L)))
        );
    }

    private V2AssessmentQuestionRequest question(
            int itemNumber,
            String correctOption,
            List<Long> skillIds
    ) {
        return new V2AssessmentQuestionRequest(
                itemNumber,
                itemNumber == 1 ? "What is 1 + 1?" : "What is 2 + 2?",
                itemNumber == 1 ? "1" : "2",
                itemNumber == 1 ? "2" : "3",
                itemNumber == 1 ? "3" : "4",
                itemNumber == 1 ? "4" : "5",
                null,
                correctOption,
                skillIds
        );
    }

    private V2AssessmentAssignmentContext assignment(
            long teacherUserId,
            String schoolId,
            String assignmentStatus,
            String classStatus
    ) {
        return new V2AssessmentAssignmentContext(
                500L,
                700L,
                teacherUserId,
                schoolId,
                3,
                "Computer",
                assignmentStatus,
                "primary",
                1,
                "2026-2027",
                1,
                "Grade 7",
                10,
                "Rizal",
                classStatus
        );
    }

    private V2AssessmentHeader header(String status) {
        return new V2AssessmentHeader(
                900L,
                500L,
                700L,
                20L,
                "SCHOOL-001",
                1,
                "2026-2027",
                1,
                "Grade 7",
                10,
                "Rizal",
                3,
                "Computer",
                11,
                "First Quarter",
                "Quiz 1",
                "quiz",
                LocalDate.of(2026, 8, 20),
                "Shade only one answer.",
                3,
                status,
                NOW,
                NOW
        );
    }
}
