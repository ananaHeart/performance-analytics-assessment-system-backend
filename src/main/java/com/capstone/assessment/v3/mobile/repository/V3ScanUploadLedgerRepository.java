package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage.OriginalImage;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("v3")
public class V3ScanUploadLedgerRepository {
    private final JdbcTemplate jdbc;
    public V3ScanUploadLedgerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** All ingestion/recovery transactions take this owner lock before a ledger/context lock. */
    public Optional<V3AuthenticatedUser> lockOwner(long teacherId, String schoolId) {
        return jdbc.query("""
                SELECT u.user_id FROM users u
                JOIN statuses s ON s.status_id = u.status_id JOIN roles r ON r.role_id = u.role_id
                WHERE u.user_id = ? AND u.school_id = ? AND s.status_name = 'active' AND r.role_name = 'teacher'
                FOR UPDATE
                """, (rs, row) -> new V3AuthenticatedUser(rs.getLong(1), schoolId, "", "teacher", "active", ""),
                teacherId, schoolId).stream().findFirst();
    }

    public Optional<Upload> find(String pageUuid, boolean lock) {
        return jdbc.query("SELECT * FROM mobile_scan_uploads WHERE scan_page_uuid = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Upload(rs.getString("scan_page_uuid"), rs.getLong("teacher_user_id"),
                        rs.getString("school_id"), rs.getString("request_hash"), rs.getString("request_json"),
                        rs.getString("upload_state"), rs.getObject("backend_scan_page_id") == null ? null : rs.getLong("backend_scan_page_id"),
                        rs.getString("receipt_page_status"), new OriginalImage(rs.getString("attachment_uuid"), "local",
                        rs.getString("storage_key"), "image/jpeg", rs.getLong("file_size_bytes"), rs.getString("content_hash"),
                        rs.getInt("width_pixels"), rs.getInt("height_pixels"))), pageUuid).stream().findFirst();
    }

    public void insert(V3AuthenticatedUser user, V3ScanPageUploadMetadata request, String requestHash,
                       String json, OriginalImage image) {
        jdbc.update("""
                INSERT INTO mobile_scan_uploads (scan_page_uuid, teacher_user_id, school_id, sync_uuid,
                    request_hash, request_json, attachment_uuid, storage_key, file_size_bytes, content_hash,
                    width_pixels, height_pixels, upload_state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')
                """, request.scanPageUuid(), user.userId(), user.schoolId(), request.syncUuid(), requestHash, json,
                image.attachmentUuid(), image.storageKey(), image.fileSizeBytes(), image.contentHash(), image.width(), image.height());
    }

    public void committed(String pageUuid, long pageId) {
        jdbc.update("""
                UPDATE mobile_scan_uploads SET upload_state = 'committed', backend_scan_page_id = ?,
                    receipt_page_status = 'captured', committed_at = CURRENT_TIMESTAMP,
                    attempt_count = attempt_count + 1, last_attempt_at = CURRENT_TIMESTAMP, last_error_code = NULL
                WHERE scan_page_uuid = ? AND upload_state = 'pending'
                """, pageId, pageUuid);
    }

    public void failed(String pageUuid, String code) {
        jdbc.update("""
                UPDATE mobile_scan_uploads SET attempt_count = attempt_count + 1,
                    last_attempt_at = CURRENT_TIMESTAMP, last_error_code = ?
                WHERE scan_page_uuid = ? AND upload_state = 'pending'
                """, code, pageUuid);
    }

    public List<String> pending(int limit) {
        return jdbc.query("""
                SELECT scan_page_uuid FROM mobile_scan_uploads WHERE upload_state = 'pending'
                    AND (last_attempt_at IS NULL OR last_attempt_at < TIMESTAMPADD(SECOND, -60, CURRENT_TIMESTAMP))
                ORDER BY created_at, scan_page_uuid LIMIT ?
                """, (rs, row) -> rs.getString(1), limit);
    }

    public record Upload(String pageUuid, long teacherId, String schoolId, String requestHash,
                         String requestJson, String state, Long pageId, String pageStatus, OriginalImage image) { }
}
