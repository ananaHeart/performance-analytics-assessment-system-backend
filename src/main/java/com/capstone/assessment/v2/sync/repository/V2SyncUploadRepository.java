package com.capstone.assessment.v2.sync.repository;

import com.capstone.assessment.v2.sync.model.V2SyncQuestionScoringRow;
import com.capstone.assessment.v2.sync.model.V2SyncTestContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile("v2")
@Repository
public class V2SyncUploadRepository {

    private final JdbcTemplate jdbcTemplate;

    public V2SyncUploadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<V2SyncTestContext> findAuthorizedActiveTest(long testId, long teacherUserId, String schoolId) {
        return jdbcTemplate.query(
                """
                SELECT t.test_id,
                       ca.class_id,
                       ca.user_id AS teacher_user_id,
                       u.school_id,
                       t.status
                  FROM tests t
                  JOIN class_assignments ca ON ca.class_assignment_id = t.class_assignment_id
                  JOIN users u ON u.user_id = ca.user_id
                 WHERE t.test_id = ?
                   AND ca.user_id = ?
                   AND u.school_id = ?
                   AND ca.status = 'active'
                   AND t.status = 'active'
                """,
                (rs, rowNum) -> new V2SyncTestContext(
                        rs.getLong("test_id"),
                        rs.getLong("class_id"),
                        rs.getLong("teacher_user_id"),
                        rs.getString("school_id"),
                        rs.getString("status")
                ),
                testId,
                teacherUserId,
                schoolId
        ).stream().findFirst();
    }

    public boolean classListBelongsToClass(long classListId, long classId, String schoolId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM class_lists cl
                  JOIN students st ON st.student_id = cl.student_id
                 WHERE cl.class_list_id = ?
                   AND cl.class_id = ?
                   AND st.school_id = ?
                   AND st.status = 'active'
                """,
                Integer.class,
                classListId,
                classId,
                schoolId
        );
        return count != null && count > 0;
    }

    public List<V2SyncQuestionScoringRow> listScoringRows(long testId) {
        return jdbcTemplate.query(
                """
                SELECT q.question_id,
                       ak.correct_option,
                       tp.points_per_item
                  FROM test_parts tp
                  JOIN questions q ON q.test_part_id = tp.test_part_id
                  JOIN answer_keys ak ON ak.question_id = q.question_id
                 WHERE tp.test_id = ?
                 ORDER BY tp.part_order, q.item_number
                """,
                (rs, rowNum) -> new V2SyncQuestionScoringRow(
                        rs.getLong("question_id"),
                        rs.getString("correct_option"),
                        rs.getBigDecimal("points_per_item")
                ),
                testId
        );
    }

    public Optional<Long> findSyncIdByUuid(String syncUuid) {
        return queryLong("SELECT sync_id FROM syncs WHERE sync_uuid = ?", syncUuid);
    }

    public long insertSync(
            String syncUuid,
            long userId,
            long testId,
            String deviceIdentifier,
            String status,
            Instant startedAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO syncs (
                        sync_uuid,
                        user_id,
                        test_id,
                        device_identifier,
                        direction,
                        sync_status,
                        started_at
                    ) VALUES (?, ?, ?, ?, 'upload', ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, syncUuid);
            statement.setLong(2, userId);
            statement.setLong(3, testId);
            statement.setString(4, deviceIdentifier);
            statement.setString(5, status);
            statement.setTimestamp(6, timestamp(startedAt));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public void updateSyncStatus(long syncId, String status, Instant completedAt, String errorMessage) {
        jdbcTemplate.update(
                """
                UPDATE syncs
                   SET sync_status = ?,
                       completed_at = ?,
                       error_message = ?
                 WHERE sync_id = ?
                """,
                status,
                timestamp(completedAt),
                errorMessage,
                syncId
        );
    }

    public Optional<Long> findSyncItemId(long syncId, String resultUuid) {
        return queryLong(
                "SELECT sync_item_id FROM sync_items WHERE sync_id = ? AND result_uuid = ?",
                syncId,
                resultUuid
        );
    }

    public long insertSyncItem(long syncId, String resultUuid, String syncAction) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO sync_items (
                        sync_id,
                        result_uuid,
                        sync_action,
                        sync_status
                    ) VALUES (?, ?, ?, 'pending')
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, syncId);
            statement.setString(2, resultUuid);
            statement.setString(3, syncAction);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public void updateSyncItem(
            long syncItemId,
            Long testResultId,
            String syncAction,
            String status,
            String errorCode,
            String errorMessage,
            Instant syncedAt
    ) {
        jdbcTemplate.update(
                """
                UPDATE sync_items
                   SET test_result_id = ?,
                       sync_action = ?,
                       sync_status = ?,
                       error_code = ?,
                       error_message = ?,
                       synced_at = ?
                 WHERE sync_item_id = ?
                """,
                testResultId,
                syncAction,
                status,
                errorCode,
                errorMessage,
                syncedAt == null ? null : timestamp(syncedAt),
                syncItemId
        );
    }

    public Optional<Long> findTestResultIdByUuid(String resultUuid) {
        return queryLong("SELECT test_result_id FROM test_results WHERE result_uuid = ?", resultUuid);
    }

    public long insertTestResult(
            String resultUuid,
            long testId,
            long classListId,
            int attemptNumber,
            BigDecimal totalScore,
            BigDecimal maxScore,
            int itemsEvaluated,
            Instant checkedAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO test_results (
                        result_uuid,
                        test_id,
                        class_list_id,
                        attempt_number,
                        total_score,
                        max_score,
                        items_evaluated,
                        checked_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, resultUuid);
            statement.setLong(2, testId);
            statement.setLong(3, classListId);
            statement.setInt(4, attemptNumber);
            statement.setBigDecimal(5, totalScore);
            statement.setBigDecimal(6, maxScore);
            statement.setInt(7, itemsEvaluated);
            statement.setTimestamp(8, timestamp(checkedAt));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public void updateTestResult(
            long testResultId,
            long classListId,
            int attemptNumber,
            BigDecimal totalScore,
            BigDecimal maxScore,
            int itemsEvaluated,
            Instant checkedAt
    ) {
        jdbcTemplate.update(
                """
                UPDATE test_results
                   SET class_list_id = ?,
                       attempt_number = ?,
                       total_score = ?,
                       max_score = ?,
                       items_evaluated = ?,
                       checked_at = ?
                 WHERE test_result_id = ?
                """,
                classListId,
                attemptNumber,
                totalScore,
                maxScore,
                itemsEvaluated,
                timestamp(checkedAt),
                testResultId
        );
    }

    public Optional<Long> findScanSessionIdByUuid(String scanUuid) {
        return queryLong("SELECT scan_session_id FROM scan_sessions WHERE scan_uuid = ?", scanUuid);
    }

    public long insertScanSession(
            String scanUuid,
            long userId,
            String deviceIdentifier,
            String templateVersion,
            String scannerVersion,
            String imageHash,
            String scanStatus,
            Instant scannedAt,
            Instant verifiedAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO scan_sessions (
                        scan_uuid,
                        scanned_by_user_id,
                        verified_by_user_id,
                        device_identifier,
                        template_version,
                        scanner_version,
                        image_hash,
                        scan_status,
                        scanned_at,
                        verified_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, scanUuid);
            statement.setLong(2, userId);
            statement.setLong(3, userId);
            statement.setString(4, deviceIdentifier);
            statement.setString(5, templateVersion);
            statement.setString(6, scannerVersion);
            statement.setString(7, imageHash);
            statement.setString(8, scanStatus);
            statement.setTimestamp(9, timestamp(scannedAt));
            statement.setTimestamp(10, timestamp(verifiedAt));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public void updateScanSession(
            long scanSessionId,
            long userId,
            String deviceIdentifier,
            String templateVersion,
            String scannerVersion,
            String imageHash,
            String scanStatus,
            Instant scannedAt,
            Instant verifiedAt
    ) {
        jdbcTemplate.update(
                """
                UPDATE scan_sessions
                   SET verified_by_user_id = ?,
                       device_identifier = ?,
                       template_version = ?,
                       scanner_version = ?,
                       image_hash = ?,
                       scan_status = ?,
                       scanned_at = ?,
                       verified_at = ?
                 WHERE scan_session_id = ?
                """,
                userId,
                deviceIdentifier,
                templateVersion,
                scannerVersion,
                imageHash,
                scanStatus,
                timestamp(scannedAt),
                timestamp(verifiedAt),
                scanSessionId
        );
    }

    public void upsertDetection(
            long scanSessionId,
            long questionId,
            String detectedOption,
            BigDecimal confidenceScore,
            String detectionStatus,
            String verificationStatus,
            String rawMark,
            Instant detectedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO omr_detections (
                    scan_session_id,
                    question_id,
                    detected_option,
                    confidence_score,
                    detection_status,
                    verification_status,
                    raw_mark,
                    detected_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    detected_option = VALUES(detected_option),
                    confidence_score = VALUES(confidence_score),
                    detection_status = VALUES(detection_status),
                    verification_status = VALUES(verification_status),
                    raw_mark = VALUES(raw_mark),
                    detected_at = VALUES(detected_at)
                """,
                scanSessionId,
                questionId,
                detectedOption,
                confidenceScore,
                detectionStatus,
                verificationStatus,
                rawMark,
                timestamp(detectedAt)
        );
    }

    public boolean scanLinkedToResult(long testResultId, long scanSessionId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM test_result_scans
                 WHERE test_result_id = ?
                   AND scan_session_id = ?
                """,
                Integer.class,
                testResultId,
                scanSessionId
        );
        return count != null && count > 0;
    }

    public void supersedeSelectedScans(long testResultId, long decidedByUserId) {
        jdbcTemplate.update(
                """
                UPDATE test_result_scans
                   SET link_status = 'superseded',
                       decided_by_user_id = ?,
                       decision_reason = 'Replaced by a retry or recapture upload.'
                 WHERE test_result_id = ?
                   AND link_status = 'selected'
                """,
                decidedByUserId,
                testResultId
        );
    }

    public void insertSelectedScan(long testResultId, long scanSessionId, long decidedByUserId) {
        jdbcTemplate.update(
                """
                INSERT INTO test_result_scans (
                    test_result_id,
                    scan_session_id,
                    link_status,
                    decided_by_user_id
                ) VALUES (?, ?, 'selected', ?)
                """,
                testResultId,
                scanSessionId,
                decidedByUserId
        );
    }

    public void selectExistingScan(long testResultId, long scanSessionId, long decidedByUserId) {
        jdbcTemplate.update(
                """
                UPDATE test_result_scans
                   SET link_status = 'selected',
                       decided_by_user_id = ?,
                       decision_reason = NULL
                 WHERE test_result_id = ?
                   AND scan_session_id = ?
                """,
                decidedByUserId,
                testResultId,
                scanSessionId
        );
    }

    public Optional<Long> findAnswerIdByUuid(String answerUuid) {
        return queryLong("SELECT student_answer_id FROM student_answers WHERE answer_uuid = ?", answerUuid);
    }

    public void insertStudentAnswer(
            long testResultId,
            long questionId,
            long verifiedByUserId,
            String answerUuid,
            String captureSource,
            Instant verifiedAt,
            String selectedOption,
            String answerStatus,
            boolean isCorrect,
            BigDecimal pointsEarned,
            String correctionReason
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO student_answers (
                    test_result_id,
                    question_id,
                    verified_by_user_id,
                    answer_uuid,
                    capture_source,
                    verified_at,
                    selected_option,
                    answer_status,
                    is_correct,
                    points_earned,
                    correction_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                testResultId,
                questionId,
                verifiedByUserId,
                answerUuid,
                captureSource,
                timestamp(verifiedAt),
                selectedOption,
                answerStatus,
                isCorrect,
                pointsEarned,
                correctionReason
        );
    }

    public void updateStudentAnswer(
            long studentAnswerId,
            long verifiedByUserId,
            String captureSource,
            Instant verifiedAt,
            String selectedOption,
            String answerStatus,
            boolean isCorrect,
            BigDecimal pointsEarned,
            String correctionReason
    ) {
        jdbcTemplate.update(
                """
                UPDATE student_answers
                   SET verified_by_user_id = ?,
                       capture_source = ?,
                       verified_at = ?,
                       selected_option = ?,
                       answer_status = ?,
                       is_correct = ?,
                       points_earned = ?,
                       correction_reason = ?
                 WHERE student_answer_id = ?
                """,
                verifiedByUserId,
                captureSource,
                timestamp(verifiedAt),
                selectedOption,
                answerStatus,
                isCorrect,
                pointsEarned,
                correctionReason,
                studentAnswerId
        );
    }

    private Optional<Long> queryLong(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args)
                .stream()
                .findFirst();
    }

    private long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Expected generated key was not returned.");
        }
        return key.longValue();
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
