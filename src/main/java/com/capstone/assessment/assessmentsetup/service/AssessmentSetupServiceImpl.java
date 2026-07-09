package com.capstone.assessment.assessmentsetup.service;

import com.capstone.assessment.assessmentsetup.dto.AssessmentDetailDto;
import com.capstone.assessment.assessmentsetup.dto.AssessmentDto;
import com.capstone.assessment.assessmentsetup.dto.CompetencyOptionDto;
import com.capstone.assessment.assessmentsetup.dto.CreateAssessmentRequest;
import com.capstone.assessment.assessmentsetup.dto.CreateTestPartRequest;
import com.capstone.assessment.assessmentsetup.repository.AssessmentSetupRepository;
import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AssessmentSetupServiceImpl implements AssessmentSetupService {

    private static final Set<String> ALLOWED_TEST_TYPES = Set.of("Quiz", "Exam", "Long Test");
    private static final Set<String> ALLOWED_TEST_STATUSES = Set.of("Draft", "Active", "Completed");
    private static final String PART_TYPE_MULTIPLE_CHOICE = "Multiple Choice";
    private static final String PART_TYPE_TRUE_OR_FALSE = "True or False";
    private static final String PART_TYPE_IDENTIFICATION = "Identification";
    private static final String PART_TYPE_ENUMERATION = "Enumeration";
    private static final Map<String, String> ALLOWED_PART_TYPES = Map.of(
            "multiple choice", PART_TYPE_MULTIPLE_CHOICE,
            "true or false", PART_TYPE_TRUE_OR_FALSE,
            "true/false", PART_TYPE_TRUE_OR_FALSE,
            "identification", PART_TYPE_IDENTIFICATION,
            "enumeration", PART_TYPE_ENUMERATION
    );

    private final AssessmentSetupRepository assessmentSetupRepository;

    public AssessmentSetupServiceImpl(AssessmentSetupRepository assessmentSetupRepository) {
        this.assessmentSetupRepository = assessmentSetupRepository;
    }

    @Override
    public List<AssessmentDto> getAssessmentsByTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new BadRequestException("Teacher ID is required.");
        }

        // Only assessment metadata is listed here; actual test questions are not stored by the system.
        return assessmentSetupRepository.findAssessmentsByTeacherId(teacherId);
    }

    @Override
    public AssessmentDetailDto getAssessmentDetail(Long testId) {
        if (testId == null) {
            throw new BadRequestException("Assessment ID is required.");
        }

        AssessmentDto assessment = assessmentSetupRepository.findAssessmentById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found."));

        // Assessment detail contains metadata, answer keys, and competency-linked parts only.
        return new AssessmentDetailDto(
                assessment.testId(),
                assessment.classId(),
                assessment.testName(),
                assessment.testType(),
                assessment.testDate(),
                assessment.gradingPeriodId(),
                assessment.gradingPeriodName(),
                assessment.testStatus(),
                assessment.subjectName(),
                assessment.sectionName(),
                assessment.gradeLevelName(),
                assessmentSetupRepository.findTestPartsByTestId(testId)
        );
    }

    @Override
    @Transactional
    public Long createAssessment(CreateAssessmentRequest request) {
        validateCreateAssessmentRequest(request);

        if (!assessmentSetupRepository.classExists(request.classId())) {
            throw new ResourceNotFoundException("Class not found.");
        }

        if (request.gradingPeriodId() != null
                && !assessmentSetupRepository.gradingPeriodMatchesClassAcademicYear(
                request.classId(),
                request.gradingPeriodId()
        )) {
            throw new BadRequestException("Grading period must belong to the class academic year.");
        }

        if (!ALLOWED_TEST_TYPES.contains(request.testType().trim())) {
            throw new BadRequestException("Test type must be one of: Quiz, Exam, Long Test.");
        }

        if (!ALLOWED_TEST_STATUSES.contains(request.testStatus().trim())) {
            throw new BadRequestException("Test status must be one of: Draft, Active, Completed.");
        }

        return assessmentSetupRepository.createAssessment(new CreateAssessmentRequest(
                request.classId(),
                request.testName().trim(),
                request.testType().trim(),
                request.testDate(),
                request.gradingPeriodId(),
                request.testStatus().trim()
        ));
    }

    @Override
    @Transactional
    public Long createTestPart(Long testId, CreateTestPartRequest request) {
        if (testId == null) {
            throw new BadRequestException("Assessment ID is required.");
        }

        if (request == null) {
            throw new BadRequestException("Create test part request must not be null.");
        }

        if (!assessmentSetupRepository.testExists(testId)) {
            throw new ResourceNotFoundException("Assessment not found.");
        }

        if (request.competencyId() == null) {
            throw new BadRequestException("Competency ID is required.");
        }

        if (!assessmentSetupRepository.competencyExists(request.competencyId())) {
            throw new ResourceNotFoundException("Competency not found.");
        }

        if (request.partOrder() == null || request.partOrder().isBlank()) {
            throw new BadRequestException("Part order is required.");
        }

        if (request.partType() == null || request.partType().isBlank()) {
            throw new BadRequestException("Part type is required.");
        }

        if (request.numberOfItems() == null) {
            throw new BadRequestException("Number of items is required.");
        }

        if (request.numberOfItems() <= 0) {
            throw new BadRequestException("Number of items must be greater than 0.");
        }

        if (request.pointsPerItem() == null) {
            throw new BadRequestException("Points per item is required.");
        }

        if (request.pointsPerItem() <= 0) {
            throw new BadRequestException("Points per item must be greater than 0.");
        }

        if (request.answerKey() == null || request.answerKey().isBlank()) {
            throw new BadRequestException("Answer key is required.");
        }

        String normalizedPartOrder = request.partOrder().trim();
        String normalizedPartType = normalizePartType(request.partType());
        String normalizedAnswerKey = normalizeAnswerKey(
                normalizedPartType,
                request.answerKey(),
                request.numberOfItems()
        );

        if (assessmentSetupRepository.testPartOrderExists(testId, normalizedPartOrder)) {
            throw new BadRequestException("Test part order already exists for this assessment.");
        }

        // The system stores competency-linked test parts and answer keys, not the actual questions themselves.
        return assessmentSetupRepository.createTestPart(testId, new CreateTestPartRequest(
                request.competencyId(),
                normalizedPartOrder,
                normalizedPartType,
                request.numberOfItems(),
                request.pointsPerItem(),
                normalizedAnswerKey
        ));
    }

    @Override
    public List<CompetencyOptionDto> getCompetenciesForClass(Long classId) {
        if (classId == null) {
            throw new BadRequestException("Class ID is required.");
        }

        if (!assessmentSetupRepository.classExists(classId)) {
            throw new ResourceNotFoundException("Class not found.");
        }

        return assessmentSetupRepository.findCompetenciesForClass(classId);
    }

    private void validateCreateAssessmentRequest(CreateAssessmentRequest request) {
        if (request == null) {
            throw new BadRequestException("Create assessment request must not be null.");
        }

        if (request.classId() == null) {
            throw new BadRequestException("Class ID is required.");
        }

        if (request.testName() == null || request.testName().isBlank()) {
            throw new BadRequestException("Test name is required.");
        }

        if (request.testType() == null || request.testType().isBlank()) {
            throw new BadRequestException("Test type is required.");
        }

        if (request.testDate() == null) {
            throw new BadRequestException("Test date is required.");
        }

        if (request.testStatus() == null || request.testStatus().isBlank()) {
            throw new BadRequestException("Test status is required.");
        }
    }

    private String normalizePartType(String partType) {
        String normalizedKey = partType.trim().toLowerCase(Locale.ROOT);
        String normalizedPartType = ALLOWED_PART_TYPES.get(normalizedKey);

        if (normalizedPartType == null) {
            throw new BadRequestException(
                    "Part type must be one of: Multiple Choice, True or False, Identification, Enumeration."
            );
        }

        return normalizedPartType;
    }

    private String normalizeAnswerKey(String partType, String answerKey, Integer numberOfItems) {
        if (PART_TYPE_MULTIPLE_CHOICE.equals(partType)) {
            return normalizeMultipleChoiceAnswerKey(answerKey, numberOfItems);
        }

        if (PART_TYPE_TRUE_OR_FALSE.equals(partType)) {
            return normalizeTrueOrFalseAnswerKey(answerKey, numberOfItems);
        }

        return answerKey.trim();
    }

    private String normalizeMultipleChoiceAnswerKey(String answerKey, Integer numberOfItems) {
        List<String> answers = splitAnswerKey(answerKey);
        if (answers.size() != numberOfItems) {
            throw new BadRequestException("Multiple Choice answer key count must match number of items.");
        }

        return answers.stream()
                .map(answer -> answer.toUpperCase(Locale.ROOT))
                .peek(answer -> {
                    if (!Set.of("A", "B", "C", "D").contains(answer)) {
                        throw new BadRequestException("Multiple Choice answers must be A, B, C, or D.");
                    }
                })
                .reduce((left, right) -> left + "," + right)
                .orElseThrow(() -> new BadRequestException("Answer key is required."));
    }

    private String normalizeTrueOrFalseAnswerKey(String answerKey, Integer numberOfItems) {
        List<String> answers = splitAnswerKey(answerKey);
        if (answers.size() != numberOfItems) {
            throw new BadRequestException("True or False answer key count must match number of items.");
        }

        return answers.stream()
                .map(this::normalizeTrueOrFalseAnswer)
                .reduce((left, right) -> left + "," + right)
                .orElseThrow(() -> new BadRequestException("Answer key is required."));
    }

    private String normalizeTrueOrFalseAnswer(String answer) {
        String normalizedAnswer = answer.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalizedAnswer) || "t".equals(normalizedAnswer)) {
            return "True";
        }

        if ("false".equals(normalizedAnswer) || "f".equals(normalizedAnswer)) {
            return "False";
        }

        throw new BadRequestException("True or False answers must be True or False.");
    }

    private List<String> splitAnswerKey(String answerKey) {
        return Arrays.stream(answerKey.split(",", -1))
                .map(String::trim)
                .peek(answer -> {
                    if (answer.isBlank()) {
                        throw new BadRequestException("Answer key entries must not be blank.");
                    }
                })
                .toList();
    }
}
