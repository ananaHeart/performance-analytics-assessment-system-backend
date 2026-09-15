package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage.OriginalImage;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
@Profile("v3")
public class V3ScanPageRepository {
    private final JdbcTemplate jdbc;

    public V3ScanPageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Call inside a transaction; the owned delivery/membership locks serialize attempt allocation. */
    public Optional<CaptureContext> lockContext(V3AuthenticatedUser user, V3ScanPageUploadMetadata request) {
        return lockContext(user, request, false);
    }

    public Optional<CaptureContext> lockContext(V3AuthenticatedUser user, V3ScanPageUploadMetadata request, boolean historical) {
        return jdbc.query("""
                SELECT delivery.test_assignment_id, delivery.test_id, membership.student_id,
                       delivery.assignment_status, delivery.open_at, delivery.close_at, delivery.allow_late_capture,
                       sheet.answer_sheet_version_id, sheet.test_version_number, assessment.version_number,
                       sheet.total_questions, assessment.total_items, sheet.total_pages,
                       page.answer_sheet_page_id, page.page_number, page.total_pages AS page_total_pages,
                       page.omr_template_id, page.qr_payload, page.qr_payload_hash,
                       template.template_code, template.template_version, template.minimum_scanner_version,
                       template.template_status, size.paper_size_code
                  FROM test_assignments delivery
                  JOIN class_assignments assignment ON assignment.class_assignment_id = delivery.class_assignment_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  JOIN statuses account_status ON account_status.status_id = teacher.status_id
                  JOIN classes cohort ON cohort.class_id = assignment.class_id
                  JOIN sections section ON section.section_id = cohort.section_id
                  JOIN class_lists membership ON membership.class_id = cohort.class_id
                  JOIN students student ON student.student_id = membership.student_id
                  JOIN tests assessment ON assessment.test_id = delivery.test_id
                  JOIN answer_sheet_versions sheet ON sheet.test_assignment_id = delivery.test_assignment_id
                  JOIN answer_sheet_pages page ON page.answer_sheet_version_id = sheet.answer_sheet_version_id
                  JOIN omr_templates template ON template.omr_template_id = page.omr_template_id
                  JOIN paper_sizes size ON size.paper_size_id = sheet.paper_size_id
                 WHERE delivery.assignment_uuid = ? AND sheet.answer_sheet_uuid = ? AND page.page_uuid = ?
                   AND membership.class_list_id = ? AND assignment.user_id = ?
                   AND teacher.school_id = ? AND assessment.school_id = ?
                   AND student.school_id = ? AND section.school_id = ?
                   AND account_status.status_name = 'active'
                   AND (? = TRUE OR (assignment.status = 'active'
                   AND cohort.status = 'active' AND membership.enrollment_status = 'enrolled'
                   AND student.status = 'active' AND assessment.status IN ('active', 'completed')
                   AND sheet.generation_status = 'ready' AND page.page_status = 'ready'))
                 FOR UPDATE
                """, (rs, row) -> new CaptureContext(
                rs.getLong("test_assignment_id"), rs.getLong("test_id"), rs.getLong("student_id"),
                rs.getString("assignment_status"), instant(rs.getTimestamp("open_at")),
                instant(rs.getTimestamp("close_at")), rs.getBoolean("allow_late_capture"),
                rs.getLong("answer_sheet_version_id"), rs.getInt("test_version_number"), rs.getInt("version_number"),
                rs.getInt("total_questions"), rs.getInt("total_items"), rs.getInt("total_pages"),
                rs.getLong("answer_sheet_page_id"), rs.getInt("page_number"), rs.getInt("page_total_pages"),
                rs.getLong("omr_template_id"), rs.getString("qr_payload"), rs.getString("qr_payload_hash"),
                rs.getString("template_code"), rs.getString("template_version"),
                rs.getString("minimum_scanner_version"), rs.getString("template_status"),
                rs.getString("paper_size_code")), request.assignmentUuid(), request.answerSheetUuid(),
                request.pageUuid(), request.classListId(), user.userId(), user.schoolId(), user.schoolId(),
                user.schoolId(), user.schoolId(), historical).stream().findFirst();
    }

    public ContentSnapshot contentSnapshot(CaptureContext context) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS item_count, COALESCE(SUM(question.maximum_points), 0) AS maximum_score,
                       SUM(CASE WHEN type.question_type_code = 'multiple_choice' THEN 1 ELSE 0 END) AS mc_count
                  FROM questions question
                  JOIN test_parts part ON part.test_part_id = question.test_part_id
                  JOIN question_types type ON type.question_type_id = question.question_type_id
                 WHERE part.test_id = ?
                """, (rs, row) -> new ContentSnapshot(rs.getInt("item_count"), rs.getInt("mc_count"),
                rs.getBigDecimal("maximum_score")), context.testId());
    }

    public int mappedQuestions(CaptureContext context) {
        return jdbc.queryForObject("""
                SELECT COUNT(DISTINCT region.question_id)
                  FROM answer_sheet_regions region
                  JOIN questions question ON question.question_id = region.question_id
                  JOIN test_parts part ON part.test_part_id = question.test_part_id
                 WHERE region.answer_sheet_page_id = ? AND part.test_id = ?
                """, Integer.class, context.pageId(), context.testId());
    }

    public boolean completeDynamicMapping(CaptureContext context) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM answer_sheet_versions s
                WHERE s.answer_sheet_version_id=? AND s.total_questions=(
                    SELECT COUNT(*) FROM answer_sheet_regions r WHERE r.answer_sheet_version_id=s.answer_sheet_version_id)
                  AND s.total_questions=(SELECT COUNT(DISTINCT r.question_id) FROM answer_sheet_regions r
                      JOIN questions q ON q.question_id=r.question_id JOIN test_parts p ON p.test_part_id=q.test_part_id
                      WHERE r.answer_sheet_version_id=s.answer_sheet_version_id AND p.test_id=?)
                  AND s.total_pages=(SELECT COUNT(*) FROM answer_sheet_pages p WHERE p.answer_sheet_version_id=s.answer_sheet_version_id AND p.page_status='ready')
                """,Integer.class,context.sheetId(),context.testId())==1
                && dynamicPageHashMatches(context);
    }
    private boolean dynamicPageHashMatches(CaptureContext context) {
        try {
            String encoded = new com.fasterxml.jackson.databind.ObjectMapper().readTree(context.qrPayload()).path("gh").asText();
            String hash = java.util.HexFormat.of().formatHex(java.util.Base64.getUrlDecoder().decode(encoded));
            return hash.equals(jdbc.queryForObject("SELECT page_geometry_hash FROM answer_sheet_pages WHERE answer_sheet_page_id=?",String.class,context.pageId()));
        } catch (java.io.IOException | IllegalArgumentException e) { return false; }
    }

    public boolean scanPageExists(String uuid) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM scan_pages WHERE scan_page_uuid = ?", Integer.class, uuid) != 0;
    }

    public Optional<ResultRow> lockResult(String uuid) {
        return jdbc.query("""
                SELECT test_result_id, test_assignment_id, class_list_id, result_status
                  FROM test_results WHERE result_uuid = ? FOR UPDATE
                """, (rs, row) -> new ResultRow(rs.getLong("test_result_id"), rs.getLong("test_assignment_id"),
                rs.getLong("class_list_id"), rs.getString("result_status")), uuid).stream().findFirst();
    }

    public long insertResult(V3ScanPageUploadMetadata request, CaptureContext context, BigDecimal maximumScore) {
        Integer attempt = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_number), 0) + 1 FROM test_results
                 WHERE test_assignment_id = ? AND class_list_id = ?
                """, Integer.class, context.assignmentId(), request.classListId());
        return insert("test_result_id", """
                INSERT INTO test_results (result_uuid, test_assignment_id, class_list_id, attempt_number,
                                          total_score, max_score, items_evaluated, result_status)
                VALUES (?, ?, ?, ?, 0, ?, 0, 'draft')
                """, request.resultUuid(), context.assignmentId(), request.classListId(), attempt, maximumScore);
    }

    public Optional<ScanRow> lockScan(String uuid) {
        return jdbc.query("""
                SELECT scan.scan_session_id, scan.test_assignment_id, scan.class_list_id,
                       scan.answer_sheet_version_id, scan.scanned_by_user_id, scan.scan_status,
                       link.test_result_id, link.link_status
                  FROM scan_sessions scan
                  LEFT JOIN test_result_scans link ON link.scan_session_id = scan.scan_session_id
                 WHERE scan.scan_uuid = ? FOR UPDATE
                """, (rs, row) -> new ScanRow(rs.getLong("scan_session_id"), rs.getLong("test_assignment_id"),
                rs.getLong("class_list_id"), rs.getLong("answer_sheet_version_id"), rs.getLong("scanned_by_user_id"),
                rs.getString("scan_status"), rs.getLong("test_result_id"), rs.getString("link_status")), uuid)
                .stream().findFirst();
    }

    public boolean hasSelectedScan(long resultId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_result_scans WHERE test_result_id = ? AND link_status = 'selected'
                """, Integer.class, resultId) != 0;
    }

    public long insertScan(V3AuthenticatedUser user, V3ScanPageUploadMetadata request, CaptureContext context,
                           long resultId) {
        long scanId = insert("scan_session_id", """
                INSERT INTO scan_sessions (scan_uuid, answer_sheet_version_id, omr_template_id, test_assignment_id,
                    class_list_id, expected_page_count, captured_page_count, scanned_by_user_id,
                    template_version, scanner_version, scan_status, scanned_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 'captured', ?)
                """, request.scanUuid(), context.sheetId(), context.templateId(), context.assignmentId(),
                request.classListId(), context.totalPages(), user.userId(), context.templateVersion(),
                request.scannerVersion(), Timestamp.from(request.capturedAt()));
        jdbc.update("""
                INSERT INTO test_result_scans (test_result_id, scan_session_id, link_status, decided_by_user_id,
                                               decision_reason)
                VALUES (?, ?, 'selected', ?, 'Initial capture link; teacher verification pending')
                """, resultId, scanId, user.userId());
        return scanId;
    }

    public Optional<SyncRow> lockSync(String uuid) {
        return jdbc.query("""
                SELECT sync_id, user_id, test_assignment_id, direction, payload_hash FROM syncs
                 WHERE sync_uuid = ? FOR UPDATE
                """, (rs, row) -> new SyncRow(rs.getLong("sync_id"), rs.getLong("user_id"),
                rs.getLong("test_assignment_id"), rs.getString("direction"), rs.getString("payload_hash")), uuid)
                .stream().findFirst();
    }

    public long insertSync(V3AuthenticatedUser user, V3ScanPageUploadMetadata request, CaptureContext context,
                           long resultId, String groupHash) {
        long syncId = insertSyncIntent(user, request, context, groupHash);
        ensureSyncItem(syncId, request.resultUuid(), resultId);
        return syncId;
    }

    public long insertSyncIntent(V3AuthenticatedUser user, V3ScanPageUploadMetadata request, CaptureContext context,
                                 String groupHash) {
        long syncId = insert("sync_id", """
                INSERT INTO syncs (sync_uuid, user_id, test_assignment_id, direction, sync_status,
                                   payload_hash, request_item_count)
                VALUES (?, ?, ?, 'upload', 'in_progress', ?, 1)
                """, request.syncUuid(), user.userId(), context.assignmentId(), groupHash);
        return syncId;
    }

    public void ensureSyncItem(long syncId, String resultUuid, long resultId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sync_items WHERE sync_id = ? AND result_uuid = ?",
                Integer.class, syncId, resultUuid) == 0) {
            jdbc.update("""
                    INSERT INTO sync_items (sync_id, result_uuid, test_result_id, sync_action, sync_status)
                    VALUES (?, ?, ?, 'upsert', 'pending')
                    """, syncId, resultUuid, resultId);
        }
    }

    public long insertPage(long scanId, V3ScanPageUploadMetadata request, CaptureContext context) {
        return insert("scan_page_id", """
                INSERT INTO scan_pages (scan_page_uuid, scan_session_id, answer_sheet_page_id, omr_template_id,
                    page_number, capture_number, scanner_version, qr_payload, qr_payload_hash, image_hash,
                    page_status, captured_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'captured', ?)
                """, request.scanPageUuid(), scanId, context.pageId(), context.templateId(), request.pageNumber(),
                request.captureNumber(), request.scannerVersion(), context.qrPayload(), context.qrHash(),
                request.imageHash(), Timestamp.from(request.capturedAt()));
    }

    /** Replaces only an unaccepted page with a committed teacher rescan decision, under the owner/context locks. */
    public long prepareRescan(V3AuthenticatedUser user,V3ScanPageUploadMetadata r,long result,long scan,CaptureContext c){
        var header=jdbc.queryForMap("SELECT scored_at,mobile_revision FROM test_results WHERE test_result_id=? FOR UPDATE",result);
        if(header.get("scored_at")!=null || jdbc.queryForObject("SELECT COUNT(*) FROM student_answers a JOIN answer_sheet_regions r ON r.question_id=a.question_id WHERE a.test_result_id=? AND r.answer_sheet_page_id=?",Integer.class,result,c.pageId())>0)
            throw rescanConflict("RESCAN_RESULT_LOCKED","Accepted answers on this page or an official score require a separate replacement result.");
        long revision=((Number)header.get("mobile_revision")).longValue();
        if(revision>=9007199254740991L)throw rescanConflict("RESULT_REVISION_EXHAUSTED","Result revision cannot advance.");
        var current=jdbc.queryForList("SELECT scan_page_id,answer_sheet_page_id,capture_number,page_status FROM scan_pages WHERE scan_session_id=? AND page_number=? ORDER BY capture_number DESC FOR UPDATE",scan,r.pageNumber());
        if(current.isEmpty())throw rescanConflict("RESCAN_PREDECESSOR_REQUIRED","A current rescan-requested capture is required.");
        var old=current.get(0);long page=((Number)old.get("scan_page_id")).longValue();
        if(!"rescan_requested".equals(old.get("page_status")) || ((Number)old.get("answer_sheet_page_id")).longValue()!=c.pageId()
                || ((Number)old.get("capture_number")).intValue()+1!=r.captureNumber())
            throw rescanConflict("RESCAN_PREDECESSOR_CONFLICT","Capture number must follow the current rescan-requested page on the same immutable sheet.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM scan_verifications WHERE scan_page_id=? AND scan_session_id=? AND verified_by_user_id=? AND verification_action='rescan_requested' AND mobile_operation_uuid IS NOT NULL AND reason_code IS NOT NULL",Integer.class,page,scan,user.userId())!=1)
            throw rescanConflict("RESCAN_DECISION_REQUIRED","A committed teacher rescan request is required.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM mobile_scan_uploads WHERE backend_scan_page_id=? AND upload_state='committed'",Integer.class,page)!=1)
            throw rescanConflict("RESCAN_PREDECESSOR_REQUIRED","The prior original capture must have a committed receipt.");
        jdbc.update("UPDATE scan_pages SET page_status='superseded' WHERE scan_page_id=?",page);
        return page;
    }
    public void linkRescan(V3AuthenticatedUser user,V3ScanPageUploadMetadata r,long result,long scan,long page,long predecessor){
        jdbc.update("UPDATE scan_pages SET supersedes_scan_page_id=? WHERE scan_page_id=?",predecessor,page);
        jdbc.update("UPDATE scan_sessions SET scan_status='captured',verified_by_user_id=NULL,verified_at=NULL WHERE scan_session_id=?",scan);
        jdbc.update("UPDATE test_results SET mobile_revision=mobile_revision+1 WHERE test_result_id=?",result);
        jdbc.update("INSERT INTO audit_logs(audit_uuid,user_id,action,entity_type,entity_id,outcome,details) VALUES(?,?,'V3_MOBILE_PAGE_RESCANNED','scan_page',?,'success',JSON_OBJECT('predecessorScanPageId',?,'syncUuid',?))",
                java.util.UUID.randomUUID().toString(),user.userId(),r.scanPageUuid(),predecessor,r.syncUuid());
    }
    private com.capstone.assessment.v3.auth.exception.V3AuthException rescanConflict(String code,String message){
        return new com.capstone.assessment.v3.auth.exception.V3AuthException(code,message,org.springframework.http.HttpStatus.CONFLICT);
    }

    public void insertOriginal(long scanId, long pageId, OriginalImage image, Instant capturedAt) {
        jdbc.update("""
                INSERT INTO answer_attachments (attachment_uuid, scan_session_id, scan_page_id, attachment_type,
                    storage_provider, storage_key, mime_type, file_size_bytes, content_hash, captured_at)
                VALUES (?, ?, ?, 'original_page', ?, ?, ?, ?, ?, ?)
                """, image.attachmentUuid(), scanId, pageId, image.storageProvider(), image.storageKey(),
                image.mimeType(), image.fileSizeBytes(), image.contentHash(), Timestamp.from(capturedAt));
    }

    public void recordCapturedPage(long scanId) {
        jdbc.update("UPDATE scan_sessions SET captured_page_count = captured_page_count + 1 WHERE scan_session_id = ?",
                scanId);
    }

    public void completeScanSync(long syncId, String resultUuid) {
        jdbc.update("""
                UPDATE sync_items SET sync_status = 'success', attempt_count = attempt_count + 1,
                    last_attempt_at = CURRENT_TIMESTAMP, processed_at = CURRENT_TIMESTAMP, synced_at = CURRENT_TIMESTAMP,
                    error_code = NULL, error_message = NULL WHERE sync_id = ? AND result_uuid = ?
                """, syncId, resultUuid);
        jdbc.update("UPDATE syncs SET sync_status = 'success', completed_at = CURRENT_TIMESTAMP WHERE sync_id = ?", syncId);
    }

    private long insert(String keyColumn, String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{keyColumn});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("An inserted scan record did not return its central ID.");
        return key.longValue();
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    public record CaptureContext(long assignmentId, long testId, long studentId, String assignmentStatus,
            Instant openAt, Instant closeAt, boolean allowLateCapture, long sheetId, int sheetTestVersion,
            int testVersion, int totalQuestions, int testTotalItems, int totalPages, long pageId, int pageNumber,
            int pageTotalPages, long templateId, String qrPayload, String qrHash, String templateCode,
            String templateVersion, String minimumScannerVersion, String templateStatus, String paperSize) { }
    public record ContentSnapshot(int count, int multipleChoiceCount, BigDecimal maximumScore) { }
    public record ResultRow(long id, long assignmentId, long classListId, String status) { }
    public record ScanRow(long id, long assignmentId, long classListId, long sheetId, long teacherId,
                          String status, long resultId, String linkStatus) { }
    public record SyncRow(long id, long teacherId, long assignmentId, String direction, String groupHash) { }
}
