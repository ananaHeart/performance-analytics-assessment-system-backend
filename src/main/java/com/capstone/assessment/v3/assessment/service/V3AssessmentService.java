package com.capstone.assessment.v3.assessment.service;

import com.capstone.assessment.v3.assessment.dto.V3AcceptedAnswerRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentPartRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentQuestionRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentReferenceDataResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentSummaryResponse;
import com.capstone.assessment.v3.assessment.dto.V3PartSkillMappingRequest;
import com.capstone.assessment.v3.assessment.dto.V3QuestionOptionRequest;
import com.capstone.assessment.v3.assessment.dto.V3RubricCriterionRequest;
import com.capstone.assessment.v3.assessment.dto.V3RubricRequest;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AcceptedAnswerRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AnswerKeyRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AssessmentHeader;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AssignmentContext;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.ClassScheduleWindow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.OptionRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.PartRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.QuestionRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.QuestionType;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.RubricCriterionRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.RubricRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.SkillMappingRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.TermWindow;
import com.capstone.assessment.v3.assessment.repository.V3AssessmentRepository;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Profile("v3")
@Service
public class V3AssessmentService {

    private static final String TEACHER_ROLE = "teacher";
    private static final Set<String> TEST_TYPES = Set.of(
            "quiz", "exam", "diagnostic", "long_test", "other"
    );
    private static final Set<String> QUESTION_TYPES = Set.of(
            "multiple_choice", "true_false", "identification", "enumeration", "essay"
    );
    private static final Set<String> RESPONSE_REGION_SIZES = Set.of(
            "none", "short", "medium", "long", "full_page"
    );
    private static final Set<String> TEXT_MATCHING_MODES = Set.of("exact", "normalized");
    private static final List<String> MC_KEYS = List.of("A", "B", "C", "D");
    private static final List<String> TF_KEYS = List.of("A", "B");
    private static final int MAX_TOTAL_ITEMS = 500;
    private static final int MAX_ITEMS_PER_PART = 200;
    private static final BigDecimal MAX_POINTS = new BigDecimal("999999.99");
    private static final ZoneId SCHOOL_TIMEZONE = ZoneId.of("Asia/Manila");

    private final V3AssessmentRepository repository;
    private final V3AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public V3AssessmentService(
            V3AssessmentRepository repository,
            V3AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this(repository, auditService, objectMapper, Clock.systemUTC());
    }

    V3AssessmentService(
            V3AssessmentRepository repository,
            V3AuditService auditService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public V3AssessmentReferenceDataResponse getReferenceData(
            V3AuthenticatedUser user,
            Long classAssignmentId,
            Integer termPeriodId
    ) {
        requireTeacher(user);
        List<AssignmentContext> contexts = repository.listActiveAssignments(user.userId(), user.schoolId());
        List<V3AssessmentReferenceDataResponse.AssignmentOption> assignments = contexts.stream()
                .map(this::toAssignmentOption)
                .toList();

        AssignmentContext selected = null;
        if (classAssignmentId != null) {
            selected = requireOwnedActiveAssignment(user, classAssignmentId);
        }

        List<V3AssessmentReferenceDataResponse.TermPeriodOption> terms = selected == null
                ? List.of()
                : repository.listTermPeriods(selected.academicYearId());
        if (selected != null && termPeriodId != null
                && !repository.termPeriodBelongsToAcademicYear(termPeriodId, selected.academicYearId())) {
            throw invalid("termPeriodId", "The term period does not belong to the assignment academic year.");
        }

        List<V3AssessmentReferenceDataResponse.SkillOption> skills = selected == null || termPeriodId == null
                ? List.of()
                : repository.listSkills(termPeriodId, selected.gradeLevelId(), selected.subjectId());
        List<V3AssessmentReferenceDataResponse.QuestionTypeOption> questionTypes = repository
                .listActiveQuestionTypes()
                .stream()
                .map(this::toQuestionTypeOption)
                .toList();

        return new V3AssessmentReferenceDataResponse(
                assignments,
                selected == null ? null : toAssignmentOption(selected),
                terms,
                questionTypes,
                skills,
                repository.listRubrics(user.schoolId(), user.userId()),
                TEST_TYPES.stream().sorted().toList(),
                List.of("none", "short", "medium", "long", "full_page")
        );
    }

    @Transactional
    public V3AssessmentResponse createAssessment(
            V3AuthenticatedUser user,
            V3AssessmentRequest request,
            V3RequestMetadata metadata
    ) {
        requireTeacher(user);
        Instant now = clock.instant();
        PreparedAssessment prepared = prepareAssessment(user, request, null, now);
        try {
            long testId = repository.insertTest(
                    UUID.randomUUID().toString(),
                    user.schoolId(),
                    user.userId(),
                    prepared.termPeriodId(),
                    prepared.testName(),
                    prepared.testType(),
                    prepared.instructions(),
                    prepared.totalItems()
            );
            long testAssignmentId = repository.insertTestAssignment(
                    UUID.randomUUID().toString(),
                    testId,
                    prepared.assignment().classAssignmentId(),
                    user.userId(),
                    prepared.openAt(),
                    prepared.closeAt(),
                    prepared.allowLateCapture(),
                    prepared.outsideScheduleConfirmed(),
                    prepared.outsideScheduleReason(),
                    prepared.outsideScheduleConfirmedByUserId(),
                    prepared.outsideScheduleConfirmedAt()
            );
            insertAssessmentGraph(user, testId, prepared);
            auditService.record(
                    user.userId(),
                    "assessment.create",
                    "tests",
                    Long.toString(testId),
                    "success",
                    metadata,
                    Map.of(
                            "testAssignmentId", testAssignmentId,
                            "classAssignmentId", prepared.assignment().classAssignmentId(),
                            "totalItems", prepared.totalItems(),
                            "partCount", prepared.parts().size()
                    ),
                    now
            );
            return requireAssessment(user, testId);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ASSESSMENT_CONFLICT",
                    "The assessment conflicts with existing assessment or rubric data.");
        }
    }

    @Transactional(readOnly = true)
    public List<V3AssessmentSummaryResponse> listAssessments(
            V3AuthenticatedUser user,
            long classAssignmentId
    ) {
        requireTeacher(user);
        requireOwnedActiveOrHistoricalAssignment(user, classAssignmentId);
        return repository.listAssessments(
                user.userId(),
                user.schoolId(),
                classAssignmentId
        );
    }

    @Transactional(readOnly = true)
    public V3AssessmentResponse getAssessment(V3AuthenticatedUser user, long testId) {
        requireTeacher(user);
        return requireAssessment(user, testId);
    }

    @Transactional
    public V3AssessmentResponse updateAssessment(
            V3AuthenticatedUser user,
            long testId,
            V3AssessmentRequest request,
            V3RequestMetadata metadata
    ) {
        requireTeacher(user);
        repository.lockTest(testId);
        AssessmentHeader header = requireOwnedHeader(user, testId);
        if (!"draft".equals(header.status())) {
            throw conflict("ASSESSMENT_CONTENT_LOCKED",
                    "Only draft assessments may be edited. Create a new version for active content.");
        }
        if (header.classAssignmentId() != request.classAssignmentId()) {
            throw invalid("classAssignmentId",
                    "An existing assessment cannot be moved to a different class assignment.");
        }

        Instant now = clock.instant();
        PreparedAssessment prepared = prepareAssessment(user, request, header, now);
        List<Long> replacedDraftRubrics = repository.findDraftRubricIdsForTest(testId, user.userId());
        try {
            int updatedHeaders = repository.updateTestHeader(
                    testId,
                    prepared.termPeriodId(),
                    prepared.testName(),
                    prepared.testType(),
                    prepared.instructions(),
                    prepared.totalItems()
            );
            if (updatedHeaders != 1 && !repository.isDraftTest(testId)) {
                throw conflict("ASSESSMENT_UPDATE_CONFLICT", "The draft assessment could not be updated.");
            }
            int updatedAssignments = repository.updateTestAssignment(
                    header.testAssignmentId(),
                    prepared.openAt(),
                    prepared.closeAt(),
                    prepared.allowLateCapture(),
                    prepared.outsideScheduleConfirmed(),
                    prepared.outsideScheduleReason(),
                    prepared.outsideScheduleConfirmedByUserId(),
                    prepared.outsideScheduleConfirmedAt()
            );
            if (updatedAssignments != 1
                    && !repository.isPlannedTestAssignment(header.testAssignmentId())) {
                throw conflict("ASSESSMENT_SCHEDULE_LOCKED", "The assessment schedule is no longer editable.");
            }
            repository.deleteParts(testId);
            for (Long rubricId : replacedDraftRubrics) {
                repository.deleteDraftRubricIfUnused(rubricId, user.userId());
            }
            insertAssessmentGraph(user, testId, prepared);
            auditService.record(
                    user.userId(),
                    "assessment.update",
                    "tests",
                    Long.toString(testId),
                    "success",
                    metadata,
                    Map.of(
                            "totalItems", prepared.totalItems(),
                            "partCount", prepared.parts().size()
                    ),
                    now
            );
            return requireAssessment(user, testId);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ASSESSMENT_CONFLICT",
                    "The assessment conflicts with existing assessment or rubric data.");
        }
    }

    @Transactional
    public V3AssessmentResponse activateAssessment(
            V3AuthenticatedUser user,
            long testId,
            V3RequestMetadata metadata
    ) {
        requireTeacher(user);
        repository.lockTest(testId);
        AssessmentHeader header = requireOwnedHeader(user, testId);
        if ("active".equals(header.status())) {
            return requireAssessment(user, testId);
        }
        if (!"draft".equals(header.status())) {
            throw conflict("ASSESSMENT_NOT_ACTIVATABLE", "Only draft assessments may be activated.");
        }

        V3AssessmentResponse assessment = requireAssessment(user, testId);
        validateActivationReadiness(assessment);
        Instant now = clock.instant();
        if (assessment.closeAt() != null && !assessment.closeAt().isAfter(now)) {
            throw invalid("closeAt", "The close time must be in the future when activating an assessment.");
        }
        String assignmentStatus = assessment.openAt() != null && assessment.openAt().isAfter(now)
                ? "planned"
                : "open";
        if (repository.activateTest(testId, now) != 1) {
            throw conflict("ASSESSMENT_ACTIVATION_CONFLICT", "The assessment was changed before activation.");
        }
        repository.activateTestAssignment(header.testAssignmentId(), assignmentStatus);
        repository.activateDraftRubricsForTest(testId, user.userId());
        auditService.record(
                user.userId(),
                "assessment.activate",
                "tests",
                Long.toString(testId),
                "success",
                metadata,
                Map.of(
                        "testAssignmentId", header.testAssignmentId(),
                        "assignmentStatus", assignmentStatus,
                        "totalItems", assessment.totalItems()
                ),
                now
        );
        return requireAssessment(user, testId);
    }

    @Transactional
    public V3AssessmentResponse archiveAssessment(
            V3AuthenticatedUser user,
            long testId,
            V3RequestMetadata metadata
    ) {
        requireTeacher(user);
        repository.lockTest(testId);
        AssessmentHeader header = requireOwnedHeader(user, testId);
        if (!"archived".equals(header.status())) {
            Instant now = clock.instant();
            repository.archiveTest(testId, now);
            repository.archiveTestAssignment(header.testAssignmentId());
            auditService.record(
                    user.userId(),
                    "assessment.archive",
                    "tests",
                    Long.toString(testId),
                    "success",
                    metadata,
                    Map.of("testAssignmentId", header.testAssignmentId()),
                    now
            );
        }
        return requireAssessment(user, testId);
    }

    private PreparedAssessment prepareAssessment(
            V3AuthenticatedUser user,
            V3AssessmentRequest request,
            AssessmentHeader existing,
            Instant now
    ) {
        if (request == null) {
            throw invalid("request", "Assessment request is required.");
        }
        AssignmentContext assignment = requireOwnedActiveAssignment(user, request.classAssignmentId());
        TermWindow termWindow = repository.findTermWindow(
                        request.termPeriodId(), assignment.academicYearId(), user.schoolId()
                )
                .orElseThrow(() -> invalid(
                        "termPeriodId",
                        "The term period does not belong to the assignment academic year and school."
                ));
        ScheduleDecision scheduleDecision = validateSchedule(
                user,
                request,
                assignment,
                termWindow,
                now
        );

        String testName = requiredTrimmed(request.testName(), "testName", 120);
        String testType = normalizeAllowed(request.testType(), TEST_TYPES, "testType");
        String instructions = optionalTrimmed(request.instructions(), 10_000, "instructions");
        List<V3AssessmentPartRequest> requestedParts = safeList(request.parts());
        if (requestedParts.isEmpty()) {
            throw invalid("parts", "At least one assessment part is required.");
        }

        Set<Integer> partOrders = new LinkedHashSet<>();
        Set<String> inlineRubricNames = new HashSet<>();
        List<PreparedPart> parts = new ArrayList<>();
        Set<Long> requestedSkillIds = new LinkedHashSet<>();
        int totalItems = 0;
        for (V3AssessmentPartRequest part : requestedParts) {
            if (part == null) {
                throw invalid("parts", "Assessment parts cannot contain null entries.");
            }
            if (!partOrders.add(part.partOrder())) {
                throw invalid("parts.partOrder", "Each part order must be unique.");
            }
            PreparedPart preparedPart = preparePart(user, assignment, request.termPeriodId(), part, inlineRubricNames);
            parts.add(preparedPart);
            totalItems = Math.addExact(totalItems, preparedPart.questions().size());
            for (PreparedMapping mapping : preparedPart.mappings()) {
                requestedSkillIds.addAll(mapping.skillIds());
            }
        }
        requireContiguous(partOrders, requestedParts.size(), "parts.partOrder", "Part orders");
        if (totalItems > MAX_TOTAL_ITEMS) {
            throw invalid("parts", "An assessment may contain at most " + MAX_TOTAL_ITEMS + " questions.");
        }

        Set<Long> validSkillIds = repository.findValidSkillIds(
                requestedSkillIds,
                request.termPeriodId(),
                assignment.gradeLevelId(),
                assignment.subjectId()
        );
        if (!validSkillIds.equals(requestedSkillIds)) {
            Set<Long> invalid = new LinkedHashSet<>(requestedSkillIds);
            invalid.removeAll(validSkillIds);
            throw new V3FieldValidationException(
                    "Assessment validation failed.",
                    HttpStatus.BAD_REQUEST,
                    Map.of(
                            "code", "INVALID_SKILL_CONTEXT",
                            "parts.skillMappings", "Invalid skill IDs for the selected term, grade, or subject: " + invalid
                    )
            );
        }

        parts.sort(Comparator.comparingInt(PreparedPart::partOrder));
        return new PreparedAssessment(
                assignment,
                request.termPeriodId(),
                testName,
                testType,
                instructions,
                request.openAt(),
                request.closeAt(),
                Boolean.TRUE.equals(request.allowLateCapture()),
                scheduleDecision.outsideScheduleConfirmed(),
                scheduleDecision.reason(),
                scheduleDecision.confirmedByUserId(),
                scheduleDecision.confirmedAt(),
                List.copyOf(parts),
                totalItems
        );
    }

    private PreparedPart preparePart(
            V3AuthenticatedUser user,
            AssignmentContext assignment,
            int termPeriodId,
            V3AssessmentPartRequest part,
            Set<String> inlineRubricNames
    ) {
        if (part.numberOfItems() > MAX_ITEMS_PER_PART) {
            throw invalid("parts.numberOfItems",
                    "A test part may contain at most " + MAX_ITEMS_PER_PART + " questions.");
        }
        String typeCode = normalizeAllowed(part.questionTypeCode(), QUESTION_TYPES,
                "parts.questionTypeCode");
        QuestionType questionType = repository.findActiveQuestionType(typeCode)
                .orElseThrow(() -> invalid("parts.questionTypeCode",
                        "The selected question type is not active in V3 reference data."));
        String partName = requiredTrimmed(part.partName(), "parts.partName", 80);
        String partInstructions = optionalTrimmed(part.partInstructions(), 5_000, "parts.partInstructions");
        BigDecimal pointsPerItem = validPoints(part.pointsPerItem(), "parts.pointsPerItem");

        List<V3AssessmentQuestionRequest> requestedQuestions = safeList(part.questions());
        if (requestedQuestions.size() != part.numberOfItems()) {
            throw invalid("parts.numberOfItems",
                    "numberOfItems must equal the actual question count in every part.");
        }
        Set<Integer> itemNumbers = new LinkedHashSet<>();
        List<PreparedQuestion> questions = new ArrayList<>();
        for (V3AssessmentQuestionRequest question : requestedQuestions) {
            if (question == null) {
                throw invalid("parts.questions", "Questions cannot contain null entries.");
            }
            if (!itemNumbers.add(question.itemNumber())) {
                throw invalid("parts.questions.itemNumber",
                        "Each item number must be unique within its part.");
            }
            questions.add(prepareQuestion(
                    user,
                    assignment,
                    questionType,
                    question,
                    pointsPerItem,
                    inlineRubricNames
            ));
        }
        requireContiguous(itemNumbers, part.numberOfItems(), "parts.questions.itemNumber",
                "Item numbers within each part");

        List<PreparedMapping> mappings = prepareMappings(part.skillMappings(), part.numberOfItems());
        boolean[] covered = new boolean[part.numberOfItems() + 1];
        for (PreparedMapping mapping : mappings) {
            for (int item = mapping.startItemNumber(); item <= mapping.endItemNumber(); item++) {
                covered[item] = true;
            }
        }
        for (int item = 1; item <= part.numberOfItems(); item++) {
            if (!covered[item]) {
                throw invalid("parts.skillMappings",
                        "Every item must be covered by at least one skill range. Missing local item " + item + ".");
            }
        }

        questions.sort(Comparator.comparingInt(PreparedQuestion::itemNumber));
        return new PreparedPart(
                part.partOrder(),
                partName,
                questionType,
                part.numberOfItems(),
                pointsPerItem,
                partInstructions,
                List.copyOf(questions),
                mappings
        );
    }

    private PreparedQuestion prepareQuestion(
            V3AuthenticatedUser user,
            AssignmentContext assignment,
            QuestionType type,
            V3AssessmentQuestionRequest question,
            BigDecimal defaultPoints,
            Set<String> inlineRubricNames
    ) {
        String questionText = requiredTrimmed(question.questionText(),
                "parts.questions.questionText", 10_000);
        BigDecimal maximumPoints = validPoints(
                question.maximumPoints() == null ? defaultPoints : question.maximumPoints(),
                "parts.questions.maximumPoints"
        );
        String responseInstructions = optionalTrimmed(
                question.responseInstructions(), 5_000, "parts.questions.responseInstructions");
        String code = type.code();

        return switch (code) {
            case "multiple_choice" -> prepareMultipleChoice(
                    question, type, questionText, maximumPoints, responseInstructions);
            case "true_false" -> prepareTrueFalse(
                    question, type, questionText, maximumPoints, responseInstructions);
            case "identification" -> prepareIdentification(
                    question, type, questionText, maximumPoints, responseInstructions);
            case "enumeration" -> prepareEnumeration(
                    question, type, questionText, maximumPoints, responseInstructions);
            case "essay" -> prepareEssay(
                    user, assignment, question, type, questionText, maximumPoints,
                    responseInstructions, inlineRubricNames);
            default -> throw invalid("parts.questionTypeCode", "Unsupported V3 question type: " + code);
        };
    }

    private PreparedQuestion prepareMultipleChoice(
            V3AssessmentQuestionRequest question,
            QuestionType type,
            String questionText,
            BigDecimal maximumPoints,
            String responseInstructions
    ) {
        rejectWrittenOnlyFields(question, "multiple_choice");
        List<PreparedOption> options = prepareOptions(question.options(), MC_KEYS, "multiple_choice");
        String correctKey = normalizeOptionKey(question.correctOptionKey());
        if (!MC_KEYS.contains(correctKey)) {
            throw invalid("parts.questions.correctOptionKey",
                    "Multiple Choice correctOptionKey must be A, B, C, or D.");
        }
        return baseQuestion(
                question,
                type,
                questionText,
                maximumPoints,
                responseInstructions,
                false,
                null,
                null,
                "none",
                false,
                options,
                new PreparedAnswerKey("option", correctKey, "exact", null,
                        optionalTrimmed(question.answerExplanation(), 10_000,
                                "parts.questions.answerExplanation")),
                List.of(),
                null
        );
    }

    private PreparedQuestion prepareTrueFalse(
            V3AssessmentQuestionRequest question,
            QuestionType type,
            String questionText,
            BigDecimal maximumPoints,
            String responseInstructions
    ) {
        rejectWrittenOnlyFields(question, "true_false");
        if (!safeList(question.options()).isEmpty()) {
            throw invalid("parts.questions.options",
                    "True/False options are generated by the backend and must be omitted.");
        }
        String correctKey = normalizeOptionKey(question.correctOptionKey());
        if (!TF_KEYS.contains(correctKey)) {
            throw invalid("parts.questions.correctOptionKey",
                    "True/False stores A=True and B=False; correctOptionKey must be A or B.");
        }
        List<PreparedOption> options = List.of(
                new PreparedOption("A", "True", 1),
                new PreparedOption("B", "False", 2)
        );
        return baseQuestion(
                question,
                type,
                questionText,
                maximumPoints,
                responseInstructions,
                false,
                null,
                null,
                "none",
                false,
                options,
                new PreparedAnswerKey("option", correctKey, "exact", null,
                        optionalTrimmed(question.answerExplanation(), 10_000,
                                "parts.questions.answerExplanation")),
                List.of(),
                null
        );
    }

    private PreparedQuestion prepareIdentification(
            V3AssessmentQuestionRequest question,
            QuestionType type,
            String questionText,
            BigDecimal maximumPoints,
            String responseInstructions
    ) {
        rejectObjectiveAndRubricFields(question, "identification");
        if (question.expectedResponseCount() != null && question.expectedResponseCount() != 1) {
            throw invalid("parts.questions.expectedResponseCount",
                    "Identification expects exactly one response.");
        }
        String matchingMode = normalizeMatchingMode(question.matchingMode());
        List<PreparedAcceptedAnswer> answers = prepareIdentificationAnswers(
                question.acceptedAnswers(), maximumPoints, matchingMode);
        return baseQuestion(
                question,
                type,
                questionText,
                maximumPoints,
                responseInstructions,
                false,
                question.maximumResponseLength(),
                1,
                writtenRegion(question.responseRegionSize(), "short", false),
                Boolean.TRUE.equals(question.forcePageBreakBefore()),
                List.of(),
                new PreparedAnswerKey("accepted_text", null, matchingMode, null,
                        optionalTrimmed(question.answerExplanation(), 10_000,
                                "parts.questions.answerExplanation")),
                answers,
                null
        );
    }

    private PreparedQuestion prepareEnumeration(
            V3AssessmentQuestionRequest question,
            QuestionType type,
            String questionText,
            BigDecimal maximumPoints,
            String responseInstructions
    ) {
        rejectObjectiveAndRubricFields(question, "enumeration");
        if (question.expectedResponseCount() == null || question.expectedResponseCount() < 1) {
            throw invalid("parts.questions.expectedResponseCount",
                    "Enumeration requires expectedResponseCount of at least one.");
        }
        String matchingMode = normalizeMatchingMode(question.matchingMode());
        List<PreparedAcceptedAnswer> answers = prepareEnumerationAnswers(
                question.acceptedAnswers(),
                question.expectedResponseCount(),
                maximumPoints,
                matchingMode
        );
        return baseQuestion(
                question,
                type,
                questionText,
                maximumPoints,
                responseInstructions,
                true,
                question.maximumResponseLength(),
                question.expectedResponseCount(),
                writtenRegion(question.responseRegionSize(), "medium", false),
                Boolean.TRUE.equals(question.forcePageBreakBefore()),
                List.of(),
                new PreparedAnswerKey("accepted_text", null, matchingMode, null,
                        optionalTrimmed(question.answerExplanation(), 10_000,
                                "parts.questions.answerExplanation")),
                answers,
                null
        );
    }

    private PreparedQuestion prepareEssay(
            V3AuthenticatedUser user,
            AssignmentContext assignment,
            V3AssessmentQuestionRequest question,
            QuestionType type,
            String questionText,
            BigDecimal maximumPoints,
            String responseInstructions,
            Set<String> inlineRubricNames
    ) {
        if (!safeList(question.options()).isEmpty()
                || question.correctOptionKey() != null
                || !safeList(question.acceptedAnswers()).isEmpty()
                || question.matchingMode() != null
                || question.expectedResponseCount() != null) {
            throw invalid("parts.questions",
                    "Essay questions cannot contain objective or accepted-text answer fields.");
        }
        if (question.rubricId() != null && question.rubric() != null) {
            throw invalid("parts.questions.rubric",
                    "Use either rubricId or an inline rubric, not both.");
        }

        PreparedRubric rubric = null;
        if (question.rubricId() != null) {
            RubricRow existing = repository.findUsableRubric(
                            question.rubricId(), user.schoolId(), user.userId())
                    .orElseThrow(() -> invalid("parts.questions.rubricId",
                            "The rubric does not exist, is archived, or belongs to another school."));
            if (existing.totalPoints().compareTo(maximumPoints) != 0) {
                throw invalid("parts.questions.rubricId",
                        "Rubric total points must equal the essay maximumPoints.");
            }
            rubric = PreparedRubric.existing(existing.rubricId());
        } else if (question.rubric() != null) {
            rubric = prepareInlineRubric(question.rubric(), maximumPoints, inlineRubricNames);
        }

        return baseQuestion(
                question,
                type,
                questionText,
                maximumPoints,
                responseInstructions,
                false,
                question.maximumResponseLength(),
                null,
                writtenRegion(question.responseRegionSize(), "long", true),
                Boolean.TRUE.equals(question.forcePageBreakBefore()),
                List.of(),
                new PreparedAnswerKey(
                        rubric == null ? "manual" : "rubric",
                        null,
                        rubric == null ? "manual" : "rubric",
                        rubric,
                        optionalTrimmed(question.answerExplanation(), 10_000,
                                "parts.questions.answerExplanation")
                ),
                List.of(),
                rubric
        );
    }

    private PreparedQuestion baseQuestion(
            V3AssessmentQuestionRequest source,
            QuestionType type,
            String questionText,
            BigDecimal maximumPoints,
            String responseInstructions,
            boolean answerOrderRequired,
            Integer maximumResponseLength,
            Integer expectedResponseCount,
            String responseRegionSize,
            boolean forcePageBreakBefore,
            List<PreparedOption> options,
            PreparedAnswerKey answerKey,
            List<PreparedAcceptedAnswer> acceptedAnswers,
            PreparedRubric rubric
    ) {
        return new PreparedQuestion(
                source.itemNumber(),
                questionText,
                type,
                maximumPoints,
                responseInstructions,
                answerOrderRequired,
                maximumResponseLength,
                expectedResponseCount,
                responseRegionSize,
                forcePageBreakBefore,
                options,
                answerKey,
                acceptedAnswers,
                rubric
        );
    }

    private List<PreparedOption> prepareOptions(
            List<V3QuestionOptionRequest> requested,
            List<String> expectedKeys,
            String typeCode
    ) {
        List<V3QuestionOptionRequest> options = safeList(requested);
        if (options.size() != expectedKeys.size()) {
            throw invalid("parts.questions.options",
                    typeCode + " requires exactly " + expectedKeys.size() + " options.");
        }
        Map<Integer, PreparedOption> byOrder = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        for (V3QuestionOptionRequest option : options) {
            if (option == null) {
                throw invalid("parts.questions.options", "Question options cannot contain null entries.");
            }
            String key = normalizeOptionKey(option.optionKey());
            if (!keys.add(key) || !expectedKeys.contains(key)) {
                throw invalid("parts.questions.options.optionKey",
                        "Option keys must be unique and exactly " + expectedKeys + ".");
            }
            PreparedOption prepared = new PreparedOption(
                    key,
                    requiredTrimmed(option.optionText(), "parts.questions.options.optionText", 2_000),
                    option.optionOrder()
            );
            if (byOrder.put(option.optionOrder(), prepared) != null) {
                throw invalid("parts.questions.options.optionOrder", "Option orders must be unique.");
            }
        }
        requireContiguous(byOrder.keySet(), expectedKeys.size(),
                "parts.questions.options.optionOrder", "Option orders");
        List<PreparedOption> sorted = byOrder.values().stream()
                .sorted(Comparator.comparingInt(PreparedOption::optionOrder))
                .toList();
        List<String> orderedKeys = sorted.stream().map(PreparedOption::optionKey).toList();
        if (!orderedKeys.equals(expectedKeys)) {
            throw invalid("parts.questions.options",
                    "Option order must match keys " + expectedKeys + ".");
        }
        return sorted;
    }

    private List<PreparedAcceptedAnswer> prepareIdentificationAnswers(
            List<V3AcceptedAnswerRequest> requested,
            BigDecimal maximumPoints,
            String matchingMode
    ) {
        List<V3AcceptedAnswerRequest> answers = safeList(requested);
        if (answers.isEmpty()) {
            throw invalid("parts.questions.acceptedAnswers",
                    "Identification requires at least one accepted answer.");
        }
        List<PreparedAcceptedAnswer> prepared = new ArrayList<>();
        Set<String> normalizedValues = new HashSet<>();
        int primaryCount = 0;
        for (int index = 0; index < answers.size(); index++) {
            V3AcceptedAnswerRequest answer = answers.get(index);
            if (answer.answerOrder() != null) {
                throw invalid("parts.questions.acceptedAnswers.answerOrder",
                        "Identification answerOrder must be omitted.");
            }
            String text = requiredTrimmed(answer.acceptedText(),
                    "parts.questions.acceptedAnswers.acceptedText", 500);
            boolean caseSensitive = Boolean.TRUE.equals(answer.caseSensitive());
            String normalized = normalizeAcceptedText(text, matchingMode, caseSensitive);
            if (!normalizedValues.add(normalized)) {
                throw invalid("parts.questions.acceptedAnswers",
                        "Accepted answer variants must be unique after normalization.");
            }
            BigDecimal points = answer.points() == null ? maximumPoints : validNonNegativePoints(
                    answer.points(), "parts.questions.acceptedAnswers.points");
            if (points.compareTo(maximumPoints) != 0) {
                throw invalid("parts.questions.acceptedAnswers.points",
                        "Every Identification variant must award maximumPoints.");
            }
            boolean primary = Boolean.TRUE.equals(answer.primary());
            if (primary) {
                primaryCount++;
            }
            prepared.add(new PreparedAcceptedAnswer(
                    null, text, normalized, matchingMode, caseSensitive, points, primary));
        }
        if (primaryCount == 0) {
            PreparedAcceptedAnswer first = prepared.get(0);
            prepared.set(0, first.withPrimary(true));
        } else if (primaryCount > 1) {
            throw invalid("parts.questions.acceptedAnswers.primary",
                    "Identification must have exactly one primary accepted answer.");
        }
        return List.copyOf(prepared);
    }

    private List<PreparedAcceptedAnswer> prepareEnumerationAnswers(
            List<V3AcceptedAnswerRequest> requested,
            int expectedCount,
            BigDecimal maximumPoints,
            String matchingMode
    ) {
        List<V3AcceptedAnswerRequest> answers = safeList(requested);
        if (answers.isEmpty()) {
            throw invalid("parts.questions.acceptedAnswers",
                    "Enumeration requires accepted answers for every expected response.");
        }
        Map<Integer, List<V3AcceptedAnswerRequest>> byOrder = new LinkedHashMap<>();
        for (V3AcceptedAnswerRequest answer : answers) {
            if (answer == null || answer.answerOrder() == null
                    || answer.answerOrder() < 1 || answer.answerOrder() > expectedCount) {
                throw invalid("parts.questions.acceptedAnswers.answerOrder",
                        "Enumeration answerOrder must be between 1 and expectedResponseCount.");
            }
            if (answer.points() == null) {
                throw invalid("parts.questions.acceptedAnswers.points",
                        "Enumeration requires explicit points for every accepted answer variant.");
            }
            byOrder.computeIfAbsent(answer.answerOrder(), ignored -> new ArrayList<>()).add(answer);
        }
        requireContiguous(byOrder.keySet(), expectedCount,
                "parts.questions.acceptedAnswers.answerOrder", "Enumeration answer orders");

        List<PreparedAcceptedAnswer> prepared = new ArrayList<>();
        BigDecimal primaryTotal = BigDecimal.ZERO;
        for (int order = 1; order <= expectedCount; order++) {
            List<V3AcceptedAnswerRequest> variants = byOrder.get(order);
            Set<String> normalizedValues = new HashSet<>();
            BigDecimal orderPoints = null;
            int primaryCount = 0;
            int startIndex = prepared.size();
            for (V3AcceptedAnswerRequest answer : variants) {
                String text = requiredTrimmed(answer.acceptedText(),
                        "parts.questions.acceptedAnswers.acceptedText", 500);
                boolean caseSensitive = Boolean.TRUE.equals(answer.caseSensitive());
                String normalized = normalizeAcceptedText(text, matchingMode, caseSensitive);
                if (!normalizedValues.add(normalized)) {
                    throw invalid("parts.questions.acceptedAnswers",
                            "Enumeration variants must be unique within each answerOrder.");
                }
                BigDecimal points = validNonNegativePoints(
                        answer.points(), "parts.questions.acceptedAnswers.points");
                if (orderPoints == null) {
                    orderPoints = points;
                } else if (orderPoints.compareTo(points) != 0) {
                    throw invalid("parts.questions.acceptedAnswers.points",
                            "All variants for one Enumeration answerOrder must award the same points.");
                }
                boolean primary = Boolean.TRUE.equals(answer.primary());
                if (primary) {
                    primaryCount++;
                }
                prepared.add(new PreparedAcceptedAnswer(
                        order, text, normalized, matchingMode, caseSensitive, points, primary));
            }
            if (primaryCount == 0) {
                PreparedAcceptedAnswer first = prepared.get(startIndex);
                prepared.set(startIndex, first.withPrimary(true));
            } else if (primaryCount > 1) {
                throw invalid("parts.questions.acceptedAnswers.primary",
                        "Each Enumeration answerOrder must have exactly one primary answer.");
            }
            primaryTotal = primaryTotal.add(Objects.requireNonNull(orderPoints));
        }
        if (primaryTotal.compareTo(maximumPoints) != 0) {
            throw invalid("parts.questions.acceptedAnswers.points",
                    "Enumeration points across answer orders must total maximumPoints.");
        }
        return List.copyOf(prepared);
    }

    private PreparedRubric prepareInlineRubric(
            V3RubricRequest request,
            BigDecimal maximumPoints,
            Set<String> inlineRubricNames
    ) {
        String rubricName = requiredTrimmed(request.rubricName(),
                "parts.questions.rubric.rubricName", 120);
        String normalizedName = rubricName.toLowerCase(Locale.ROOT);
        if (!inlineRubricNames.add(normalizedName)) {
            throw invalid("parts.questions.rubric.rubricName",
                    "Inline rubric names must be unique in one request. Use rubricId to reuse a rubric.");
        }
        List<V3RubricCriterionRequest> criteria = safeList(request.criteria());
        if (criteria.isEmpty()) {
            throw invalid("parts.questions.rubric.criteria", "A rubric requires at least one criterion.");
        }
        Set<Integer> orders = new LinkedHashSet<>();
        List<PreparedRubricCriterion> prepared = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (V3RubricCriterionRequest criterion : criteria) {
            if (criterion == null || !orders.add(criterion.criterionOrder())) {
                throw invalid("parts.questions.rubric.criteria.criterionOrder",
                        "Rubric criterion orders must be unique.");
            }
            BigDecimal points = validPoints(
                    criterion.maximumPoints(), "parts.questions.rubric.criteria.maximumPoints");
            total = total.add(points);
            prepared.add(new PreparedRubricCriterion(
                    criterion.criterionOrder(),
                    requiredTrimmed(criterion.criterionName(),
                            "parts.questions.rubric.criteria.criterionName", 120),
                    requiredTrimmed(criterion.criterionDescription(),
                            "parts.questions.rubric.criteria.criterionDescription", 10_000),
                    points,
                    serializeJson(criterion.levelDefinition()),
                    criterion.required() == null || criterion.required()
            ));
        }
        requireContiguous(orders, criteria.size(),
                "parts.questions.rubric.criteria.criterionOrder", "Rubric criterion orders");
        if (total.compareTo(maximumPoints) != 0) {
            throw invalid("parts.questions.rubric.criteria.maximumPoints",
                    "Rubric criterion points must total the essay maximumPoints.");
        }
        prepared.sort(Comparator.comparingInt(PreparedRubricCriterion::criterionOrder));
        return PreparedRubric.inline(
                rubricName,
                optionalTrimmed(request.description(), 10_000,
                        "parts.questions.rubric.description"),
                total,
                List.copyOf(prepared)
        );
    }

    private List<PreparedMapping> prepareMappings(
            List<V3PartSkillMappingRequest> requested,
            int numberOfItems
    ) {
        List<V3PartSkillMappingRequest> mappings = safeList(requested);
        if (mappings.isEmpty()) {
            throw invalid("parts.skillMappings", "Every test part requires at least one skill mapping.");
        }
        Set<String> uniqueRanges = new HashSet<>();
        List<PreparedMapping> prepared = new ArrayList<>();
        for (V3PartSkillMappingRequest mapping : mappings) {
            if (mapping == null || mapping.startItemNumber() > mapping.endItemNumber()
                    || mapping.endItemNumber() > numberOfItems) {
                throw invalid("parts.skillMappings",
                        "Skill ranges must stay within local item numbers 1 through numberOfItems.");
            }
            Set<Long> skillIds = new LinkedHashSet<>(safeList(mapping.skillIds()));
            if (skillIds.isEmpty() || skillIds.contains(null)) {
                throw invalid("parts.skillMappings.skillIds", "Every skill range requires valid skill IDs.");
            }
            for (Long skillId : skillIds) {
                String key = mapping.startItemNumber() + ":" + mapping.endItemNumber() + ":" + skillId;
                if (!uniqueRanges.add(key)) {
                    throw invalid("parts.skillMappings",
                            "Duplicate part-skill range mappings are not allowed.");
                }
            }
            prepared.add(new PreparedMapping(
                    mapping.startItemNumber(), mapping.endItemNumber(), Set.copyOf(skillIds)));
        }
        return List.copyOf(prepared);
    }

    private void insertAssessmentGraph(V3AuthenticatedUser user, long testId, PreparedAssessment assessment) {
        for (PreparedPart part : assessment.parts()) {
            long testPartId = repository.insertTestPart(
                    testId,
                    part.partOrder(),
                    part.partName(),
                    part.questionType().questionTypeId(),
                    part.numberOfItems(),
                    part.pointsPerItem(),
                    part.partInstructions()
            );
            for (PreparedQuestion question : part.questions()) {
                Long rubricId = persistRubricIfNeeded(user, question.rubric());
                long questionId = repository.insertQuestion(
                        UUID.randomUUID().toString(),
                        testPartId,
                        question.questionType().questionTypeId(),
                        question.itemNumber(),
                        question.questionText(),
                        question.maximumPoints(),
                        rubricId,
                        question.responseInstructions(),
                        question.answerOrderRequired(),
                        question.maximumResponseLength(),
                        question.expectedResponseCount(),
                        question.responseRegionSize(),
                        question.forcePageBreakBefore()
                );

                Map<String, Long> optionIds = new HashMap<>();
                for (PreparedOption option : question.options()) {
                    optionIds.put(option.optionKey(), repository.insertQuestionOption(
                            questionId, option.optionKey(), option.optionText(), option.optionOrder()));
                }
                PreparedAnswerKey answerKey = question.answerKey();
                Long keyRubricId = answerKey.rubric() == null ? null : rubricId;
                repository.insertAnswerKey(
                        questionId,
                        answerKey.answerKeyType(),
                        answerKey.correctOptionKey() == null
                                ? null
                                : optionIds.get(answerKey.correctOptionKey()),
                        answerKey.scoringMethod(),
                        keyRubricId,
                        answerKey.answerExplanation()
                );
                for (PreparedAcceptedAnswer accepted : question.acceptedAnswers()) {
                    repository.insertAcceptedAnswer(
                            questionId,
                            accepted.answerOrder(),
                            accepted.acceptedText(),
                            accepted.normalizedText(),
                            accepted.matchingMode(),
                            accepted.caseSensitive(),
                            accepted.points(),
                            accepted.primary()
                    );
                }
            }
            for (PreparedMapping mapping : part.mappings()) {
                for (Long skillId : mapping.skillIds()) {
                    repository.insertPartSkillMapping(
                            testPartId,
                            skillId,
                            mapping.startItemNumber(),
                            mapping.endItemNumber()
                    );
                }
            }
        }
    }

    private Long persistRubricIfNeeded(V3AuthenticatedUser user, PreparedRubric rubric) {
        if (rubric == null) {
            return null;
        }
        if (rubric.existingRubricId() != null) {
            return rubric.existingRubricId();
        }
        long rubricId = repository.insertRubric(
                UUID.randomUUID().toString(),
                user.schoolId(),
                user.userId(),
                rubric.rubricName(),
                rubric.description(),
                rubric.totalPoints()
        );
        for (PreparedRubricCriterion criterion : rubric.criteria()) {
            repository.insertRubricCriterion(
                    rubricId,
                    criterion.criterionOrder(),
                    criterion.criterionName(),
                    criterion.criterionDescription(),
                    criterion.maximumPoints(),
                    criterion.levelDefinition(),
                    criterion.required()
            );
        }
        return rubricId;
    }

    private V3AssessmentResponse requireAssessment(V3AuthenticatedUser user, long testId) {
        AssessmentHeader header = requireOwnedHeader(user, testId);
        List<PartRow> parts = repository.findParts(testId);
        List<QuestionRow> questions = repository.findQuestions(testId);
        Map<Long, List<QuestionRow>> questionsByPart = questions.stream()
                .collect(Collectors.groupingBy(
                        QuestionRow::testPartId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<OptionRow>> optionsByQuestion = repository.findOptions(testId).stream()
                .collect(Collectors.groupingBy(
                        OptionRow::questionId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, AnswerKeyRow> answerKeys = repository.findAnswerKeys(testId).stream()
                .collect(Collectors.toMap(
                        AnswerKeyRow::questionId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, List<AcceptedAnswerRow>> acceptedByQuestion = repository.findAcceptedAnswers(testId).stream()
                .collect(Collectors.groupingBy(
                        AcceptedAnswerRow::questionId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<SkillMappingRow>> mappingsByPart = repository.findSkillMappings(testId).stream()
                .collect(Collectors.groupingBy(
                        SkillMappingRow::testPartId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, RubricRow> rubricRows = repository.findRubricsForTest(testId).stream()
                .collect(Collectors.toMap(RubricRow::rubricId, Function.identity()));
        Map<Long, List<RubricCriterionRow>> criteriaByRubric = repository
                .findRubricCriteriaForTest(testId).stream()
                .collect(Collectors.groupingBy(
                        RubricCriterionRow::rubricId, LinkedHashMap::new, Collectors.toList()));

        List<V3AssessmentResponse.Part> partResponses = new ArrayList<>();
        BigDecimal maximumScore = BigDecimal.ZERO;
        for (PartRow part : parts) {
            List<V3AssessmentResponse.Question> questionResponses = new ArrayList<>();
            for (QuestionRow question : questionsByPart.getOrDefault(part.testPartId(), List.of())) {
                maximumScore = maximumScore.add(question.maximumPoints());
                AnswerKeyRow key = answerKeys.get(question.questionId());
                RubricRow rubric = question.rubricId() == null ? null : rubricRows.get(question.rubricId());
                questionResponses.add(new V3AssessmentResponse.Question(
                        question.questionId(),
                        question.questionUuid(),
                        question.itemNumber(),
                        question.questionText(),
                        question.maximumPoints(),
                        question.responseInstructions(),
                        question.maximumResponseLength(),
                        question.expectedResponseCount(),
                        question.responseRegionSize(),
                        question.forcePageBreakBefore(),
                        question.answerOrderRequired(),
                        optionsByQuestion.getOrDefault(question.questionId(), List.of()).stream()
                                .map(option -> new V3AssessmentResponse.Option(
                                        option.questionOptionId(),
                                        option.optionKey(),
                                        option.optionText(),
                                        option.optionOrder()
                                )).toList(),
                        key == null ? null : new V3AssessmentResponse.AnswerKey(
                                key.answerKeyType(),
                                key.correctOptionKey(),
                                key.scoringMethod(),
                                key.answerExplanation()
                        ),
                        acceptedByQuestion.getOrDefault(question.questionId(), List.of()).stream()
                                .map(answer -> new V3AssessmentResponse.AcceptedAnswer(
                                        answer.acceptedAnswerId(),
                                        answer.answerOrder(),
                                        answer.acceptedText(),
                                        answer.matchingMode(),
                                        answer.caseSensitive(),
                                        answer.points(),
                                        answer.primary()
                                )).toList(),
                        rubric == null ? null : toRubricResponse(
                                rubric, criteriaByRubric.getOrDefault(rubric.rubricId(), List.of()))
                ));
            }
            partResponses.add(new V3AssessmentResponse.Part(
                    part.testPartId(),
                    part.partOrder(),
                    part.partName(),
                    part.questionTypeCode(),
                    part.questionTypeName(),
                    part.numberOfItems(),
                    part.pointsPerItem(),
                    part.partInstructions(),
                    List.copyOf(questionResponses),
                    mappingsByPart.getOrDefault(part.testPartId(), List.of()).stream()
                            .map(mapping -> new V3AssessmentResponse.SkillMapping(
                                    mapping.partSkillMappingId(),
                                    mapping.skillId(),
                                    mapping.rootTagName(),
                                    mapping.competencyName(),
                                    mapping.startItemNumber(),
                                    mapping.endItemNumber(),
                                    mapping.itemCount()
                            )).toList()
            ));
        }

        return new V3AssessmentResponse(
                header.testId(),
                header.testUuid(),
                header.testAssignmentId(),
                header.assignmentUuid(),
                header.classAssignmentId(),
                header.termPeriodId(),
                header.termName(),
                header.testName(),
                header.testType(),
                header.instructions(),
                header.totalItems(),
                maximumScore,
                header.status(),
                header.versionNumber(),
                header.openAt(),
                header.closeAt(),
                header.assignmentStatus(),
                header.allowLateCapture(),
                header.outsideScheduleConfirmed(),
                header.outsideScheduleReason(),
                header.outsideScheduleConfirmedByUserId(),
                header.outsideScheduleConfirmedAt(),
                new V3AssessmentResponse.AssignmentContext(
                        header.classId(),
                        header.academicYearId(),
                        header.academicYearName(),
                        header.gradeLevelId(),
                        header.gradeLevelName(),
                        header.sectionName(),
                        header.subjectId(),
                        header.subjectName()
                ),
                List.copyOf(partResponses),
                header.createdAt(),
                header.updatedAt()
        );
    }

    private V3AssessmentResponse.Rubric toRubricResponse(
            RubricRow rubric,
            List<RubricCriterionRow> criteria
    ) {
        return new V3AssessmentResponse.Rubric(
                rubric.rubricId(),
                rubric.rubricUuid(),
                rubric.rubricName(),
                rubric.description(),
                rubric.totalPoints(),
                rubric.status(),
                criteria.stream().map(criterion -> new V3AssessmentResponse.RubricCriterion(
                        criterion.rubricCriterionId(),
                        criterion.criterionOrder(),
                        criterion.criterionName(),
                        criterion.criterionDescription(),
                        criterion.maximumPoints(),
                        criterion.levelDefinition(),
                        criterion.required()
                )).toList()
        );
    }

    private void validateActivationReadiness(V3AssessmentResponse assessment) {
        if (assessment.parts().isEmpty() || assessment.totalItems() < 1) {
            throw conflict("ASSESSMENT_INCOMPLETE", "The assessment has no questions.");
        }
        int actualTotal = 0;
        for (V3AssessmentResponse.Part part : assessment.parts()) {
            if (part.questions().size() != part.numberOfItems()) {
                throw conflict("ASSESSMENT_SNAPSHOT_MISMATCH",
                        "A test-part item snapshot does not match its questions.");
            }
            actualTotal += part.questions().size();
            boolean[] covered = new boolean[part.numberOfItems() + 1];
            for (V3AssessmentResponse.SkillMapping mapping : part.skillMappings()) {
                if (mapping.startItemNumber() < 1
                        || mapping.endItemNumber() > part.numberOfItems()
                        || mapping.itemCount() != mapping.endItemNumber() - mapping.startItemNumber() + 1) {
                    throw conflict("ASSESSMENT_MAPPING_INVALID", "A part-skill mapping range is invalid.");
                }
                for (int item = mapping.startItemNumber(); item <= mapping.endItemNumber(); item++) {
                    covered[item] = true;
                }
            }
            for (int item = 1; item <= part.numberOfItems(); item++) {
                if (!covered[item]) {
                    throw conflict("ASSESSMENT_MAPPING_INCOMPLETE",
                            "Every assessment item requires at least one skill mapping.");
                }
            }
            for (V3AssessmentResponse.Question question : part.questions()) {
                validateStoredQuestion(part.questionTypeCode(), question);
            }
        }
        if (actualTotal != assessment.totalItems()) {
            throw conflict("ASSESSMENT_SNAPSHOT_MISMATCH",
                    "The assessment total-items snapshot does not match its questions.");
        }
    }

    private void validateStoredQuestion(String type, V3AssessmentResponse.Question question) {
        if (question.answerKey() == null) {
            throw conflict("ASSESSMENT_ANSWER_KEY_INCOMPLETE",
                    "Every question requires exactly one answer key.");
        }
        switch (type) {
            case "multiple_choice" -> {
                if (!question.options().stream().map(V3AssessmentResponse.Option::optionKey).toList().equals(MC_KEYS)
                        || !"option".equals(question.answerKey().answerKeyType())
                        || !MC_KEYS.contains(question.answerKey().correctOptionKey())) {
                    throw conflict("ASSESSMENT_MULTIPLE_CHOICE_INVALID",
                            "Multiple Choice questions require A-D options and one valid key.");
                }
            }
            case "true_false" -> {
                List<String> keys = question.options().stream()
                        .map(V3AssessmentResponse.Option::optionKey).toList();
                if (!keys.equals(TF_KEYS)
                        || !"option".equals(question.answerKey().answerKeyType())
                        || !TF_KEYS.contains(question.answerKey().correctOptionKey())) {
                    throw conflict("ASSESSMENT_TRUE_FALSE_INVALID",
                            "True/False questions require A=True, B=False, and one valid key.");
                }
            }
            case "identification" -> {
                if (question.expectedResponseCount() == null
                        || question.expectedResponseCount() != 1
                        || question.acceptedAnswers().isEmpty()
                        || !"accepted_text".equals(question.answerKey().answerKeyType())) {
                    throw conflict("ASSESSMENT_IDENTIFICATION_INVALID",
                            "Identification requires accepted answers and one expected response.");
                }
            }
            case "enumeration" -> {
                if (question.expectedResponseCount() == null
                        || question.expectedResponseCount() < 1
                        || question.acceptedAnswers().isEmpty()
                        || !"accepted_text".equals(question.answerKey().answerKeyType())) {
                    throw conflict("ASSESSMENT_ENUMERATION_INVALID",
                            "Enumeration requires ordered accepted answers.");
                }
                Set<Integer> orders = question.acceptedAnswers().stream()
                        .map(V3AssessmentResponse.AcceptedAnswer::answerOrder)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (orders.size() != question.expectedResponseCount()) {
                    throw conflict("ASSESSMENT_ENUMERATION_INVALID",
                            "Enumeration accepted-answer orders are incomplete.");
                }
            }
            case "essay" -> {
                if (question.rubric() == null
                        && !"manual".equals(question.answerKey().answerKeyType())) {
                    throw conflict("ASSESSMENT_ESSAY_INVALID",
                            "An Essay without a rubric must use manual scoring.");
                }
                if (question.rubric() != null
                        && (!"rubric".equals(question.answerKey().answerKeyType())
                        || question.rubric().totalPoints().compareTo(question.maximumPoints()) != 0)) {
                    throw conflict("ASSESSMENT_ESSAY_RUBRIC_INVALID",
                            "Essay rubric points must equal the question maximum points.");
                }
            }
            default -> throw conflict("ASSESSMENT_QUESTION_TYPE_INVALID",
                    "The assessment contains an unsupported question type.");
        }
    }

    private AssignmentContext requireOwnedActiveAssignment(V3AuthenticatedUser user, long classAssignmentId) {
        AssignmentContext assignment = requireOwnedActiveOrHistoricalAssignment(user, classAssignmentId);
        if (!"active".equals(assignment.assignmentStatus()) || !"active".equals(assignment.classStatus())) {
            throw conflict("CLASS_ASSIGNMENT_INACTIVE",
                    "Assessment creation requires an active class assignment and class.");
        }
        return assignment;
    }

    private AssignmentContext requireOwnedActiveOrHistoricalAssignment(
            V3AuthenticatedUser user,
            long classAssignmentId
    ) {
        AssignmentContext assignment = repository.findAssignmentContext(classAssignmentId)
                .orElseThrow(() -> notFound("CLASS_ASSIGNMENT_NOT_FOUND", "Class assignment was not found."));
        if (assignment.teacherUserId() != user.userId()
                || !assignment.schoolId().equals(user.schoolId())) {
            throw forbidden("CLASS_ASSIGNMENT_FORBIDDEN",
                    "The class assignment does not belong to the authenticated teacher.");
        }
        return assignment;
    }

    private AssessmentHeader requireOwnedHeader(V3AuthenticatedUser user, long testId) {
        AssessmentHeader header = repository.findHeader(testId)
                .orElseThrow(() -> notFound("ASSESSMENT_NOT_FOUND", "Assessment was not found."));
        if (header.createdByUserId() != user.userId()
                || !header.schoolId().equals(user.schoolId())) {
            throw forbidden("ASSESSMENT_FORBIDDEN",
                    "The assessment does not belong to the authenticated teacher.");
        }
        AssignmentContext assignment = requireOwnedActiveOrHistoricalAssignment(user, header.classAssignmentId());
        if (assignment.teacherUserId() != user.userId()) {
            throw forbidden("ASSESSMENT_FORBIDDEN",
                    "The assessment assignment does not belong to the authenticated teacher.");
        }
        return header;
    }

    private V3AssessmentReferenceDataResponse.AssignmentOption toAssignmentOption(AssignmentContext context) {
        return new V3AssessmentReferenceDataResponse.AssignmentOption(
                context.classAssignmentId(),
                context.classId(),
                context.academicYearId(),
                context.academicYearName(),
                context.gradeLevelId(),
                context.gradeLevelName(),
                context.sectionName(),
                context.subjectId(),
                context.subjectName(),
                context.assignmentRole(),
                context.assignmentStatus()
        );
    }

    private V3AssessmentReferenceDataResponse.QuestionTypeOption toQuestionTypeOption(QuestionType type) {
        return new V3AssessmentReferenceDataResponse.QuestionTypeOption(
                type.questionTypeId(),
                type.code(),
                type.name(),
                type.captureMode(),
                type.scoringMode(),
                type.supportsOmr(),
                type.supportsOcr(),
                type.supportsMultipleResponse(),
                type.requiresAttachment(),
                type.requiresTeacherVerification(),
                type.allowsTeacherAnswerEdit()
        );
    }

    private ScheduleDecision validateSchedule(
            V3AuthenticatedUser user,
            V3AssessmentRequest request,
            AssignmentContext assignment,
            TermWindow termWindow,
            Instant now
    ) {
        Instant openAt = request.openAt();
        Instant closeAt = request.closeAt();
        if (openAt != null && closeAt != null && !closeAt.isAfter(openAt)) {
            throw invalid("closeAt", "closeAt must be later than openAt.");
        }
        validateInsideTerm("openAt", openAt, termWindow);
        validateInsideTerm("closeAt", closeAt, termWindow);

        List<ClassScheduleWindow> schedules = repository.listActiveClassScheduleWindows(
                assignment.classAssignmentId()
        );
        if (closeAt == null || schedules.isEmpty() || isInsideClassSchedule(closeAt, schedules)) {
            return ScheduleDecision.none();
        }

        if (!Boolean.TRUE.equals(request.confirmOutsideClassSchedule())) {
            throw invalid(
                    "confirmOutsideClassSchedule",
                    "The close time is outside the teacher's active class timetable. Confirm the override and provide a reason."
            );
        }
        String reason = optionalTrimmed(
                request.outsideClassScheduleReason(),
                255,
                "outsideClassScheduleReason"
        );
        if (reason == null || reason.length() < 5) {
            throw invalid(
                    "outsideClassScheduleReason",
                    "A reason of at least 5 characters is required for an out-of-schedule close time."
            );
        }
        return new ScheduleDecision(true, reason, user.userId(), now);
    }

    private void validateInsideTerm(String field, Instant value, TermWindow termWindow) {
        if (value != null && (value.isBefore(termWindow.startAt()) || value.isAfter(termWindow.endAt()))) {
            throw invalid(field, "The assessment schedule must stay inside the selected term period.");
        }
    }

    private boolean isInsideClassSchedule(Instant closeAt, List<ClassScheduleWindow> schedules) {
        ZonedDateTime localClose = closeAt.atZone(SCHOOL_TIMEZONE);
        LocalDate date = localClose.toLocalDate();
        LocalTime time = localClose.toLocalTime();
        int dayOfWeek = localClose.getDayOfWeek().getValue();
        return schedules.stream().anyMatch(schedule ->
                "Asia/Manila".equals(schedule.timezoneName())
                        && schedule.dayOfWeek() == dayOfWeek
                        && !date.isBefore(schedule.effectiveFrom())
                        && (schedule.effectiveTo() == null || !date.isAfter(schedule.effectiveTo()))
                        && !time.isBefore(schedule.startTime())
                        && !time.isAfter(schedule.endTime())
        );
    }

    private void rejectWrittenOnlyFields(V3AssessmentQuestionRequest question, String type) {
        if (question.expectedResponseCount() != null
                || question.maximumResponseLength() != null
                || !safeList(question.acceptedAnswers()).isEmpty()
                || question.matchingMode() != null
                || question.rubricId() != null
                || question.rubric() != null
                || (question.responseRegionSize() != null
                && !"none".equals(normalize(question.responseRegionSize())))
                || Boolean.TRUE.equals(question.forcePageBreakBefore())) {
            throw invalid("parts.questions",
                    type + " cannot contain written-response or rubric fields.");
        }
    }

    private void rejectObjectiveAndRubricFields(V3AssessmentQuestionRequest question, String type) {
        if (!safeList(question.options()).isEmpty()
                || question.correctOptionKey() != null
                || question.rubricId() != null
                || question.rubric() != null) {
            throw invalid("parts.questions",
                    type + " cannot contain objective options, a correct option key, or a rubric.");
        }
    }

    private String writtenRegion(String requested, String defaultValue, boolean allowFullPage) {
        String normalized = requested == null || requested.isBlank() ? defaultValue : normalize(requested);
        if (!RESPONSE_REGION_SIZES.contains(normalized)
                || "none".equals(normalized)
                || (!allowFullPage && "full_page".equals(normalized))) {
            throw invalid("parts.questions.responseRegionSize",
                    "The response-region size is not valid for this written question type.");
        }
        return normalized;
    }

    private String normalizeMatchingMode(String value) {
        String normalized = value == null || value.isBlank() ? "normalized" : normalize(value);
        if (!TEXT_MATCHING_MODES.contains(normalized)) {
            throw invalid("parts.questions.matchingMode", "matchingMode must be exact or normalized.");
        }
        return normalized;
    }

    private String normalizeAcceptedText(String value, String matchingMode, boolean caseSensitive) {
        String normalized = value.trim();
        if ("normalized".equals(matchingMode)) {
            normalized = normalized.replaceAll("\\s+", " ");
        }
        return caseSensitive ? normalized : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAllowed(String value, Set<String> allowed, String field) {
        String normalized = normalize(value);
        if (!allowed.contains(normalized)) {
            throw invalid(field, "Unsupported value. Allowed values: " + allowed.stream().sorted().toList());
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String requiredTrimmed(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "This field is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw invalid(field, "This field may contain at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    private String optionalTrimmed(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw invalid(field, "This field may contain at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    private BigDecimal validPoints(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX_POINTS) > 0 || value.scale() > 2) {
            throw invalid(field, "Points must be positive, use at most two decimals, and stay within database limits.");
        }
        return value.setScale(Math.max(0, value.scale()), RoundingMode.UNNECESSARY);
    }

    private BigDecimal validNonNegativePoints(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_POINTS) > 0 || value.scale() > 2) {
            throw invalid(field, "Points cannot be negative and may use at most two decimals.");
        }
        return value;
    }

    private void requireContiguous(Set<Integer> values, int expectedCount, String field, String label) {
        if (values.size() != expectedCount) {
            throw invalid(field, label + " must be unique.");
        }
        for (int expected = 1; expected <= expectedCount; expected++) {
            if (!values.contains(expected)) {
                throw invalid(field, label + " must be contiguous starting at 1.");
            }
        }
    }

    private String serializeJson(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("parts.questions.rubric.criteria.levelDefinition",
                    "Rubric levelDefinition must be valid JSON.");
        }
    }

    private void requireTeacher(V3AuthenticatedUser user) {
        if (user == null) {
            throw new V3AuthException(
                    "AUTHENTICATION_REQUIRED",
                    "Authentication is required.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        if (!TEACHER_ROLE.equalsIgnoreCase(user.role())) {
            throw forbidden("TEACHER_ROLE_REQUIRED", "Only authenticated teachers may manage assessments.");
        }
        if (!"active".equalsIgnoreCase(user.status())
                || user.schoolId() == null
                || user.schoolId().isBlank()) {
            throw forbidden(
                    "ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active teacher account associated with a school is required."
            );
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private V3FieldValidationException invalid(String field, String message) {
        return new V3FieldValidationException(
                "Assessment validation failed.",
                HttpStatus.BAD_REQUEST,
                Map.of("code", "VALIDATION_FAILED", field, message)
        );
    }

    private V3AuthException conflict(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.CONFLICT);
    }

    private V3AuthException forbidden(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.FORBIDDEN);
    }

    private V3AuthException notFound(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.NOT_FOUND);
    }

    private record PreparedAssessment(
            AssignmentContext assignment,
            int termPeriodId,
            String testName,
            String testType,
            String instructions,
            Instant openAt,
            Instant closeAt,
            boolean allowLateCapture,
            boolean outsideScheduleConfirmed,
            String outsideScheduleReason,
            Long outsideScheduleConfirmedByUserId,
            Instant outsideScheduleConfirmedAt,
            List<PreparedPart> parts,
            int totalItems
    ) {
    }

    private record ScheduleDecision(
            boolean outsideScheduleConfirmed,
            String reason,
            Long confirmedByUserId,
            Instant confirmedAt
    ) {
        static ScheduleDecision none() {
            return new ScheduleDecision(false, null, null, null);
        }
    }

    private record PreparedPart(
            int partOrder,
            String partName,
            QuestionType questionType,
            int numberOfItems,
            BigDecimal pointsPerItem,
            String partInstructions,
            List<PreparedQuestion> questions,
            List<PreparedMapping> mappings
    ) {
    }

    private record PreparedQuestion(
            int itemNumber,
            String questionText,
            QuestionType questionType,
            BigDecimal maximumPoints,
            String responseInstructions,
            boolean answerOrderRequired,
            Integer maximumResponseLength,
            Integer expectedResponseCount,
            String responseRegionSize,
            boolean forcePageBreakBefore,
            List<PreparedOption> options,
            PreparedAnswerKey answerKey,
            List<PreparedAcceptedAnswer> acceptedAnswers,
            PreparedRubric rubric
    ) {
    }

    private record PreparedOption(String optionKey, String optionText, int optionOrder) {
    }

    private record PreparedAnswerKey(
            String answerKeyType,
            String correctOptionKey,
            String scoringMethod,
            PreparedRubric rubric,
            String answerExplanation
    ) {
    }

    private record PreparedAcceptedAnswer(
            Integer answerOrder,
            String acceptedText,
            String normalizedText,
            String matchingMode,
            boolean caseSensitive,
            BigDecimal points,
            boolean primary
    ) {
        PreparedAcceptedAnswer withPrimary(boolean newPrimary) {
            return new PreparedAcceptedAnswer(
                    answerOrder,
                    acceptedText,
                    normalizedText,
                    matchingMode,
                    caseSensitive,
                    points,
                    newPrimary
            );
        }
    }

    private record PreparedMapping(int startItemNumber, int endItemNumber, Set<Long> skillIds) {
    }

    private record PreparedRubric(
            Long existingRubricId,
            String rubricName,
            String description,
            BigDecimal totalPoints,
            List<PreparedRubricCriterion> criteria
    ) {
        static PreparedRubric existing(long rubricId) {
            return new PreparedRubric(rubricId, null, null, null, List.of());
        }

        static PreparedRubric inline(
                String name,
                String description,
                BigDecimal totalPoints,
                List<PreparedRubricCriterion> criteria
        ) {
            return new PreparedRubric(null, name, description, totalPoints, criteria);
        }
    }

    private record PreparedRubricCriterion(
            int criterionOrder,
            String criterionName,
            String criterionDescription,
            BigDecimal maximumPoints,
            String levelDefinition,
            boolean required
    ) {
    }
}
