package com.capstone.assessment.v2.sync.service;

import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.notification.service.V2NotificationService;
import com.capstone.assessment.v2.sync.dto.V2SyncAnswerUploadRequest;
import com.capstone.assessment.v2.sync.dto.V2SyncDetectionUploadRequest;
import com.capstone.assessment.v2.sync.dto.V2SyncResultUploadRequest;
import com.capstone.assessment.v2.sync.dto.V2SyncScanSessionUploadRequest;
import com.capstone.assessment.v2.sync.dto.V2SyncUploadItemResponse;
import com.capstone.assessment.v2.sync.dto.V2SyncUploadRequest;
import com.capstone.assessment.v2.sync.dto.V2SyncUploadResponse;
import com.capstone.assessment.v2.sync.model.V2SyncQuestionScoringRow;
import com.capstone.assessment.v2.sync.model.V2SyncTestContext;
import com.capstone.assessment.v2.sync.repository.V2SyncUploadRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Profile("v2")
@Service
public class V2SyncUploadService {

    private static final String CONTRACT_VERSION = "2.0";
    private static final String TEACHER_ROLE = "teacher";
    private static final Set<String> SYNC_ACTIONS = Set.of("create", "update");
    private static final Set<String> ANSWER_STATUSES = Set.of("answered", "blank", "multiple", "invalid");
    private static final Set<String> CAPTURE_SOURCES = Set.of("omr", "teacher_correction", "manual");
    private static final Set<String> OPTIONS = Set.of("A", "B", "C", "D", "E");
    private static final Set<String> DETECTION_STATUSES = Set.of("detected", "blank", "multiple_marks", "uncertain");
    private static final Set<String> VERIFICATION_STATUSES = Set.of("pending", "confirmed", "corrected");

    private final V2SyncUploadRepository uploadRepository;
    private final ObjectMapper objectMapper;
    private final V2NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public V2SyncUploadService(
            V2SyncUploadRepository uploadRepository,
            ObjectMapper objectMapper,
            V2NotificationService notificationService
    ) {
        this(uploadRepository, objectMapper, notificationService, Clock.systemUTC());
    }

    V2SyncUploadService(V2SyncUploadRepository uploadRepository, ObjectMapper objectMapper, Clock clock) {
        this(uploadRepository, objectMapper, null, clock);
    }

    V2SyncUploadService(
            V2SyncUploadRepository uploadRepository,
            ObjectMapper objectMapper,
            V2NotificationService notificationService,
            Clock clock
    ) {
        this.uploadRepository = uploadRepository;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public V2SyncUploadResponse upload(V2AuthenticatedUser principal, V2SyncUploadRequest request) {
        requireTeacher(principal);
        validateBatch(request);

        V2SyncTestContext test = uploadRepository.findAuthorizedActiveTest(
                request.testId(),
                principal.userId(),
                principal.schoolId()
        ).orElseThrow(() -> authError(
                "TEST_ACCESS_DENIED",
                "Teacher does not own this active assessment.",
                HttpStatus.FORBIDDEN
        ));

        Map<Long, V2SyncQuestionScoringRow> scoringRows = scoringRows(request.testId());
        long syncId = uploadRepository.findSyncIdByUuid(request.syncUuid())
                .orElseGet(() -> uploadRepository.insertSync(
                        request.syncUuid(),
                        principal.userId(),
                        request.testId(),
                        request.deviceIdentifier(),
                        "in_progress",
                        now()
                ));

        List<V2SyncUploadItemResponse> items = request.results().stream()
                .map(result -> processResult(principal, request, test, scoringRows, syncId, result))
                .toList();

        String status = overallStatus(items);
        Instant completedAt = now();
        uploadRepository.updateSyncStatus(syncId, status, completedAt, null);
        notifySyncCompleted(principal, request, syncId, status, items, completedAt);
        return new V2SyncUploadResponse(
                request.syncUuid(),
                syncId,
                request.testId(),
                status,
                completedAt,
                items
        );
    }

    private V2SyncUploadItemResponse processResult(
            V2AuthenticatedUser principal,
            V2SyncUploadRequest request,
            V2SyncTestContext test,
            Map<Long, V2SyncQuestionScoringRow> scoringRows,
            long syncId,
            V2SyncResultUploadRequest result
    ) {
        Long syncItemId = null;
        String action = "create";
        try {
            validateUuid(result.resultUuid(), "RESULT_UUID_INVALID");
            action = allowed(lower(result.syncAction()), SYNC_ACTIONS, "INVALID_SYNC_ACTION", "Sync action must be create or update.");
            String syncAction = action;
            syncItemId = uploadRepository.findSyncItemId(syncId, result.resultUuid())
                    .orElseGet(() -> uploadRepository.insertSyncItem(syncId, result.resultUuid(), syncAction));

            ResultScore score = scoreResult(scoringRows, result);
            if (!uploadRepository.classListBelongsToClass(result.classListId(), test.classId(), principal.schoolId())) {
                throw itemError("INVALID_CLASS_LIST", "Learner is not part of the assessment class.");
            }

            long testResultId = upsertTestResult(request, result, score);
            Long scanSessionId = upsertScanSession(principal, request, testResultId, result);
            upsertAnswers(principal, result, score.answers());

            uploadRepository.updateSyncItem(syncItemId, testResultId, action, "success", null, null, now());
            return new V2SyncUploadItemResponse(
                    result.resultUuid(),
                    syncItemId,
                    testResultId,
                    scanSessionId,
                    "success",
                    null,
                    null
            );
        } catch (ItemFailureException | DataAccessException exception) {
            String code = exception instanceof ItemFailureException failure
                    ? failure.code()
                    : "DATABASE_ERROR";
            String message = exception instanceof ItemFailureException failure
                    ? failure.getMessage()
                    : "Result could not be saved.";
            if (syncItemId != null) {
                uploadRepository.updateSyncItem(syncItemId, null, action, "failed", code, message, null);
            }
            return new V2SyncUploadItemResponse(
                    result == null ? null : result.resultUuid(),
                    syncItemId,
                    null,
                    null,
                    "failed",
                    code,
                    message
            );
        }
    }

    private long upsertTestResult(V2SyncUploadRequest request, V2SyncResultUploadRequest result, ResultScore score) {
        return uploadRepository.findTestResultIdByUuid(result.resultUuid())
                .map(existingId -> {
                    uploadRepository.updateTestResult(
                            existingId,
                            result.classListId(),
                            result.attemptNumber(),
                            score.totalScore(),
                            score.maxScore(),
                            score.answers().size(),
                            instant(result.checkedAt())
                    );
                    return existingId;
                })
                .orElseGet(() -> uploadRepository.insertTestResult(
                        result.resultUuid(),
                        request.testId(),
                        result.classListId(),
                        result.attemptNumber(),
                        score.totalScore(),
                        score.maxScore(),
                        score.answers().size(),
                        instant(result.checkedAt())
                ));
    }

    private Long upsertScanSession(
            V2AuthenticatedUser principal,
            V2SyncUploadRequest request,
            long testResultId,
            V2SyncResultUploadRequest result
    ) {
        V2SyncScanSessionUploadRequest scan = result.scanSession();
        if (scan == null) {
            return null;
        }
        validateUuid(scan.scanUuid(), "SCAN_UUID_INVALID");
        if (!"verified".equals(lower(scan.scanStatus()))) {
            throw itemError("SCAN_NOT_VERIFIED", "Only teacher-verified scan sessions can be uploaded.");
        }

        long scanSessionId = uploadRepository.findScanSessionIdByUuid(scan.scanUuid())
                .map(existingId -> {
                    uploadRepository.updateScanSession(
                            existingId,
                            principal.userId(),
                            request.deviceIdentifier(),
                            scan.templateVersion(),
                            scan.scannerVersion(),
                            scan.imageHash(),
                            "verified",
                            instant(scan.scannedAt()),
                            instant(scan.verifiedAt())
                    );
                    return existingId;
                })
                .orElseGet(() -> uploadRepository.insertScanSession(
                        scan.scanUuid(),
                        principal.userId(),
                        request.deviceIdentifier(),
                        scan.templateVersion(),
                        scan.scannerVersion(),
                        scan.imageHash(),
                        "verified",
                        instant(scan.scannedAt()),
                        instant(scan.verifiedAt())
                ));

        for (V2SyncDetectionUploadRequest detection : safeDetections(scan)) {
            upsertDetection(scanSessionId, detection);
        }

        if (uploadRepository.scanLinkedToResult(testResultId, scanSessionId)) {
            uploadRepository.selectExistingScan(testResultId, scanSessionId, principal.userId());
        } else {
            uploadRepository.supersedeSelectedScans(testResultId, principal.userId());
            uploadRepository.insertSelectedScan(testResultId, scanSessionId, principal.userId());
        }
        return scanSessionId;
    }

    private void upsertDetection(long scanSessionId, V2SyncDetectionUploadRequest detection) {
        String detectionStatus = allowed(
                lower(detection.detectionStatus()),
                DETECTION_STATUSES,
                "INVALID_DETECTION_STATUS",
                "Detection status is invalid."
        );
        String verificationStatus = allowed(
                lower(detection.verificationStatus()),
                VERIFICATION_STATUSES,
                "INVALID_VERIFICATION_STATUS",
                "Verification status is invalid."
        );
        String detectedOption = normalizeOption(detection.detectedOption(), true);
        if (!"detected".equals(detectionStatus) && detectedOption != null) {
            throw itemError("AMBIGUOUS_DETECTION_OPTION", "Blank, multiple, or uncertain detections must not submit a detected option.");
        }
        uploadRepository.upsertDetection(
                scanSessionId,
                detection.questionId(),
                detectedOption,
                detection.confidenceScore(),
                detectionStatus,
                verificationStatus,
                rawMarkJson(detection.rawMarkInformation()),
                instant(detection.detectedAt())
        );
    }

    private void upsertAnswers(
            V2AuthenticatedUser principal,
            V2SyncResultUploadRequest result,
            List<ScoredAnswer> answers
    ) {
        boolean manualResult = result.scanSession() == null;
        for (ScoredAnswer answer : answers) {
            if (manualResult && !"manual".equals(answer.request().captureSource())) {
                throw itemError("INVALID_MANUAL_CAPTURE_SOURCE", "Manual results must use captureSource manual.");
            }
            uploadRepository.findAnswerIdByUuid(answer.request().answerUuid())
                    .ifPresentOrElse(
                            existingId -> uploadRepository.updateStudentAnswer(
                                    existingId,
                                    principal.userId(),
                                    answer.request().captureSource(),
                                    instant(answer.request().verifiedAt()),
                                    answer.selectedOption(),
                                    answer.request().answerStatus(),
                                    answer.correct(),
                                    answer.pointsEarned(),
                                    answer.request().correctionReason()
                            ),
                            () -> uploadRepository.insertStudentAnswer(
                                    resultId(result.resultUuid()),
                                    answer.request().questionId(),
                                    principal.userId(),
                                    answer.request().answerUuid(),
                                    answer.request().captureSource(),
                                    instant(answer.request().verifiedAt()),
                                    answer.selectedOption(),
                                    answer.request().answerStatus(),
                                    answer.correct(),
                                    answer.pointsEarned(),
                                    answer.request().correctionReason()
                            )
                    );
        }
    }

    private long resultId(String resultUuid) {
        return uploadRepository.findTestResultIdByUuid(resultUuid)
                .orElseThrow(() -> itemError("RESULT_NOT_SAVED", "Result was not saved before answers were processed."));
    }

    private ResultScore scoreResult(
            Map<Long, V2SyncQuestionScoringRow> scoringRows,
            V2SyncResultUploadRequest result
    ) {
        if (result.answers() == null || result.answers().isEmpty()) {
            throw itemError("ANSWERS_REQUIRED", "At least one verified answer is required.");
        }
        Map<Long, V2SyncAnswerUploadRequest> answersByQuestion = new LinkedHashMap<>();
        for (V2SyncAnswerUploadRequest answer : result.answers()) {
            validateUuid(answer.answerUuid(), "ANSWER_UUID_INVALID");
            if (answersByQuestion.putIfAbsent(answer.questionId(), answer) != null) {
                throw itemError("DUPLICATE_ANSWER_QUESTION", "Only one answer per question is allowed.");
            }
        }
        if (!answersByQuestion.keySet().equals(scoringRows.keySet())) {
            throw itemError("INCOMPLETE_ANSWER_SET", "Upload must include one verified answer for every assessment question.");
        }

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        List<ScoredAnswer> scoredAnswers = answersByQuestion.entrySet().stream()
                .map(entry -> {
                    V2SyncQuestionScoringRow key = scoringRows.get(entry.getKey());
                    V2SyncAnswerUploadRequest answer = entry.getValue();
                    String answerStatus = allowed(
                            lower(answer.answerStatus()),
                            ANSWER_STATUSES,
                            "INVALID_ANSWER_STATUS",
                            "Answer status is invalid."
                    );
                    String captureSource = allowed(
                            lower(answer.captureSource()),
                            CAPTURE_SOURCES,
                            "INVALID_CAPTURE_SOURCE",
                            "Capture source is invalid."
                    );
                    if (result.scanSession() == null && !"manual".equals(captureSource)) {
                        throw itemError("INVALID_MANUAL_CAPTURE_SOURCE", "Manual results must use captureSource manual.");
                    }
                    if (result.scanSession() != null && "manual".equals(captureSource)) {
                        throw itemError("INVALID_SCAN_CAPTURE_SOURCE", "Scanned results must use omr or teacher_correction capture source.");
                    }
                    String selectedOption = normalizeOption(answer.selectedOption(), true);
                    if ("answered".equals(answerStatus) && selectedOption == null) {
                        throw itemError("ANSWER_OPTION_REQUIRED", "Answered items must include selectedOption.");
                    }
                    if (!"answered".equals(answerStatus) && selectedOption != null) {
                        throw itemError("INVALID_UNANSWERED_OPTION", "Blank, multiple, or invalid answers must use selectedOption null.");
                    }
                    if ("teacher_correction".equals(captureSource)
                            && (answer.correctionReason() == null || answer.correctionReason().isBlank())) {
                        throw itemError("CORRECTION_REASON_REQUIRED", "Teacher-corrected answers require correctionReason.");
                    }
                    boolean correct = selectedOption != null && selectedOption.equalsIgnoreCase(key.correctOption());
                    BigDecimal points = correct ? key.pointsPerItem() : BigDecimal.ZERO;
                    return new ScoredAnswer(
                            new V2SyncAnswerUploadRequest(
                                    answer.answerUuid(),
                                    answer.questionId(),
                                    selectedOption,
                                    answerStatus,
                                    captureSource,
                                    answer.verifiedAt(),
                                    answer.correctionReason()
                            ),
                            selectedOption,
                            correct,
                            points
                    );
                })
                .toList();
        for (V2SyncQuestionScoringRow key : scoringRows.values()) {
            maxScore = maxScore.add(key.pointsPerItem());
        }
        for (ScoredAnswer answer : scoredAnswers) {
            totalScore = totalScore.add(answer.pointsEarned());
        }
        return new ResultScore(totalScore, maxScore, scoredAnswers);
    }

    private Map<Long, V2SyncQuestionScoringRow> scoringRows(long testId) {
        List<V2SyncQuestionScoringRow> rows = uploadRepository.listScoringRows(testId);
        if (rows.isEmpty()) {
            throw authError("SCORING_KEYS_REQUIRED", "Assessment has no answer keys to score.", HttpStatus.CONFLICT);
        }
        Map<Long, V2SyncQuestionScoringRow> byQuestion = new LinkedHashMap<>();
        for (V2SyncQuestionScoringRow row : rows) {
            byQuestion.put(row.questionId(), row);
        }
        return byQuestion;
    }

    private void validateBatch(V2SyncUploadRequest request) {
        if (request == null) {
            throw authError("SYNC_REQUEST_REQUIRED", "Sync upload request is required.", HttpStatus.BAD_REQUEST);
        }
        if (!CONTRACT_VERSION.equals(request.contractVersion())) {
            throw authError("UNSUPPORTED_CONTRACT_VERSION", "V2 sync upload requires contractVersion 2.0.", HttpStatus.BAD_REQUEST);
        }
        validateBatchUuid(request.syncUuid());
        if (request.results() == null || request.results().isEmpty()) {
            throw authError("RESULTS_REQUIRED", "At least one result is required.", HttpStatus.BAD_REQUEST);
        }
        Set<String> resultUuids = new LinkedHashSet<>();
        for (V2SyncResultUploadRequest result : request.results()) {
            if (result == null) {
                throw authError("RESULT_REQUIRED", "Result payload is required.", HttpStatus.BAD_REQUEST);
            }
            if (!resultUuids.add(result.resultUuid())) {
                throw authError("DUPLICATE_RESULT_UUID", "Result UUIDs must be unique in one batch.", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void requireTeacher(V2AuthenticatedUser principal) {
        if (principal == null || !TEACHER_ROLE.equalsIgnoreCase(principal.role())) {
            throw authError("FORBIDDEN", "Teacher access is required.", HttpStatus.FORBIDDEN);
        }
        if (principal.schoolId() == null || principal.schoolId().isBlank()) {
            throw authError("SCHOOL_CONTEXT_REQUIRED", "School context is required.", HttpStatus.FORBIDDEN);
        }
    }

    private String overallStatus(List<V2SyncUploadItemResponse> items) {
        long successes = items.stream().filter(item -> "success".equals(item.status())).count();
        if (successes == items.size()) {
            return "success";
        }
        if (successes == 0) {
            return "failed";
        }
        return "partial_success";
    }

    private void notifySyncCompleted(
            V2AuthenticatedUser principal,
            V2SyncUploadRequest request,
            long syncId,
            String status,
            List<V2SyncUploadItemResponse> items,
            Instant completedAt
    ) {
        if (notificationService == null) {
            return;
        }
        long successfulItems = items.stream().filter(item -> "success".equals(item.status())).count();
        String notificationType = "sync_" + status;
        String title = switch (status) {
            case "success" -> "Assessment sync completed";
            case "partial_success" -> "Assessment sync partially completed";
            default -> "Assessment sync failed";
        };
        String message = successfulItems + " of " + items.size()
                + " student result(s) synchronized for assessment " + request.testId() + ".";
        notificationService.notifyUser(
                principal.userId(),
                notificationType,
                title,
                message,
                "syncs",
                Long.toString(syncId),
                "sync:" + request.syncUuid() + ":" + status,
                completedAt
        );
    }

    private List<V2SyncDetectionUploadRequest> safeDetections(V2SyncScanSessionUploadRequest scan) {
        return scan.detections() == null ? List.of() : scan.detections();
    }

    private String rawMarkJson(Object rawMarkInformation) {
        try {
            return objectMapper.writeValueAsString(rawMarkInformation == null ? Map.of() : rawMarkInformation);
        } catch (JsonProcessingException exception) {
            throw itemError("INVALID_RAW_MARK", "Raw mark information must be JSON-serializable.");
        }
    }

    private String allowed(String value, Set<String> allowed, String code, String message) {
        if (value == null || !allowed.contains(value)) {
            throw itemError(code, message);
        }
        return value;
    }

    private String normalizeOption(String option, boolean nullable) {
        if (option == null || option.isBlank()) {
            if (nullable) {
                return null;
            }
            throw itemError("OPTION_REQUIRED", "Option is required.");
        }
        String normalized = option.trim().toUpperCase(Locale.ROOT);
        if (!OPTIONS.contains(normalized)) {
            throw itemError("INVALID_OPTION", "Options must be A, B, C, D, or E.");
        }
        return normalized;
    }

    private String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private void validateUuid(String value, String code) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw itemError(code, "Invalid UUID value.");
        }
    }

    private void validateBatchUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw authError("SYNC_UUID_INVALID", "Invalid sync UUID value.", HttpStatus.BAD_REQUEST);
        }
    }

    private Instant instant(OffsetDateTime value) {
        if (value == null) {
            throw itemError("TIMESTAMP_REQUIRED", "Timestamp is required.");
        }
        return value.toInstant();
    }

    private Instant now() {
        return clock.instant();
    }

    private V2AuthException authError(String code, String message, HttpStatus status) {
        return new V2AuthException(code, message, status);
    }

    private ItemFailureException itemError(String code, String message) {
        return new ItemFailureException(code, message);
    }

    private record ResultScore(
            BigDecimal totalScore,
            BigDecimal maxScore,
            List<ScoredAnswer> answers
    ) {
    }

    private record ScoredAnswer(
            V2SyncAnswerUploadRequest request,
            String selectedOption,
            boolean correct,
            BigDecimal pointsEarned
    ) {
    }

    private static final class ItemFailureException extends RuntimeException {
        private final String code;

        private ItemFailureException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
