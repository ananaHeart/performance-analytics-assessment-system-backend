package com.capstone.assessment.v3.answersheet.service;

import com.capstone.assessment.v3.answersheet.dto.V3AnswerSheetEligibilityResponse;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.AssignmentContext;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Question;
import com.capstone.assessment.v3.answersheet.repository.V3AnswerSheetRepository;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V3AnswerSheetServiceTest {

    private static final V3AuthenticatedUser TEACHER = new V3AuthenticatedUser(
            42L,
            "SCHOOL-001",
            "teacher@example.com",
            "teacher",
            "active",
            "session"
    );
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private final V3AnswerSheetRepository repository = mock(V3AnswerSheetRepository.class);
    private final V3AnswerSheetPdfRenderer renderer = mock(V3AnswerSheetPdfRenderer.class);
    private final V3AnswerSheetFileStorage storage = mock(V3AnswerSheetFileStorage.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private V3AnswerSheetService service;

    @BeforeEach
    void setUp() {
        service = new V3AnswerSheetService(
                repository,
                renderer,
                storage,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void principalCannotReadAnswerSheetCapabilities() {
        V3AuthenticatedUser principal = new V3AuthenticatedUser(
                1L, "SCHOOL-001", "principal@example.com", "principal", "active", "session"
        );

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.getReferenceData(principal)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("ANSWER_SHEET_TEACHER_REQUIRED", exception.getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void exactValidatedA4AssessmentIsEligible() {
        stubValidAssessment(V3AnswerSheetTestFixtures.tenValidQuestions());

        V3AnswerSheetEligibilityResponse response = service.getEligibility(
                TEACHER,
                5001L,
                "A4"
        );

        assertTrue(response.eligible());
        assertTrue(response.blockers().isEmpty());
        assertEquals(V3AnswerSheetService.VALIDATED_TEMPLATE_CODE, response.templateCode());
        assertEquals(10, response.totalQuestions());
    }

    @Test
    void fiveQuestionsMeetProductMinimumButRemainBlockedWithoutValidatedGeometry() {
        AssignmentContext assignment = V3AnswerSheetTestFixtures.activeAssignment();
        assignment = new AssignmentContext(
                assignment.testAssignmentId(), assignment.assignmentUuid(), assignment.testId(),
                assignment.testUuid(), assignment.testVersionNumber(), assignment.testName(),
                assignment.testStatus(), 5, assignment.assignmentStatus(),
                assignment.classAssignmentStatus(), assignment.gradeLevelName(),
                assignment.sectionName(), assignment.subjectName()
        );
        List<Question> questions = new ArrayList<>(V3AnswerSheetTestFixtures.tenValidQuestions().subList(0, 5));
        when(repository.findOwnedAssignment(5001L, TEACHER.userId(), TEACHER.schoolId()))
                .thenReturn(Optional.of(assignment));
        when(repository.findActivePaperSize("A4")).thenReturn(Optional.of(V3AnswerSheetTestFixtures.a4()));
        when(repository.findQuestions(assignment.testId())).thenReturn(questions);
        when(repository.findValidatedTemplate("A4"))
                .thenReturn(Optional.of(V3AnswerSheetTestFixtures.validatedTemplate()));

        V3AnswerSheetEligibilityResponse response = service.getEligibility(TEACHER, 5001L, "A4");

        assertFalse(response.eligible());
        assertTrue(response.blockers().stream()
                .anyMatch(blocker -> "UNSUPPORTED_TEMPLATE_ITEM_COUNT".equals(blocker.code())));
    }

    @Test
    void mixedQuestionTypeIsExplicitlyBlocked() {
        List<Question> questions = new ArrayList<>(V3AnswerSheetTestFixtures.tenValidQuestions());
        Question original = questions.get(9);
        questions.set(9, new Question(
                original.questionId(), original.questionUuid(), original.testPartId(),
                original.partOrder(), original.partItemCountSnapshot(), original.partItemNumber(),
                original.globalItemNumber(), 2, "true_false", 2, List.of("A", "B"),
                1, 1, 1
        ));
        stubValidAssessment(questions);

        V3AnswerSheetEligibilityResponse response = service.getEligibility(TEACHER, 5001L, "A4");

        assertFalse(response.eligible());
        assertTrue(response.blockers().stream()
                .anyMatch(blocker -> "UNSUPPORTED_QUESTION_TYPE".equals(blocker.code())));
    }

    @Test
    void anotherTeachersAssignmentIsNotDisclosed() {
        when(repository.findOwnedAssignment(5001L, TEACHER.userId(), TEACHER.schoolId()))
                .thenReturn(Optional.empty());

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.getEligibility(TEACHER, 5001L, "A4")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("TEST_ASSIGNMENT_NOT_FOUND", exception.getCode());
    }

    private void stubValidAssessment(List<Question> questions) {
        AssignmentContext assignment = V3AnswerSheetTestFixtures.activeAssignment();
        when(repository.findOwnedAssignment(5001L, TEACHER.userId(), TEACHER.schoolId()))
                .thenReturn(Optional.of(assignment));
        when(repository.findActivePaperSize("A4")).thenReturn(Optional.of(V3AnswerSheetTestFixtures.a4()));
        when(repository.findQuestions(assignment.testId())).thenReturn(questions);
        when(repository.findValidatedTemplate("A4"))
                .thenReturn(Optional.of(V3AnswerSheetTestFixtures.validatedTemplate()));
    }
}
