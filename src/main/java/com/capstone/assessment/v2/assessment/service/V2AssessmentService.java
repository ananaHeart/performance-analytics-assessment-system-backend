package com.capstone.assessment.v2.assessment.service;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentPartRequest;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentPartResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentQuestionRequest;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentQuestionResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentReferenceDataResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentRequest;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentSummaryResponse;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Profile("v2")
@Service
public class V2AssessmentService {

    private static final String TEACHER_ROLE = "teacher";
    private static final Set<String> ALLOWED_TEST_TYPES = Set.of(
            "quiz", "exam", "diagnostic", "long_test", "other"
    );
    private static final Set<String> ALLOWED_PART_TYPES = Set.of("multiple_choice", "true_false");
    private static final Set<String> ALLOWED_OPTIONS = Set.of("A", "B", "C", "D", "E");

    private final V2AssessmentRepository assessmentRepository;
    private final V2AuthRepository authRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public V2AssessmentService(
            V2AssessmentRepository assessmentRepository,
            V2AuthRepository authRepository,
            ObjectMapper objectMapper
    ) {
        this(assessmentRepository, authRepository, objectMapper, Clock.systemUTC());
    }

    V2AssessmentService(
            V2AssessmentRepository assessmentRepository,
            V2AuthRepository authRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.assessmentRepository = assessmentRepository;
        this.authRepository = authRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public V2AssessmentReferenceDataResponse getReferenceData(
            V2AuthenticatedUser principal,
            Long classAssignmentId,
            Integer termPeriodId
    ) {
        requireTeacher(principal);
        List<com.capstone.assessment.v2.assessment.dto.V2AssessmentAssignmentOption> assignments =
                assessmentRepository.listActiveTeacherAssignments(principal.userId(), principal.schoolId());

        if (classAssignmentId == null) {
            return new V2AssessmentReferenceDataResponse(assignments, List.of(), List.of());
        }

        V2AssessmentAssignmentContext context = requireOwnedActiveAssignment(principal, classAssignmentId);
        List<com.capstone.assessment.v2.assessment.dto.V2AssessmentTermPeriodOption> terms =
                assessmentRepository.listTermPeriods(context.academicYearId());

        if (termPeriodId == null) {
            return new V2AssessmentReferenceDataResponse(assignments, terms, List.of());
        }
        requireTermMatchesAssignment(termPeriodId, context);
        return new V2AssessmentReferenceDataResponse(
                assignments,
                terms,
                assessmentRepository.listSkillsForContext(
                        termPeriodId,
                        context.gradeLevelId(),
                        context.subjectId()
                )
        );
    }

    @Transactional
    public V2AssessmentResponse createAssessment(
            V2AuthenticatedUser principal,
            V2AssessmentRequest request,
            V2RequestMetadata metadata
    ) {
        requireTeacher(principal);
        PreparedAssessment prepared = prepareAssessment(principal, request);

        try {
            long testId = assessmentRepository.insertTest(
                    prepared.assignment().classAssignmentId(),
                    prepared.termPeriodId(),
                    prepared.testName(),
                    prepared.testType(),
                    prepared.testDate(),
                    prepared.instructions(),
                    prepared.totalItems()
            );
            insertAssessmentGraph(testId, prepared);
            recordAudit(principal, metadata, "CREATE_ASSESSMENT", testId, Map.of(
                    "classAssignmentId", prepared.assignment().classAssignmentId(),
                    "termPeriodId", prepared.termPeriodId(),
                    "status", "draft",
                    "totalItems", prepared.totalItems()
            ));
            return requireAssessment(principal, testId);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ASSESSMENT_SAVE_CONFLICT", "The assessment conflicts with existing V2 data.");
        }
    }

    @Transactional(readOnly = true)
    public List<V2AssessmentSummaryResponse> listAssessments(
            V2AuthenticatedUser principal,
            Long classAssignmentId
    ) {
        requireTeacher(principal);
        if (classAssignmentId != null) {
            requireOwnedActiveAssignment(principal, classAssignmentId);
        }
        return assessmentRepository.listAssessments(principal.userId(), principal.schoolId(), classAssignmentId);
    }

    @Transactional(readOnly = true)
    public V2AssessmentResponse getAssessment(V2AuthenticatedUser principal, long testId) {
        requireTeacher(principal);
        return requireAssessment(principal, testId);
    }

    @Transactional
    public V2AssessmentResponse updateAssessment(
            V2AuthenticatedUser principal,
            long testId,
        V2AssessmentRequest request,
        V2RequestMetadata metadata
    ) {
        requireTeacher(principal);
        V2AssessmentHeader existing = requireOwnedHeader(principal, testId);
        assessmentRepository.lockTest(testId);
        existing = requireOwnedHeader(principal, testId);
        if (!"draft".equals(existing.status())) {
            throw conflict("ASSESSMENT_NOT_EDITABLE", "Only draft assessments may be edited.");
        }
        if (!existing.classAssignmentId().equals(request.classAssignmentId())) {
            throw badRequest(
                    "CLASS_ASSIGNMENT_CHANGE_NOT_ALLOWED",
                    "An assessment cannot be moved to another class assignment."
            );
        }

        PreparedAssessment prepared = prepareAssessment(principal, request);
        try {
            int updated = assessmentRepository.updateTestHeader(
                    testId,
                    prepared.termPeriodId(),
                    prepared.testName(),
                    prepared.testType(),
                    prepared.testDate(),
                    prepared.instructions(),
                    prepared.totalItems()
            );
            if (updated != 1) {
                throw conflict("ASSESSMENT_STATE_CHANGED", "The assessment state changed before this edit completed.");
            }
            assessmentRepository.deleteParts(testId);
            insertAssessmentGraph(testId, prepared);
            recordAudit(principal, metadata, "UPDATE_ASSESSMENT", testId, Map.of(
                    "classAssignmentId", prepared.assignment().classAssignmentId(),
                    "termPeriodId", prepared.termPeriodId(),
                    "status", "draft",
                    "totalItems", prepared.totalItems()
            ));
            return requireAssessment(principal, testId);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ASSESSMENT_SAVE_CONFLICT", "The assessment update conflicts with existing V2 data.");
        }
    }

    @Transactional
    public V2AssessmentResponse activateAssessment(
            V2AuthenticatedUser principal,
            long testId,
            V2RequestMetadata metadata
    ) {
        requireTeacher(principal);
        V2AssessmentHeader header = requireOwnedHeader(principal, testId);
        assessmentRepository.lockTest(testId);
        header = requireOwnedHeader(principal, testId);
        if (!"draft".equals(header.status())) {
            throw conflict("ASSESSMENT_NOT_ACTIVATABLE", "Only draft assessments may be activated.");
        }

        validateActivationReadiness(testId);
        assessmentRepository.updateStatus(testId, "active");
        recordAudit(principal, metadata, "ACTIVATE_ASSESSMENT", testId, Map.of(
                "previousStatus", "draft",
                "newStatus", "active"
        ));
        return requireAssessment(principal, testId);
    }

    @Transactional
    public V2AssessmentResponse archiveAssessment(
            V2AuthenticatedUser principal,
            long testId,
            V2RequestMetadata metadata
    ) {
        requireTeacher(principal);
        V2AssessmentHeader header = requireOwnedHeader(principal, testId);
        assessmentRepository.lockTest(testId);
        header = requireOwnedHeader(principal, testId);
        if ("archived".equals(header.status())) {
            throw conflict("ASSESSMENT_ALREADY_ARCHIVED", "This assessment is already archived.");
        }
        assessmentRepository.updateStatus(testId, "archived");
        recordAudit(principal, metadata, "ARCHIVE_ASSESSMENT", testId, Map.of(
                "previousStatus", header.status(),
                "newStatus", "archived"
        ));
        return requireAssessment(principal, testId);
    }

    private void insertAssessmentGraph(long testId, PreparedAssessment prepared) {
        for (PreparedPart part : prepared.parts()) {
            long testPartId = assessmentRepository.insertTestPart(
                    testId,
                    part.partOrder(),
                    part.partName(),
                    part.partType(),
                    part.questions().size(),
                    part.pointsPerItem()
            );
            for (PreparedQuestion question : part.questions()) {
                long questionId = assessmentRepository.insertQuestion(
                        testPartId,
                        question.itemNumber(),
                        question.questionText(),
                        question.optionA(),
                        question.optionB(),
                        question.optionC(),
                        question.optionD(),
                        question.optionE()
                );
                assessmentRepository.insertAnswerKey(questionId, question.correctOption());
                for (Long skillId : question.skillIds()) {
                    assessmentRepository.insertMapping(questionId, skillId);
                }
            }
        }
    }

    private PreparedAssessment prepareAssessment(
            V2AuthenticatedUser principal,
            V2AssessmentRequest request
    ) {
        if (request == null) {
            throw badRequest("ASSESSMENT_REQUEST_REQUIRED", "Assessment request is required.");
        }
        V2AssessmentAssignmentContext assignment =
                requireOwnedActiveAssignment(principal, requirePositive(request.classAssignmentId(), "CLASS_ASSIGNMENT_REQUIRED"));
        requirePositive(request.termPeriodId(), "TERM_PERIOD_REQUIRED");
        requireTermMatchesAssignment(request.termPeriodId(), assignment);

        String testType = normalizeAllowed(request.testType(), ALLOWED_TEST_TYPES, "INVALID_TEST_TYPE",
                "Test type must be quiz, exam, diagnostic, long_test, or other.");
        String testName = requiredTrimmed(request.testName(), "TEST_NAME_REQUIRED", "Test name is required.");
        String instructions = optionalTrimmed(request.instructions());
        if (request.testDate() == null) {
            throw badRequest("TEST_DATE_REQUIRED", "Test date is required.");
        }

        if (request.parts() == null || request.parts().isEmpty()) {
            throw badRequest("PARTS_REQUIRED", "At least one test part is required.");
        }

        List<PreparedPart> preparedParts = new ArrayList<>();
        Set<Integer> partOrders = new LinkedHashSet<>();
        Set<Long> allSkillIds = new LinkedHashSet<>();
        int totalItems = 0;

        for (V2AssessmentPartRequest part : request.parts()) {
            PreparedPart preparedPart = preparePart(part);
            if (!partOrders.add(preparedPart.partOrder())) {
                throw conflict("DUPLICATE_PART_ORDER", "Every test part must have a unique part order.");
            }
            totalItems += preparedPart.questions().size();
            preparedPart.questions().forEach(question -> allSkillIds.addAll(question.skillIds()));
            preparedParts.add(preparedPart);
        }

        Set<Long> validSkillIds = assessmentRepository.findValidSkillIds(
                allSkillIds,
                request.termPeriodId(),
                assignment.gradeLevelId(),
                assignment.subjectId()
        );
        if (!validSkillIds.containsAll(allSkillIds)) {
            throw badRequest(
                    "INVALID_SKILL_MAPPING",
                    "Every mapped skill must match the assessment term, grade level, subject, and active curriculum."
            );
        }

        return new PreparedAssessment(
                assignment,
                request.termPeriodId(),
                testName,
                testType,
                request.testDate(),
                instructions,
                totalItems,
                preparedParts
        );
    }

    private PreparedPart preparePart(V2AssessmentPartRequest part) {
        if (part == null) {
            throw badRequest("PART_REQUIRED", "Test part details are required.");
        }
        String partType = normalizeAllowed(part.partType(), ALLOWED_PART_TYPES, "INVALID_PART_TYPE",
                "Part type must be multiple_choice or true_false.");
        int partOrder = requirePositive(part.partOrder(), "PART_ORDER_REQUIRED");
        String partName = requiredTrimmed(part.partName(), "PART_NAME_REQUIRED", "Part name is required.");
        if (part.pointsPerItem() == null || part.pointsPerItem().compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("INVALID_POINTS_PER_ITEM", "Points per item must be greater than zero.");
        }
        if (part.questions() == null || part.questions().isEmpty()) {
            throw badRequest("QUESTIONS_REQUIRED", "Every test part must contain at least one question.");
        }

        Map<Integer, PreparedQuestion> questionsByItemNumber = new LinkedHashMap<>();
        Map<Integer, LinkedHashSet<Long>> skillIdsByItemNumber = new LinkedHashMap<>();

        for (V2AssessmentQuestionRequest question : part.questions()) {
            PreparedQuestion preparedQuestion = prepareQuestion(question, partType, List.of());
            if (questionsByItemNumber.putIfAbsent(preparedQuestion.itemNumber(), preparedQuestion) != null) {
                throw conflict(
                        "DUPLICATE_ITEM_NUMBER",
                        "Every question must have a unique item number within its part."
                );
            }
            skillIdsByItemNumber.put(
                    preparedQuestion.itemNumber(),
                    new LinkedHashSet<>(safeSkillIds(question.skillIds()))
            );
        }

        for (V2SkillRangeMappingRequest range : safeRanges(part.skillMappings())) {
            if (range == null) {
                throw badRequest("SKILL_MAPPING_REQUIRED", "Skill mapping details are required.");
            }
            int from = requirePositive(range.fromItemNumber(), "INVALID_MAPPING_RANGE");
            int to = requirePositive(range.toItemNumber(), "INVALID_MAPPING_RANGE");
            if (from > to) {
                throw badRequest("INVALID_MAPPING_RANGE", "Skill mapping range start cannot be after its end.");
            }
            List<Long> rangeSkillIds = safeSkillIds(range.skillIds());
            if (rangeSkillIds.isEmpty()) {
                throw badRequest("SKILL_MAPPING_REQUIRED", "A skill mapping range must contain at least one skill.");
            }
            for (int itemNumber = from; itemNumber <= to; itemNumber++) {
                LinkedHashSet<Long> targetSkillIds = skillIdsByItemNumber.get(itemNumber);
                if (targetSkillIds == null) {
                    throw badRequest(
                            "INVALID_MAPPING_RANGE",
                            "Skill mapping ranges must point only to existing item numbers."
                    );
                }
                targetSkillIds.addAll(rangeSkillIds);
            }
        }

        List<PreparedQuestion> preparedQuestions = new ArrayList<>();
        for (PreparedQuestion question : questionsByItemNumber.values()) {
            List<Long> skillIds = new ArrayList<>(skillIdsByItemNumber.get(question.itemNumber()));
            if (skillIds.isEmpty()) {
                throw badRequest(
                        "QUESTION_SKILL_MAPPING_REQUIRED",
                        "Every question must map to at least one skill."
                );
            }
            preparedQuestions.add(question.withSkillIds(skillIds));
        }

        return new PreparedPart(
                partOrder,
                partName,
                partType,
                part.pointsPerItem(),
                preparedQuestions
        );
    }

    private PreparedQuestion prepareQuestion(
            V2AssessmentQuestionRequest question,
            String partType,
            List<Long> skillIds
    ) {
        if (question == null) {
            throw badRequest("QUESTION_REQUIRED", "Question details are required.");
        }
        int itemNumber = requirePositive(question.itemNumber(), "ITEM_NUMBER_REQUIRED");
        String correctOption = normalizeOption(question.correctOption());
        String questionText = requiredTrimmed(
                question.questionText(),
                "QUESTION_TEXT_REQUIRED",
                "Question text is required."
        );

        if ("true_false".equals(partType)) {
            if (!"A".equals(correctOption) && !"B".equals(correctOption)) {
                throw badRequest("INVALID_TRUE_FALSE_ANSWER", "True/False answer keys must use A for True or B for False.");
            }
            return new PreparedQuestion(
                    itemNumber,
                    questionText,
                    "True",
                    "False",
                    "",
                    "",
                    null,
                    correctOption,
                    skillIds
            );
        }

        String optionA = requiredOption(question.optionA(), "A");
        String optionB = requiredOption(question.optionB(), "B");
        String optionC = requiredOption(question.optionC(), "C");
        String optionD = requiredOption(question.optionD(), "D");
        String optionE = optionalOption(question.optionE());
        if ("E".equals(correctOption) && optionE == null) {
            throw badRequest("OPTION_E_REQUIRED", "Option E is required when E is the correct answer.");
        }

        return new PreparedQuestion(
                itemNumber,
                questionText,
                optionA,
                optionB,
                optionC,
                optionD,
                optionE,
                correctOption,
                skillIds
        );
    }

    private List<Long> safeSkillIds(List<Long> skillIds) {
        if (skillIds == null) {
            return List.of();
        }
        List<Long> normalized = new ArrayList<>();
        for (Long skillId : skillIds) {
            if (skillId == null || skillId <= 0) {
                throw badRequest("INVALID_SKILL_ID", "Skill IDs must be positive.");
            }
            if (!normalized.contains(skillId)) {
                normalized.add(skillId);
            }
        }
        return normalized;
    }

    private List<V2SkillRangeMappingRequest> safeRanges(List<V2SkillRangeMappingRequest> ranges) {
        return ranges == null ? List.of() : ranges;
    }

    private V2AssessmentAssignmentContext requireOwnedActiveAssignment(
            V2AuthenticatedUser principal,
            long classAssignmentId
    ) {
        V2AssessmentAssignmentContext context = assessmentRepository.findAssignmentContext(classAssignmentId)
                .orElseThrow(() -> new V2AuthException(
                        "CLASS_ASSIGNMENT_NOT_FOUND",
                        "Class assignment was not found.",
                        HttpStatus.NOT_FOUND
                ));
        if (!context.teacherUserId().equals(principal.userId())
                || !context.schoolId().equals(principal.schoolId())) {
            throw new V2AuthException(
                    "CLASS_ASSIGNMENT_FORBIDDEN",
                    "This teacher does not own the selected class assignment.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (!"active".equals(context.assignmentStatus()) || !"active".equals(context.classStatus())) {
            throw conflict(
                    "CLASS_ASSIGNMENT_NOT_ACTIVE",
                    "Only active class assignments may be used for assessment creation."
            );
        }
        return context;
    }

    private void requireTermMatchesAssignment(Integer termPeriodId, V2AssessmentAssignmentContext assignment) {
        if (!assessmentRepository.termPeriodBelongsToAcademicYear(termPeriodId, assignment.academicYearId())) {
            throw badRequest(
                    "TERM_PERIOD_YEAR_MISMATCH",
                    "Term period must belong to the same academic year as the assigned class."
            );
        }
    }

    private V2AssessmentResponse requireAssessment(V2AuthenticatedUser principal, long testId) {
        V2AssessmentHeader header = requireOwnedHeader(principal, testId);
        List<V2AssessmentPartRow> parts = assessmentRepository.findParts(testId);
        List<V2AssessmentQuestionRow> questions = assessmentRepository.findQuestions(testId);

        List<V2AssessmentPartResponse> partResponses = parts.stream()
                .map(part -> new V2AssessmentPartResponse(
                        part.testPartId(),
                        part.partOrder(),
                        part.partName(),
                        part.partType(),
                        part.numberOfItems(),
                        part.pointsPerItem(),
                        questions.stream()
                                .filter(question -> question.testPartId().equals(part.testPartId()))
                                .map(question -> new V2AssessmentQuestionResponse(
                                        question.questionId(),
                                        question.itemNumber(),
                                        question.questionText(),
                                        question.optionA(),
                                        question.optionB(),
                                        question.optionC(),
                                        question.optionD(),
                                        question.optionE(),
                                        question.correctOption(),
                                        question.skillIds()
                                ))
                                .toList()
                ))
                .toList();

        return new V2AssessmentResponse(
                header.testId(),
                header.classAssignmentId(),
                header.classId(),
                header.academicYearId(),
                header.yearName(),
                header.gradeLevelId(),
                header.gradeLevelName(),
                header.sectionId(),
                header.sectionName(),
                header.subjectId(),
                header.subjectName(),
                header.termPeriodId(),
                header.termName(),
                header.testName(),
                header.testType(),
                header.testDate(),
                header.instructions(),
                header.totalItems(),
                header.status(),
                header.createdAt(),
                header.updatedAt(),
                partResponses
        );
    }

    private V2AssessmentHeader requireOwnedHeader(V2AuthenticatedUser principal, long testId) {
        V2AssessmentHeader header = assessmentRepository.findHeader(testId)
                .orElseThrow(() -> new V2AuthException(
                        "ASSESSMENT_NOT_FOUND",
                        "Assessment was not found.",
                        HttpStatus.NOT_FOUND
                ));
        if (!header.teacherUserId().equals(principal.userId()) || !header.schoolId().equals(principal.schoolId())) {
            throw new V2AuthException(
                    "ASSESSMENT_FORBIDDEN",
                    "This teacher does not own the selected assessment.",
                    HttpStatus.FORBIDDEN
            );
        }
        return header;
    }

    private void validateActivationReadiness(long testId) {
        if (assessmentRepository.countParts(testId) == 0 || assessmentRepository.countQuestions(testId) == 0) {
            throw conflict("ASSESSMENT_HAS_NO_QUESTIONS", "Assessment must contain at least one question.");
        }
        if (assessmentRepository.countQuestionsMissingAnswerKey(testId) > 0) {
            throw conflict("MISSING_ANSWER_KEYS", "Every question must have exactly one answer key before activation.");
        }
        if (assessmentRepository.countQuestionsMissingMappings(testId) > 0) {
            throw conflict("MISSING_SKILL_MAPPINGS", "Every question must have at least one skill mapping before activation.");
        }
        if (assessmentRepository.countInvalidTrueFalseAnswerKeys(testId) > 0) {
            throw conflict("INVALID_TRUE_FALSE_ANSWER", "True/False questions may only use A or B as answer keys.");
        }
        if (assessmentRepository.countSnapshotMismatches(testId) > 0) {
            throw conflict(
                    "ITEM_SNAPSHOT_MISMATCH",
                    "Assessment total_items and part number_of_items must match the actual questions."
            );
        }
    }

    private void recordAudit(
            V2AuthenticatedUser principal,
            V2RequestMetadata metadata,
            String action,
            long testId,
            Map<String, Object> details
    ) {
        V2RequestMetadata safeMetadata = metadata == null
                ? new V2RequestMetadata(null, null, null)
                : metadata;
        authRepository.recordAudit(
                UUID.randomUUID().toString(),
                principal.userId(),
                action,
                "tests",
                Long.toString(testId),
                "success",
                safeMetadata.ipAddress(),
                safeMetadata.deviceIdentifier(),
                safeMetadata.userAgent(),
                writeJson(details),
                clock.instant()
        );
    }

    private String writeJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize V2 assessment audit details.", exception);
        }
    }

    private void requireTeacher(V2AuthenticatedUser user) {
        if (user == null || !TEACHER_ROLE.equalsIgnoreCase(user.role())) {
            throw new V2AuthException("FORBIDDEN", "Teacher access is required.", HttpStatus.FORBIDDEN);
        }
        if (user.schoolId() == null || user.schoolId().isBlank()) {
            throw new V2AuthException("SCHOOL_CONTEXT_REQUIRED", "School context is required.", HttpStatus.FORBIDDEN);
        }
    }

    private String normalizeAllowed(
            String value,
            Set<String> allowedValues,
            String code,
            String message
    ) {
        String normalized = requiredTrimmed(value, code, message).toLowerCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw badRequest(code, message);
        }
        return normalized;
    }

    private String normalizeOption(String value) {
        String option = requiredTrimmed(value, "ANSWER_KEY_REQUIRED", "Correct option is required.")
                .toUpperCase(Locale.ROOT);
        if (!ALLOWED_OPTIONS.contains(option)) {
            throw badRequest("INVALID_ANSWER_KEY", "Correct option must be A, B, C, D, or E.");
        }
        return option;
    }

    private String requiredOption(String value, String optionLabel) {
        return requiredTrimmed(
                value,
                "OPTION_" + optionLabel + "_REQUIRED",
                "Option " + optionLabel + " is required for multiple-choice questions."
        );
    }

    private String optionalOption(String value) {
        String normalized = optionalTrimmed(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String requiredTrimmed(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(code, message);
        }
        return value.trim();
    }

    private String optionalTrimmed(String value) {
        return value == null ? null : value.trim();
    }

    private long requirePositive(Long value, String code) {
        if (value == null || value <= 0) {
            throw badRequest(code, "Identifier must be positive.");
        }
        return value;
    }

    private int requirePositive(Integer value, String code) {
        if (value == null || value <= 0) {
            throw badRequest(code, "Identifier must be positive.");
        }
        return value;
    }

    private V2AuthException badRequest(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.BAD_REQUEST);
    }

    private V2AuthException conflict(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.CONFLICT);
    }

    private record PreparedAssessment(
            V2AssessmentAssignmentContext assignment,
            Integer termPeriodId,
            String testName,
            String testType,
            java.time.LocalDate testDate,
            String instructions,
            Integer totalItems,
            List<PreparedPart> parts
    ) {
    }

    private record PreparedPart(
            Integer partOrder,
            String partName,
            String partType,
            BigDecimal pointsPerItem,
            List<PreparedQuestion> questions
    ) {
    }

    private record PreparedQuestion(
            Integer itemNumber,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String optionE,
            String correctOption,
            List<Long> skillIds
    ) {
        PreparedQuestion withSkillIds(List<Long> replacementSkillIds) {
            return new PreparedQuestion(
                    itemNumber,
                    questionText,
                    optionA,
                    optionB,
                    optionC,
                    optionD,
                    optionE,
                    correctOption,
                    replacementSkillIds
            );
        }
    }
}
