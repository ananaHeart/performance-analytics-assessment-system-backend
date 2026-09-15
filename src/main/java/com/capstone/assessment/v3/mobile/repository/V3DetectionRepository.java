package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("v3")
public class V3DetectionRepository {
    private final JdbcTemplate jdbc;
    public V3DetectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Receipt> receipt(String operation) {
        return jdbc.query("SELECT scan_page_id, request_hash, response_json FROM mobile_detection_uploads WHERE operation_uuid=? FOR UPDATE",
                (rs, n) -> new Receipt(rs.getLong(1), rs.getString(2), rs.getString(3)), operation).stream().findFirst();
    }

    /** Owner lock is acquired by the service first. This query retains ownership on historical replay. */
    public Optional<Page> lockPage(V3AuthenticatedUser user, String uuid, boolean replay) {
        return jdbc.query("""
                SELECT p.scan_page_id, p.scan_session_id, p.answer_sheet_page_id, s.answer_sheet_version_id,
                       r.test_result_id, r.result_uuid, r.mobile_revision, r.result_status,
                       s.test_assignment_id, d.test_id, p.page_status, s.scan_status, link.link_status,
                       v.test_version_number, t.version_number
                FROM scan_pages p
                JOIN mobile_scan_uploads upload ON upload.backend_scan_page_id=p.scan_page_id AND upload.upload_state='committed'
                JOIN scan_sessions s ON s.scan_session_id=p.scan_session_id
                JOIN test_result_scans link ON link.scan_session_id=s.scan_session_id
                JOIN test_results r ON r.test_result_id=link.test_result_id
                    AND r.test_assignment_id=s.test_assignment_id AND r.class_list_id=s.class_list_id
                JOIN test_assignments d ON d.test_assignment_id=s.test_assignment_id
                JOIN class_assignments a ON a.class_assignment_id=d.class_assignment_id
                JOIN classes c ON c.class_id=a.class_id
                JOIN sections section ON section.section_id=c.section_id
                JOIN class_lists membership ON membership.class_list_id=s.class_list_id AND membership.class_id=c.class_id
                JOIN students student ON student.student_id=membership.student_id
                JOIN tests t ON t.test_id=d.test_id
                JOIN answer_sheet_pages manifest ON manifest.answer_sheet_page_id=p.answer_sheet_page_id
                JOIN answer_sheet_versions v ON v.answer_sheet_version_id=manifest.answer_sheet_version_id
                    AND v.answer_sheet_version_id=s.answer_sheet_version_id AND v.test_assignment_id=d.test_assignment_id
                WHERE p.scan_page_uuid=? AND a.user_id=? AND s.scanned_by_user_id=?
                    AND upload.teacher_user_id=? AND upload.school_id=?
                    AND t.school_id=? AND section.school_id=? AND student.school_id=?
                    AND (?=TRUE OR (a.status='active' AND c.status='active' AND membership.enrollment_status='enrolled'
                        AND student.status='active' AND t.status='active' AND d.assignment_status <> 'archived'
                        AND v.generation_status='ready' AND manifest.page_status='ready'))
                FOR UPDATE
                """, (rs,n) -> new Page(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), rs.getString(6), rs.getLong(7), rs.getString(8), rs.getLong(9), rs.getLong(10),
                        rs.getString(11), rs.getString(12), rs.getString(13), rs.getInt(14), rs.getInt(15)),
                uuid, user.userId(), user.userId(), user.userId(), user.schoolId(), user.schoolId(), user.schoolId(), user.schoolId(), replay)
                .stream().findFirst();
    }

    public Optional<Region> region(Page page, Detection detection) {
        return jdbc.query("""
                SELECT g.answer_sheet_region_id, q.question_id, qt.question_type_code, g.region_type, g.geometry_snapshot
                FROM answer_sheet_regions g
                JOIN questions q ON q.question_id=g.question_id AND q.test_part_id=g.test_part_id AND q.question_type_id=g.question_type_id
                JOIN question_types qt ON qt.question_type_id=q.question_type_id
                JOIN test_parts part ON part.test_part_id=q.test_part_id
                WHERE g.region_uuid=? AND q.question_uuid=? AND g.answer_sheet_page_id=?
                    AND g.answer_sheet_version_id=? AND part.test_id=? FOR UPDATE
                """, (rs,n) -> new Region(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5)),
                detection.regionUuid(), detection.questionUuid(), page.manifestPageId(), page.sheetId(), page.testId())
                .stream().findFirst();
    }

    public List<String> options(long questionId) {
        return jdbc.queryForList("SELECT option_key FROM question_options WHERE question_id=? AND is_active=TRUE ORDER BY option_key FOR UPDATE", String.class, questionId);
    }

    public boolean occupied(String uuid, long pageId, long regionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM omr_detections WHERE detection_uuid=? OR (scan_page_id=? AND answer_sheet_region_id=?)",
                Integer.class, uuid, pageId, regionId) != 0;
    }

    public long insert(Page page, Region region, Detection row, String raw) {
        return insertKey("omr_detection_id", """
                INSERT INTO omr_detections(detection_uuid,scan_session_id,scan_page_id,answer_sheet_region_id,
                    question_id,detected_option,confidence_score,detection_status,raw_mark)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, row.detectionUuid(),page.scanId(),page.pageId(),region.id(),region.questionId(),row.detectedOption(),
                row.confidence().setScale(4, RoundingMode.HALF_UP),row.detectionStatus(),raw);
    }

    public boolean syncExists(String uuid) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM syncs WHERE sync_uuid=?", Integer.class, uuid) != 0;
    }

    public long insertSync(V3AuthenticatedUser user, Page page, String uuid, String hash) {
        return insertKey("sync_id", """
                INSERT INTO syncs(sync_uuid,user_id,test_assignment_id,direction,sync_status,payload_hash,request_item_count,completed_at)
                VALUES (?,?,?,'upload','success',?,1,CURRENT_TIMESTAMP)
                """, uuid,user.userId(),page.assignmentId(),hash);
    }

    public void finish(Page page, long syncId, String operation, String hash, String response) {
        jdbc.update("""
                INSERT INTO sync_items(sync_id,result_uuid,test_result_id,sync_action,sync_status,attempt_count,last_attempt_at,processed_at,synced_at)
                VALUES (?,?,?,'upsert','success',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """,syncId,page.resultUuid(),page.resultId());
        jdbc.update("UPDATE test_results SET mobile_revision=mobile_revision+1 WHERE test_result_id=?",page.resultId());
        jdbc.update("INSERT INTO mobile_detection_uploads(operation_uuid,sync_id,scan_page_id,request_hash,response_json) VALUES (?,?,?,?,?)",
                operation,syncId,page.pageId(),hash,response);
    }

    private long insertKey(String key, String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement=connection.prepareStatement(sql,new String[]{key});
            for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); return statement; },keys);
        return keys.getKey().longValue();
    }
    public record Receipt(long pageId,String hash,String response) { }
    public record Region(long id,long questionId,String type,String regionType,String geometry) { }
    public record Page(long pageId,long scanId,long manifestPageId,long sheetId,long resultId,String resultUuid,long revision,
            String resultStatus,long assignmentId,long testId,String pageStatus,String scanStatus,String linkStatus,int sheetVersion,int testVersion) { }
}
