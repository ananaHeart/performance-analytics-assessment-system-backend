package com.capstone.assessment.v3.assessment.service;

import com.capstone.assessment.v3.assessment.dto.V3AcceptedAnswerRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentPartRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentQuestionRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse;
import com.capstone.assessment.v3.assessment.dto.V3PartSkillMappingRequest;
import com.capstone.assessment.v3.assessment.dto.V3QuestionOptionRequest;
import com.capstone.assessment.v3.assessment.dto.V3RubricCriterionRequest;
import com.capstone.assessment.v3.assessment.dto.V3RubricRequest;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AssessmentHeader;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AssignmentContext;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.ClassScheduleWindow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.OptionRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.PartRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.QuestionRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.QuestionType;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.SkillMappingRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.TermWindow;
import com.capstone.assessment.v3.assessment.repository.V3AssessmentRepository;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V3AssessmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final V3AuthenticatedUser TEACHER = new V3AuthenticatedUser(
            42L, "SCHOOL-001", "teacher@example.com", "teacher", "active", "session"
    );
    private static final AssignmentContext ASSIGNMENT = new AssignmentContext(
            77L,
            42L,
            "SCHOOL-001",
            700L,
            2026,
            "2025-2026",
            8,
            "Grade 8",
            "Narra",
            2,
            "Science",
            "primary",
            "active",
            "active"
    );

    private final V3AssessmentRepository repository = mock(V3AssessmentRepository.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private V3AssessmentService service;

    @BeforeEach
    void setUp() {
        service = new V3AssessmentService(
                repository,
                auditService,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsCompleteDraftWithAllFiveQuestionTypes() {
        stubValidCreationContext();
        when(repository.insertTest(
                anyString(), eq("SCHOOL-001"), eq(42L), eq(9),
                eq("Five Type Assessment"), eq("quiz"), eq("Mixed assessment"), eq(5)
        )).thenReturn(1000L);
        when(repository.findHeader(1000L)).thenReturn(Optional.of(header("draft", 5)));

        V3AssessmentResponse response = service.createAssessment(
                TEACHER,
                validFiveTypeRequest(),
                null
        );

        assertEquals(1000L, response.testId());
        assertEquals(5, response.totalItems());
        assertEquals("draft", response.status());
        verify(repository, times(5)).insertTestPart(
                eq(1000L), anyInt(), anyString(), anyInt(), eq(1), any(BigDecimal.class), nullable(String.class)
        );
        verify(repository, times(5)).insertQuestion(
                anyString(), anyLong(), anyInt(), eq(1), anyString(), any(BigDecimal.class),
                nullable(Long.class), nullable(String.class), anyBoolean(), nullable(Integer.class),
                nullable(Integer.class), anyString(), anyBoolean()
        );
        verify(repository, times(6)).insertQuestionOption(anyLong(), anyString(), anyString(), anyInt());
        verify(repository, times(5)).insertAnswerKey(
                anyLong(), anyString(), nullable(Long.class), anyString(), nullable(Long.class), nullable(String.class)
        );
        verify(repository, times(3)).insertAcceptedAnswer(
                anyLong(), nullable(Integer.class), anyString(), anyString(), anyString(),
                anyBoolean(), any(BigDecimal.class), anyBoolean()
        );
        verify(repository).insertRubric(
                anyString(), eq("SCHOOL-001"), eq(42L), eq("Science Explanation Rubric"),
                nullable(String.class), eq(new BigDecimal("5"))
        );
        verify(repository, times(2)).insertRubricCriterion(
                anyLong(), anyInt(), anyString(), anyString(), any(BigDecimal.class),
                nullable(String.class), anyBoolean()
        );
        verify(repository, times(5)).insertPartSkillMapping(anyLong(), eq(501L), eq(1), eq(1));
    }

    @Test
    void unauthenticatedAndPrincipalUsersCannotCreateAssessments() {
        V3AuthException unauthenticated = assertThrows(
                V3AuthException.class,
                () -> service.createAssessment(null, validFiveTypeRequest(), null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatus());

        V3AuthenticatedUser principal = new V3AuthenticatedUser(
                1L, "SCHOOL-001", "principal@example.com", "principal", "active", "session"
        );
        V3AuthException principalError = assertThrows(
                V3AuthException.class,
                () -> service.createAssessment(principal, validFiveTypeRequest(), null)
        );
        assertEquals(HttpStatus.FORBIDDEN, principalError.getStatus());
        assertEquals("TEACHER_ROLE_REQUIRED", principalError.getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void inactiveTeacherCannotCreateAssessment() {
        V3AuthenticatedUser inactive = new V3AuthenticatedUser(
                42L, "SCHOOL-001", "teacher@example.com", "teacher", "inactive", "session"
        );

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.createAssessment(inactive, validFiveTypeRequest(), null)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("ACTIVE_SCHOOL_ACCOUNT_REQUIRED", exception.getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void teacherCannotUseAnotherTeachersClassAssignment() {
        AssignmentContext foreignAssignment = new AssignmentContext(
                77L, 99L, "SCHOOL-001", 700L, 2026, "2025-2026",
                8, "Grade 8", "Narra", 2, "Science", "primary", "active", "active"
        );
        when(repository.findAssignmentContext(77L)).thenReturn(Optional.of(foreignAssignment));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.createAssessment(TEACHER, validFiveTypeRequest(), null)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("CLASS_ASSIGNMENT_FORBIDDEN", exception.getCode());
        verify(repository, never()).insertTest(
                anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString(),
                nullable(String.class), anyInt()
        );
    }

    @Test
    void termPeriodMustBelongToAssignmentAcademicYear() {
        when(repository.findAssignmentContext(77L)).thenReturn(Optional.of(ASSIGNMENT));
        when(repository.findTermWindow(9, 2026, "SCHOOL-001")).thenReturn(Optional.empty());

        assertInvalidField(
                "termPeriodId",
                () -> service.createAssessment(TEACHER, validFiveTypeRequest(), null)
        );
        verify(repository, never()).insertTest(
                anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString(),
                nullable(String.class), anyInt()
        );
    }

    @Test
    void assessmentScheduleMustStayInsideSelectedTerm() {
        stubValidCreationContext();
        when(repository.findTermWindow(9, 2026, "SCHOOL-001")).thenReturn(Optional.of(
                new TermWindow(9, 2026, NOW, NOW.plusSeconds(5_400), "active")
        ));

        assertInvalidField(
                "closeAt",
                () -> service.createAssessment(TEACHER, validFiveTypeRequest(), null)
        );
        verify(repository, never()).insertTest(
                anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString(),
                nullable(String.class), anyInt()
        );
    }

    @Test
    void outsideTimetableCloseRequiresExplicitConfirmation() {
        stubValidCreationContext();
        when(repository.listActiveClassScheduleWindows(77L)).thenReturn(List.of(
                new ClassScheduleWindow(
                        2,
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0),
                        "Asia/Manila",
                        LocalDate.of(2026, 9, 1),
                        null
                )
        ));

        assertInvalidField(
                "confirmOutsideClassSchedule",
                () -> service.createAssessment(TEACHER, validFiveTypeRequest(), null)
        );
        verify(repository, never()).insertTest(
                anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString(),
                nullable(String.class), anyInt()
        );
    }

    @Test
    void confirmedOutsideTimetableClosePersistsAuditFields() {
        stubValidCreationContext();
        when(repository.listActiveClassScheduleWindows(77L)).thenReturn(List.of(
                new ClassScheduleWindow(
                        2,
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0),
                        "Asia/Manila",
                        LocalDate.of(2026, 9, 1),
                        null
                )
        ));
        when(repository.insertTest(
                anyString(), eq("SCHOOL-001"), eq(42L), eq(9),
                eq("Five Type Assessment"), eq("quiz"), eq("Mixed assessment"), eq(5)
        )).thenReturn(1000L);
        when(repository.findHeader(1000L)).thenReturn(Optional.of(header("draft", 5)));
        V3AssessmentRequest request = withScheduleConfirmation(
                validFiveTypeRequest(),
                true,
                "Deadline adjusted for a school activity."
        );

        service.createAssessment(TEACHER, request, null);

        verify(repository).insertTestAssignment(
                anyString(),
                eq(1000L),
                eq(77L),
                eq(42L),
                eq(NOW.plusSeconds(3600)),
                eq(NOW.plusSeconds(7200)),
                eq(false),
                eq(true),
                eq("Deadline adjusted for a school activity."),
                eq(42L),
                eq(NOW)
        );
    }

    @Test
    void duplicatePartOrderIsRejected() {
        stubValidCreationContext();
        V3AssessmentPartRequest first = multipleChoicePart(1, List.of(mcQuestion(1)), 1, fullMapping(1));
        V3AssessmentPartRequest duplicate = trueFalsePart(1, "A");
        V3AssessmentRequest request = requestWithParts(List.of(first, duplicate));

        assertInvalidField(
                "parts.partOrder",
                () -> service.createAssessment(TEACHER, request, null)
        );
        verify(repository, never()).insertTest(
                anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString(),
                nullable(String.class), anyInt()
        );
    }

    @Test
    void duplicateItemNumberWithinPartIsRejected() {
        stubValidCreationContext();
        V3AssessmentPartRequest part = multipleChoicePart(
                1,
                List.of(mcQuestion(1), mcQuestion(1)),
                2,
                fullMapping(2)
        );

        assertInvalidField(
                "parts.questions.itemNumber",
                () -> service.createAssessment(TEACHER, requestWithParts(List.of(part)), null)
        );
    }

    @Test
    void everyPartItemMustHaveSkillRangeCoverage() {
        stubValidCreationContext();
        V3AssessmentPartRequest part = multipleChoicePart(
                1,
                List.of(mcQuestion(1), mcQuestion(2)),
                2,
                List.of(new V3PartSkillMappingRequest(1, 1, List.of(501L)))
        );

        assertInvalidField(
                "parts.skillMappings",
                () -> service.createAssessment(TEACHER, requestWithParts(List.of(part)), null)
        );
    }

    @Test
    void trueFalseOnlyAcceptsAOrBAndGeneratesItsOwnOptions() {
        stubValidCreationContext();

        assertInvalidField(
                "parts.questions.correctOptionKey",
                () -> service.createAssessment(
                        TEACHER,
                        requestWithParts(List.of(trueFalsePart(1, "C"))),
                        null
                )
        );
    }

    @Test
    void enumerationPrimaryPointsMustEqualQuestionMaximum() {
        stubValidCreationContext();
        V3AssessmentQuestionRequest invalidEnumeration = new V3AssessmentQuestionRequest(
                1,
                "List three states of matter.",
                new BigDecimal("3"),
                null,
                200,
                2,
                "medium",
                false,
                List.of(),
                null,
                null,
                "normalized",
                List.of(
                        accepted(1, "Solid", "1", true),
                        accepted(2, "Liquid", "1", true)
                ),
                null,
                null
        );
        V3AssessmentPartRequest part = part(
                1, "Enumeration", "enumeration", "3", invalidEnumeration
        );

        assertInvalidField(
                "parts.questions.acceptedAnswers.points",
                () -> service.createAssessment(TEACHER, requestWithParts(List.of(part)), null)
        );
    }

    @Test
    void incompleteStoredQuestionBlocksActivation() {
        when(repository.findHeader(1000L)).thenReturn(Optional.of(header("draft", 1)));
        when(repository.findAssignmentContext(77L)).thenReturn(Optional.of(ASSIGNMENT));
        when(repository.findParts(1000L)).thenReturn(List.of(new PartRow(
                3000L, 1000L, 1, "Multiple Choice", 1,
                "multiple_choice", "Multiple Choice", 1, BigDecimal.ONE, null
        )));
        when(repository.findQuestions(1000L)).thenReturn(List.of(new QuestionRow(
                4000L, "question-uuid", 3000L, 1, "Question", BigDecimal.ONE,
                null, null, false, null, null, "none", false
        )));
        when(repository.findOptions(1000L)).thenReturn(List.of(
                option(1L, "A", 1), option(2L, "B", 2),
                option(3L, "C", 3), option(4L, "D", 4)
        ));
        when(repository.findSkillMappings(1000L)).thenReturn(List.of(new SkillMappingRow(
                5000L, 3000L, 501L, "Matter", "States of Matter", 1, 1, 1
        )));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.activateAssessment(TEACHER, 1000L, null)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("ASSESSMENT_ANSWER_KEY_INCOMPLETE", exception.getCode());
        verify(repository, never()).activateTest(anyLong(), any(Instant.class));
    }

    @Test
    void activeAssessmentContentCannotBeEdited() {
        when(repository.findHeader(1000L)).thenReturn(Optional.of(header("active", 5)));
        when(repository.findAssignmentContext(77L)).thenReturn(Optional.of(ASSIGNMENT));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.updateAssessment(TEACHER, 1000L, validFiveTypeRequest(), null)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("ASSESSMENT_CONTENT_LOCKED", exception.getCode());
    }

    @Test
    void unchangedDraftValuesRemainSavableWhenMysqlReportsZeroChangedRows() {
        stubValidCreationContext();
        when(repository.findHeader(1000L)).thenReturn(Optional.of(header("draft", 5)));
        when(repository.updateTestHeader(
                eq(1000L), eq(9), eq("Five Type Assessment"), eq("quiz"),
                eq("Mixed assessment"), eq(5)
        )).thenReturn(0);
        when(repository.isDraftTest(1000L)).thenReturn(true);
        when(repository.updateTestAssignment(
                eq(2000L), eq(NOW.plusSeconds(3600)), eq(NOW.plusSeconds(7200)), eq(false),
                eq(false), nullable(String.class), nullable(Long.class), nullable(Instant.class)
        )).thenReturn(0);
        when(repository.isPlannedTestAssignment(2000L)).thenReturn(true);

        V3AssessmentResponse response = service.updateAssessment(
                TEACHER,
                1000L,
                validFiveTypeRequest(),
                null
        );

        assertEquals(1000L, response.testId());
        assertEquals("draft", response.status());
        verify(repository).deleteParts(1000L);
        verify(repository).isDraftTest(1000L);
        verify(repository).isPlannedTestAssignment(2000L);
    }

    @Test
    void persistenceConflictIsTranslatedAndWriteMethodsAreTransactional() throws Exception {
        stubValidCreationContext();
        when(repository.insertTest(
                anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString(),
                nullable(String.class), anyInt()
        )).thenThrow(new DataIntegrityViolationException("duplicate"));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.createAssessment(TEACHER, validFiveTypeRequest(), null)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("ASSESSMENT_CONFLICT", exception.getCode());
        verify(repository, never()).insertTestAssignment(
                anyString(), anyLong(), anyLong(), anyLong(), nullable(Instant.class),
                nullable(Instant.class), anyBoolean(), anyBoolean(), nullable(String.class),
                nullable(Long.class), nullable(Instant.class)
        );
        assertNotNull(V3AssessmentService.class.getMethod(
                "createAssessment", V3AuthenticatedUser.class, V3AssessmentRequest.class,
                com.capstone.assessment.v3.auth.service.V3RequestMetadata.class
        ).getAnnotation(Transactional.class));
        assertNotNull(V3AssessmentService.class.getMethod(
                "updateAssessment", V3AuthenticatedUser.class, long.class, V3AssessmentRequest.class,
                com.capstone.assessment.v3.auth.service.V3RequestMetadata.class
        ).getAnnotation(Transactional.class));
    }

    private void stubValidCreationContext() {
        when(repository.findAssignmentContext(77L)).thenReturn(Optional.of(ASSIGNMENT));
        when(repository.findTermWindow(9, 2026, "SCHOOL-001")).thenReturn(Optional.of(
                new TermWindow(9, 2026, NOW, NOW.plusSeconds(100_000), "active")
        ));
        when(repository.listActiveClassScheduleWindows(77L)).thenReturn(List.of());
        when(repository.findActiveQuestionType("multiple_choice"))
                .thenReturn(Optional.of(questionType(1, "multiple_choice", true, false, false)));
        when(repository.findActiveQuestionType("true_false"))
                .thenReturn(Optional.of(questionType(2, "true_false", true, false, false)));
        when(repository.findActiveQuestionType("identification"))
                .thenReturn(Optional.of(questionType(3, "identification", false, true, true)));
        when(repository.findActiveQuestionType("enumeration"))
                .thenReturn(Optional.of(questionType(4, "enumeration", false, true, true)));
        when(repository.findActiveQuestionType("essay"))
                .thenReturn(Optional.of(questionType(5, "essay", false, true, true)));
        when(repository.findValidSkillIds(Set.of(501L), 9, 8, 2)).thenReturn(Set.of(501L));
    }

    private V3AssessmentRequest validFiveTypeRequest() {
        return requestWithParts(List.of(
                multipleChoicePart(1, List.of(mcQuestion(1)), 1, fullMapping(1)),
                trueFalsePart(2, "B"),
                part(3, "Identification", "identification", "2", identificationQuestion()),
                part(4, "Enumeration", "enumeration", "2", enumerationQuestion()),
                part(5, "Essay", "essay", "5", essayQuestion())
        ));
    }

    private V3AssessmentRequest requestWithParts(List<V3AssessmentPartRequest> parts) {
        return new V3AssessmentRequest(
                77L,
                9,
                "Five Type Assessment",
                "quiz",
                "Mixed assessment",
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                false,
                false,
                null,
                parts
        );
    }

    private V3AssessmentRequest withScheduleConfirmation(
            V3AssessmentRequest request,
            boolean confirmed,
            String reason
    ) {
        return new V3AssessmentRequest(
                request.classAssignmentId(),
                request.termPeriodId(),
                request.testName(),
                request.testType(),
                request.instructions(),
                request.openAt(),
                request.closeAt(),
                request.allowLateCapture(),
                confirmed,
                reason,
                request.parts()
        );
    }

    private V3AssessmentPartRequest multipleChoicePart(
            int partOrder,
            List<V3AssessmentQuestionRequest> questions,
            int numberOfItems,
            List<V3PartSkillMappingRequest> mappings
    ) {
        return new V3AssessmentPartRequest(
                partOrder,
                "Multiple Choice",
                "multiple_choice",
                numberOfItems,
                BigDecimal.ONE,
                null,
                questions,
                mappings
        );
    }

    private V3AssessmentPartRequest trueFalsePart(int partOrder, String correctKey) {
        V3AssessmentQuestionRequest question = new V3AssessmentQuestionRequest(
                1, "Water boils at 100 degrees Celsius.", BigDecimal.ONE, null,
                null, null, null, false, List.of(), correctKey, null,
                null, List.of(), null, null
        );
        return part(partOrder, "True or False", "true_false", "1", question);
    }

    private V3AssessmentPartRequest part(
            int partOrder,
            String partName,
            String type,
            String points,
            V3AssessmentQuestionRequest question
    ) {
        return new V3AssessmentPartRequest(
                partOrder,
                partName,
                type,
                1,
                new BigDecimal(points),
                null,
                List.of(question),
                fullMapping(1)
        );
    }

    private V3AssessmentQuestionRequest mcQuestion(int itemNumber) {
        return new V3AssessmentQuestionRequest(
                itemNumber,
                "Which state has a fixed volume but no fixed shape?",
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                false,
                List.of(
                        optionRequest("A", "Solid", 1),
                        optionRequest("B", "Liquid", 2),
                        optionRequest("C", "Gas", 3),
                        optionRequest("D", "Plasma", 4)
                ),
                "B",
                null,
                null,
                List.of(),
                null,
                null
        );
    }

    private V3AssessmentQuestionRequest identificationQuestion() {
        return new V3AssessmentQuestionRequest(
                1,
                "Name the process plants use to make food.",
                new BigDecimal("2"),
                null,
                120,
                1,
                "short",
                false,
                List.of(),
                null,
                null,
                "normalized",
                List.of(accepted(null, "Photosynthesis", "2", true)),
                null,
                null
        );
    }

    private V3AssessmentQuestionRequest enumerationQuestion() {
        return new V3AssessmentQuestionRequest(
                1,
                "Give two states of matter.",
                new BigDecimal("2"),
                null,
                200,
                2,
                "medium",
                false,
                List.of(),
                null,
                null,
                "normalized",
                List.of(
                        accepted(1, "Solid", "1", true),
                        accepted(2, "Liquid", "1", true)
                ),
                null,
                null
        );
    }

    private V3AssessmentQuestionRequest essayQuestion() {
        V3RubricRequest rubric = new V3RubricRequest(
                "Science Explanation Rubric",
                "Scores scientific accuracy and clarity.",
                List.of(
                        new V3RubricCriterionRequest(
                                1, "Scientific accuracy", "Uses correct scientific ideas.",
                                new BigDecimal("3"), null, true
                        ),
                        new V3RubricCriterionRequest(
                                2, "Clarity", "Explains the answer clearly.",
                                new BigDecimal("2"), null, true
                        )
                )
        );
        return new V3AssessmentQuestionRequest(
                1,
                "Explain how matter changes state.",
                new BigDecimal("5"),
                "Use complete sentences.",
                1000,
                null,
                "long",
                true,
                List.of(),
                null,
                null,
                null,
                List.of(),
                null,
                rubric
        );
    }

    private V3AcceptedAnswerRequest accepted(
            Integer order,
            String text,
            String points,
            boolean primary
    ) {
        return new V3AcceptedAnswerRequest(
                order, text, new BigDecimal(points), primary, false
        );
    }

    private V3QuestionOptionRequest optionRequest(String key, String text, int order) {
        return new V3QuestionOptionRequest(key, text, order);
    }

    private List<V3PartSkillMappingRequest> fullMapping(int endItem) {
        return List.of(new V3PartSkillMappingRequest(1, endItem, List.of(501L)));
    }

    private QuestionType questionType(
            int id,
            String code,
            boolean supportsOmr,
            boolean requiresAttachment,
            boolean teacherVerification
    ) {
        return new QuestionType(
                id,
                code,
                code,
                supportsOmr ? "bubble" : "written",
                supportsOmr ? "automatic" : "teacher",
                supportsOmr,
                !supportsOmr,
                "enumeration".equals(code),
                requiresAttachment,
                teacherVerification,
                !supportsOmr,
                true
        );
    }

    private AssessmentHeader header(String status, int totalItems) {
        return new AssessmentHeader(
                1000L,
                "test-uuid",
                "SCHOOL-001",
                42L,
                1,
                9,
                "First Quarter",
                "Five Type Assessment",
                "quiz",
                "Mixed assessment",
                totalItems,
                status,
                2000L,
                "assignment-uuid",
                77L,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                "planned",
                false,
                false,
                null,
                null,
                null,
                700L,
                2026,
                "2025-2026",
                8,
                "Grade 8",
                "Narra",
                2,
                "Science",
                NOW,
                NOW
        );
    }

    private OptionRow option(long id, String key, int order) {
        return new OptionRow(id, 4000L, key, key, order);
    }

    private void assertInvalidField(String field, Runnable invocation) {
        V3FieldValidationException exception = assertThrows(
                V3FieldValidationException.class,
                invocation::run
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getErrors().containsKey(field), exception.getErrors().toString());
    }
}
