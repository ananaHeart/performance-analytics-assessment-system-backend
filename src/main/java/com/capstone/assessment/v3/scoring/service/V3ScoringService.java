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
import com.capstone.assessment.v3.mobile.service.V3MobileFinalizationService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Profile("v3")
@Service
public class V3ScoringService {

    private static final Set<String> OBJECTIVE_TYPES = Set.of("multiple_choice", "true_false");
    private static final Set<String> WRITTEN_TYPES = Set.of("identification", "enumeration", "essay");
    private static final Set<String> SCORABLE_TEST_STATUSES = Set.of("active", "completed");

    private final V3ScoringRepository repository;
    private final V3AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final V3MobileFinalizationService mobileFinalization;

    @Autowired
    public V3ScoringService(
            V3ScoringRepository repository,
            V3AuditService auditService,
            ObjectMapper objectMapper,
            V3MobileFinalizationService mobileFinalization
    ) {
        this(repository, auditService, objectMapper, Clock.systemUTC(), mobileFinalization);
    }

    V3ScoringService(
            V3ScoringRepository repository,
            V3AuditService auditService,
            ObjectMapper objectMapper,
            Clock clock,
            V3MobileFinalizationService mobileFinalization
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.mobileFinalization = mobileFinalization;
    }

    @Transactional
    public V3ScoredResultResponse finalizeResult(
            V3AuthenticatedUser user,
            long testResultId,
            V3RequestMetadata requestMetadata
    ) {
        return finalizeInternal(user,testResultId,requestMetadata,null);
    }

    /** Internal correction entry point; the public bodyless finalization route cannot select this path. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public V3ScoredResultResponse finalizeCorrection(V3AuthenticatedUser user,long testResultId,
            com.capstone.assessment.v3.mobile.service.V3ResultCorrectionService.ValidatedCorrection correction) {
        if(correction==null)throw conflict("CORRECTION_NOT_VALIDATED","A validated correction is required.");
        return finalizeInternal(user,testResultId,null,correction);
    }
    private V3ScoredResultResponse finalizeInternal(V3AuthenticatedUser user,long testResultId,V3RequestMetadata requestMetadata,
            com.capstone.assessment.v3.mobile.service.V3ResultCorrectionService.ValidatedCorrection correction) {
        requireTeacher(user);
        mobileFinalization.lockOwner(user);

        ResultContext context = repository.findResultContextForUpdate(testResultId)
                .orElseThrow(() -> error(
                        "RESULT_NOT_FOUND",
                        "The assessment result was not found.",
                        HttpStatus.NOT_FOUND
                ));
        validateOwnership(user, context);
        var mobile = correction==null?mobileFinalization.prepare(context):java.util.Optional.of(mobileFinalization.prepareCorrection(context,correction));
        if (mobile.isPresent() && mobile.get().replay() != null) return mobile.get().replay();

        List<ScoringRow> rows = repository.findScoringRowsForUpdate(context.testId(), context.testResultId());
        if (rows.isEmpty()) {
            throw conflict("RESULT_NOT_READY", "The assessment has no questions to score.");
        }
        if (repository.countAnswersOutsideTest(context.testResultId(), context.testId()) > 0) {
            throw conflict(
                    "ANSWER_COVERAGE_INVALID",
                    "The result contains an answer that does not belong to this assessment."
            );
        }

        Instant now = clock.instant();
        PerformanceRuleSet ruleSet = repository.findActiveStudentScoreRuleSet(user.schoolId(), now)
                .orElseThrow(() -> conflict(
                        "SCORING_RULE_NOT_CONFIGURED",
                        "No active student-score performance rule is configured for this school."
                ));
        ParsedRule parsedRule = parseRule(ruleSet);

        Map<Long, PartAccumulator> partScores = new LinkedHashMap<>();
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        int objectiveUpdates = 0;

        for (ScoringRow row : rows) {
            validateCommonAnswer(row, user.userId());
            ScoredAnswer scoredAnswer = scoreAnswer(row);

            if (OBJECTIVE_TYPES.contains(row.questionTypeCode())) {
                objectiveUpdates += repository.updateObjectiveScore(
                        row.studentAnswerId(),
                        scoredAnswer.isCorrect(),
                        scoredAnswer.pointsEarned()
                );
            }

            PartAccumulator part = partScores.computeIfAbsent(
                    row.testPartId(),
                    ignored -> new PartAccumulator(row.testPartId(), row.partOrder(), row.partName())
            );
            part.add(scoredAnswer.pointsEarned(), row.maximumPoints());
            totalScore = totalScore.add(scoredAnswer.pointsEarned());
            maxScore = maxScore.add(row.maximumPoints());
        }

        totalScore = normalizePoints(totalScore);
        maxScore = normalizePoints(maxScore);
        if (maxScore.signum() <= 0 || totalScore.compareTo(maxScore) > 0) {
            throw invalidRule("The computed result totals violate the score boundaries.");
        }

        RuleDecision decision = parsedRule.decide(percentage(totalScore, maxScore, parsedRule.roundingScale()));
        int itemsEvaluated = rows.size();
        boolean snapshotChanged = resultSnapshotChanged(
                context,
                totalScore,
                maxScore,
                itemsEvaluated,
                decision,
                ruleSet.performanceRuleSetId()
        );
        boolean scoreChanged = objectiveUpdates > 0 || snapshotChanged;

        int scoreVersion = context.scoreVersion();
        Instant scoredAt = context.scoredAt();
        if (scoreChanged) {
            scoreVersion = mobile.isPresent() && mobile.get().targetScoreVersion()>0 ? mobile.get().targetScoreVersion() : "finalized".equals(context.resultStatus())
                    ? Math.addExact(context.scoreVersion(), 1)
                    : Math.max(context.scoreVersion(), 1);
            int updated = repository.updateResultScore(
                    context.testResultId(),
                    context.scoreVersion(),
                    totalScore,
                    maxScore,
                    itemsEvaluated,
                    decision.percentage(),
                    decision.status(),
                    ruleSet.performanceRuleSetId(),
                    scoreVersion,
                    now
            );
            if (updated != 1) {
                throw conflict(
                        "SCORE_UPDATE_CONFLICT",
                        "The result changed while it was being scored. Reload it and try again."
                );
            }
            scoredAt = now;
            auditService.record(
                    user.userId(),
                    "result.score.finalize",
                    "test_results",
                    Long.toString(context.testResultId()),
                    "success",
                    requestMetadata,
                    Map.of(
                            "testId", context.testId(),
                            "classListId", context.classListId(),
                            "totalScore", totalScore.toPlainString(),
                            "maxScore", maxScore.toPlainString(),
                            "itemsEvaluated", itemsEvaluated,
                            "performanceStatus", decision.status(),
                            "performanceRuleSetId", ruleSet.performanceRuleSetId(),
                            "scoreVersion", scoreVersion
                    ),
                    now
            );
        }

        var response = response(
                context,
                totalScore,
                maxScore,
                decision,
                ruleSet.performanceRuleSetId(),
                itemsEvaluated,
                scoreVersion,
                scoredAt,
                scoreChanged,
                partScores,
                parsedRule.roundingScale()
        );
        if (mobile.isPresent()) mobileFinalization.complete(mobile.get(), response);
        return response;
    }

    private void requireTeacher(V3AuthenticatedUser user) {
        if (user == null) {
            throw error("AUTHENTICATION_REQUIRED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        if (!"teacher".equals(user.role())) {
            throw error(
                    "TEACHER_ROLE_REQUIRED",
                    "Only authenticated teachers may finalize assessment scores.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (!"active".equals(user.status()) || user.schoolId() == null || user.schoolId().isBlank()) {
            throw error(
                    "ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active school-linked teacher account is required.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void validateOwnership(V3AuthenticatedUser user, ResultContext context) {
        if (context.teacherUserId() != user.userId()
                || !Objects.equals(context.testSchoolId(), user.schoolId())
                || !Objects.equals(context.studentSchoolId(), user.schoolId())) {
            throw error(
                    "RESULT_ACCESS_DENIED",
                    "You do not own this assessment result.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (context.assignmentClassId() != context.membershipClassId()) {
            throw conflict(
                    "RESULT_CLASS_MISMATCH",
                    "The learner membership does not belong to the assigned class."
            );
        }
        if ("superseded".equals(context.resultStatus())) {
            throw conflict("RESULT_SUPERSEDED", "A superseded result cannot be finalized.");
        }
        if (!SCORABLE_TEST_STATUSES.contains(context.testStatus())) {
            throw conflict("ASSESSMENT_NOT_SCORABLE", "The assessment is not active or completed.");
        }
        if ("archived".equals(context.testAssignmentStatus())) {
            throw conflict("ASSIGNMENT_ARCHIVED", "An archived test assignment cannot be scored.");
        }
    }

    private void validateCommonAnswer(ScoringRow row, long teacherUserId) {
        if (row.answerKeyId() == null) {
            throw conflict(
                    "ANSWER_KEY_MISSING",
                    "Question " + row.questionId() + " has no answer key."
            );
        }
        if (row.studentAnswerId() == null) {
            throw conflict(
                    "ANSWER_MISSING",
                    "Question " + row.questionId() + " has no submitted student answer."
            );
        }
        if (!"finalized".equals(row.evaluationStatus())
                || row.verifiedByUserId() == null
                || row.verifiedAt() == null
                || row.finalizedAt() == null) {
            throw conflict(
                    "ANSWER_NOT_FINALIZED",
                    "Question " + row.questionId() + " is not fully teacher-verified."
            );
        }
        if (row.verifiedByUserId() != teacherUserId) {
            throw error(
                    "ANSWER_VERIFIER_MISMATCH",
                    "Question " + row.questionId() + " was not verified by the assigned teacher.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (row.maximumPoints() == null || row.maximumPoints().signum() <= 0) {
            throw conflict(
                    "QUESTION_POINTS_INVALID",
                    "Question " + row.questionId() + " has invalid maximum points."
            );
        }
    }

    private ScoredAnswer scoreAnswer(ScoringRow row) {
        if (OBJECTIVE_TYPES.contains(row.questionTypeCode())) {
            return scoreObjectiveAnswer(row);
        }
        if (WRITTEN_TYPES.contains(row.questionTypeCode())) {
            return scoreWrittenAnswer(row);
        }
        throw conflict(
                "QUESTION_TYPE_UNSUPPORTED",
                "Question " + row.questionId() + " uses an unsupported question type."
        );
    }

    private ScoredAnswer scoreObjectiveAnswer(ScoringRow row) {
        if (!"option".equals(row.answerKeyType()) || row.correctQuestionOptionId() == null) {
            throw conflict(
                    "ANSWER_KEY_INVALID",
                    "Question " + row.questionId() + " requires an option answer key."
            );
        }
        if (!Set.of("answered", "blank").contains(row.answerStatus())) {
            throw conflict(
                    "OBJECTIVE_RESCAN_REQUIRED",
                    "Question " + row.questionId()
                            + " has a multiple, uncertain, invalid, or unresolved objective mark."
            );
        }
        if ("blank".equals(row.answerStatus())) {
            if (row.selectedQuestionOptionId() != null) {
                throw conflict(
                        "OBJECTIVE_ANSWER_INVALID",
                        "Question " + row.questionId() + " is blank but has a selected option."
                );
            }
            return new ScoredAnswer(false, normalizePoints(BigDecimal.ZERO));
        }
        if (row.selectedQuestionOptionId() == null
                || row.selectedOptionQuestionId() == null
                || row.selectedOptionQuestionId() != row.questionId()) {
            throw conflict(
                    "OBJECTIVE_ANSWER_INVALID",
                    "Question " + row.questionId() + " has an invalid selected option."
            );
        }

        boolean correct = Objects.equals(row.selectedQuestionOptionId(), row.correctQuestionOptionId());
        return new ScoredAnswer(
                correct,
                normalizePoints(correct ? row.maximumPoints() : BigDecimal.ZERO)
        );
    }

    private ScoredAnswer scoreWrittenAnswer(ScoringRow row) {
        if (!Set.of("answered", "blank").contains(row.answerStatus())) {
            throw conflict(
                    "WRITTEN_ANSWER_NOT_READY",
                    "Question " + row.questionId() + " has not completed manual review."
            );
        }
        if ("answered".equals(row.answerStatus())
                && (row.responseText() == null || row.responseText().isBlank())
                && row.writtenEvidenceAttachmentCount() == 0) {
            throw conflict(
                    "WRITTEN_RESPONSE_MISSING",
                    "Question " + row.questionId() + " has no written-response text or retained evidence."
            );
        }

        BigDecimal points = normalizePoints(row.pointsEarned());
        if (points.signum() < 0 || points.compareTo(row.maximumPoints()) > 0) {
            throw conflict(
                    "MANUAL_SCORE_OUT_OF_RANGE",
                    "Question " + row.questionId() + " has points outside its allowed range."
            );
        }
        if ("blank".equals(row.answerStatus()) && points.signum() != 0) {
            throw conflict(
                    "BLANK_ANSWER_HAS_POINTS",
                    "Question " + row.questionId() + " is blank and must receive zero points."
            );
        }

        if ("essay".equals(row.questionTypeCode())) {
            validateEssayRubric(row, points);
        } else if (!Set.of("accepted_text", "manual").contains(row.answerKeyType())) {
            throw conflict(
                    "ANSWER_KEY_INVALID",
                    "Question " + row.questionId() + " requires an accepted-text or manual answer key."
            );
        }

        return new ScoredAnswer(points.compareTo(row.maximumPoints()) == 0, points);
    }

    private void validateEssayRubric(ScoringRow row, BigDecimal points) {
        Long rubricId = row.answerKeyRubricId() != null ? row.answerKeyRubricId() : row.questionRubricId();
        if (rubricId == null) {
            if (!"manual".equals(row.answerKeyType())) {
                throw conflict(
                        "ESSAY_SCORING_CONFIGURATION_INVALID",
                        "Essay question " + row.questionId() + " requires a rubric or manual answer key."
                );
            }
            return;
        }
        if (!"rubric".equals(row.answerKeyType())
                || !Objects.equals(row.questionRubricId(), rubricId)
                || !Objects.equals(row.answerKeyRubricId(), rubricId)) {
            throw conflict(
                    "ESSAY_SCORING_CONFIGURATION_INVALID",
                    "Essay question " + row.questionId() + " has inconsistent rubric references."
            );
        }
        if (repository.countRubricScoresOutsideRubric(
                row.studentAnswerId(), row.answerScoreVersion(), rubricId
        ) > 0) {
            throw conflict(
                    "RUBRIC_SCORE_INVALID",
                    "Essay question " + row.questionId() + " contains scores from another rubric."
            );
        }

        RubricScoreSummary summary = repository.summarizeRubricScores(
                row.studentAnswerId(), row.answerScoreVersion(), rubricId
        );
        if (summary == null
                || summary.totalCriteria() == 0
                || summary.scoredCriteria() != summary.totalCriteria()
                || summary.scoredRequiredCriteria() != summary.requiredCriteria()
                || summary.excessiveScoreCount() > 0
                || normalizePoints(summary.awardedPoints()).compareTo(points) != 0) {
            throw conflict(
                    "RUBRIC_SCORE_INCOMPLETE",
                    "Essay question " + row.questionId() + " has incomplete or inconsistent rubric scores."
            );
        }
    }

    private ParsedRule parseRule(PerformanceRuleSet ruleSet) {
        try {
            JsonNode root = objectMapper.readTree(ruleSet.ruleDefinition());
            if (!"percentage".equals(root.path("metric").asText())) {
                throw invalidRule("The student-score rule must use the percentage metric.");
            }
            int roundingScale = root.path("rounding_scale").asInt(-1);
            if (roundingScale < 0 || roundingScale > 4) {
                throw invalidRule("The student-score rule has an invalid rounding scale.");
            }
            JsonNode bandsNode = root.path("bands");
            if (!bandsNode.isArray() || bandsNode.isEmpty()) {
                throw invalidRule("The student-score rule has no performance bands.");
            }

            List<PerformanceBand> bands = new ArrayList<>();
            for (JsonNode bandNode : bandsNode) {
                BigDecimal minimum = decimalField(bandNode, "minimum_percentage");
                BigDecimal maximum = decimalField(bandNode, "maximum_percentage");
                String status = requiredText(bandNode, "status");
                String label = requiredText(bandNode, "label");
                if (minimum.signum() < 0
                        || maximum.compareTo(BigDecimal.valueOf(100)) > 0
                        || minimum.compareTo(maximum) > 0) {
                    throw invalidRule("The student-score rule contains an invalid performance band.");
                }
                bands.add(new PerformanceBand(
                        minimum,
                        maximum,
                        bandNode.path("minimum_inclusive").asBoolean(true),
                        bandNode.path("maximum_inclusive").asBoolean(true),
                        status,
                        label
                ));
            }
            return new ParsedRule(roundingScale, List.copyOf(bands));
        } catch (V3AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidRule("The active student-score rule is not valid JSON.");
        }
    }

    private BigDecimal decimalField(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isNumber()) {
            throw invalidRule("The student-score rule is missing " + fieldName + ".");
        }
        return value.decimalValue();
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText("").trim();
        if (value.isEmpty()) {
            throw invalidRule("The student-score rule is missing " + fieldName + ".");
        }
        return value;
    }

    private boolean resultSnapshotChanged(
            ResultContext context,
            BigDecimal totalScore,
            BigDecimal maxScore,
            int itemsEvaluated,
            RuleDecision decision,
            long performanceRuleSetId
    ) {
        return !"finalized".equals(context.resultStatus())
                || !sameNumber(context.totalScore(), totalScore)
                || !sameNumber(context.maxScore(), maxScore)
                || context.itemsEvaluated() != itemsEvaluated
                || !sameNumber(context.percentageSnapshot(), decision.percentage())
                || !Objects.equals(context.performanceStatus(), decision.status())
                || !Objects.equals(context.performanceRuleSetId(), performanceRuleSetId);
    }

    private V3ScoredResultResponse response(
            ResultContext context,
            BigDecimal totalScore,
            BigDecimal maxScore,
            RuleDecision decision,
            long performanceRuleSetId,
            int itemsEvaluated,
            int scoreVersion,
            Instant scoredAt,
            boolean scoreChanged,
            Map<Long, PartAccumulator> partScores,
            int roundingScale
    ) {
        List<V3ScoredResultResponse.PartScore> parts = partScores.values().stream()
                .sorted(Comparator.comparingInt(PartAccumulator::partOrder))
                .map(part -> new V3ScoredResultResponse.PartScore(
                        part.testPartId(),
                        part.partOrder(),
                        part.partName(),
                        normalizePoints(part.totalScore()),
                        normalizePoints(part.maxScore()),
                        percentage(part.totalScore(), part.maxScore(), roundingScale),
                        part.itemsEvaluated()
                ))
                .toList();
        return new V3ScoredResultResponse(
                context.testResultId(),
                context.resultUuid(),
                context.testAssignmentId(),
                context.testId(),
                context.classListId(),
                context.studentId(),
                context.studentName(),
                context.attemptNumber(),
                totalScore,
                maxScore,
                decision.percentage(),
                decision.status(),
                decision.label(),
                performanceRuleSetId,
                itemsEvaluated,
                scoreVersion,
                "finalized",
                scoredAt,
                scoreChanged,
                parts
        );
    }

    private BigDecimal percentage(BigDecimal score, BigDecimal maximum, int scale) {
        if (maximum == null || maximum.signum() <= 0) {
            throw invalidRule("A percentage cannot be computed from a zero maximum score.");
        }
        return score.multiply(BigDecimal.valueOf(100)).divide(maximum, scale, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePoints(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw conflict("SCORE_PRECISION_INVALID", "Scores may use at most two decimal places.");
        }
    }

    private boolean sameNumber(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private V3AuthException conflict(String code, String message) {
        return error(code, message, HttpStatus.CONFLICT);
    }

    private V3AuthException invalidRule(String message) {
        return error("SCORING_RULE_INVALID", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private V3AuthException error(String code, String message, HttpStatus status) {
        return new V3AuthException(code, message, status);
    }

    private record ScoredAnswer(boolean isCorrect, BigDecimal pointsEarned) {
    }

    private record RuleDecision(BigDecimal percentage, String status, String label) {
    }

    private record PerformanceBand(
            BigDecimal minimum,
            BigDecimal maximum,
            boolean minimumInclusive,
            boolean maximumInclusive,
            String status,
            String label
    ) {
        private boolean contains(BigDecimal percentage) {
            int minimumComparison = percentage.compareTo(minimum);
            int maximumComparison = percentage.compareTo(maximum);
            return (minimumComparison > 0 || minimumInclusive && minimumComparison == 0)
                    && (maximumComparison < 0 || maximumInclusive && maximumComparison == 0);
        }
    }

    private record ParsedRule(int roundingScale, List<PerformanceBand> bands) {
        private RuleDecision decide(BigDecimal percentage) {
            List<PerformanceBand> matches = bands.stream()
                    .filter(band -> band.contains(percentage))
                    .toList();
            if (matches.size() != 1) {
                throw new V3AuthException(
                        "SCORING_RULE_INVALID",
                        "The student-score rule must match exactly one performance band.",
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            PerformanceBand band = matches.get(0);
            return new RuleDecision(percentage, band.status(), band.label());
        }
    }

    private static final class PartAccumulator {
        private final long testPartId;
        private final int partOrder;
        private final String partName;
        private BigDecimal totalScore = BigDecimal.ZERO;
        private BigDecimal maxScore = BigDecimal.ZERO;
        private int itemsEvaluated;

        private PartAccumulator(long testPartId, int partOrder, String partName) {
            this.testPartId = testPartId;
            this.partOrder = partOrder;
            this.partName = partName;
        }

        private void add(BigDecimal earnedPoints, BigDecimal maximumPoints) {
            totalScore = totalScore.add(earnedPoints);
            maxScore = maxScore.add(maximumPoints);
            itemsEvaluated++;
        }

        private long testPartId() {
            return testPartId;
        }

        private int partOrder() {
            return partOrder;
        }

        private String partName() {
            return partName;
        }

        private BigDecimal totalScore() {
            return totalScore;
        }

        private BigDecimal maxScore() {
            return maxScore;
        }

        private int itemsEvaluated() {
            return itemsEvaluated;
        }
    }
}
