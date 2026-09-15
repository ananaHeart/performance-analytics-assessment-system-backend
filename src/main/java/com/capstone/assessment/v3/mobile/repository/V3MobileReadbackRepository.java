package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3DetectionUploadResponse.IdMapping;
import com.capstone.assessment.v3.mobile.dto.V3MobileReadback.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Non-locking reads only; service supplies one repeatable-read snapshot per response. */
@Repository
@Profile("v3")
public class V3MobileReadbackRepository {
    private final JdbcTemplate jdbc;
    public V3MobileReadbackRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean active(V3AuthenticatedUser u) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM users u JOIN roles r ON r.role_id=u.role_id JOIN statuses s ON s.status_id=u.status_id
                WHERE u.user_id=? AND u.school_id=? AND r.role_name='teacher' AND s.status_name='active'
                """,Integer.class,u.userId(),u.schoolId())==1;
    }
    public Optional<ResultRow> result(V3AuthenticatedUser u,String uuid) {
        return jdbc.query("""
                SELECT r.*,d.assignment_uuid,d.test_id,cl.student_id,
                       f.mobile_revision AS receipt_revision,f.score_version AS receipt_version,f.response_json
                FROM test_results r JOIN test_assignments d ON d.test_assignment_id=r.test_assignment_id
                JOIN class_assignments a ON a.class_assignment_id=d.class_assignment_id
                JOIN classes c ON c.class_id=a.class_id JOIN sections section ON section.section_id=c.section_id
                JOIN tests t ON t.test_id=d.test_id JOIN class_lists cl ON cl.class_list_id=r.class_list_id AND cl.class_id=a.class_id
                JOIN students student ON student.student_id=cl.student_id
                LEFT JOIN mobile_result_finalizations f ON f.test_result_id=r.test_result_id
                WHERE r.result_uuid=? AND a.user_id=? AND t.school_id=? AND student.school_id=? AND section.school_id=?
                """,(rs,n)->new ResultRow(rs.getLong("test_result_id"),rs.getString("result_uuid"),rs.getString("assignment_uuid"),
                rs.getLong("test_assignment_id"),rs.getLong("test_id"),rs.getLong("class_list_id"),rs.getLong("student_id"),rs.getInt("attempt_number"),
                rs.getString("result_status"),rs.getLong("mobile_revision"),rs.getInt("score_version"),rs.getBigDecimal("total_score"),
                rs.getBigDecimal("max_score"),rs.getBigDecimal("percentage_snapshot"),rs.getInt("items_evaluated"),
                rs.getString("performance_status"),rs.getObject("performance_rule_set_id")==null?null:rs.getLong("performance_rule_set_id"),
                rs.getTimestamp("scored_at")==null?null:rs.getTimestamp("scored_at").toInstant(),
                rs.getObject("receipt_revision")==null?null:rs.getLong("receipt_revision"),
                rs.getObject("receipt_version")==null?null:rs.getInt("receipt_version"),rs.getString("response_json")),
                uuid,u.userId(),u.schoolId(),u.schoolId(),u.schoolId()).stream().findFirst();
    }
    public boolean dynamicResult(long result) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM test_result_scans l JOIN scan_sessions s ON s.scan_session_id=l.scan_session_id JOIN answer_sheet_versions v ON v.answer_sheet_version_id=s.answer_sheet_version_id WHERE l.test_result_id=? AND l.link_status='selected' AND v.manifest_version=2",Integer.class,result)>0;
    }
    public int committedManifestPages(String scanUuid) {
        return jdbc.queryForObject("SELECT COUNT(DISTINCT p.answer_sheet_page_id) FROM scan_sessions s JOIN scan_pages p ON p.scan_session_id=s.scan_session_id JOIN mobile_scan_uploads u ON u.backend_scan_page_id=p.scan_page_id WHERE s.scan_uuid=? AND p.page_status<>'superseded' AND u.upload_state='committed'",Integer.class,scanUuid);
    }
    public List<PageRow> pages(long result) {
        return jdbc.query("""
                SELECT s.scan_session_id,s.scan_uuid,p.scan_page_id,p.scan_page_uuid,p.page_status,
                       v.answer_sheet_version_id,v.answer_sheet_uuid,g.answer_sheet_page_id,g.page_uuid,
                       o.answer_attachment_id,o.attachment_uuid,l.link_status
                FROM test_result_scans l JOIN scan_sessions s ON s.scan_session_id=l.scan_session_id
                JOIN answer_sheet_versions v ON v.answer_sheet_version_id=s.answer_sheet_version_id
                JOIN scan_pages p ON p.scan_session_id=s.scan_session_id
                JOIN answer_sheet_pages g ON g.answer_sheet_page_id=p.answer_sheet_page_id
                LEFT JOIN answer_attachments o ON o.scan_page_id=p.scan_page_id AND o.scan_session_id=s.scan_session_id AND o.attachment_type='original_page'
                WHERE l.test_result_id=? ORDER BY s.scan_uuid,p.page_number,p.capture_number LIMIT 201
                """,(rs,n)->new PageRow(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getString(4),rs.getString(5),
                rs.getLong(6),rs.getString(7),rs.getLong(8),rs.getString(9),rs.getObject(10)==null?null:rs.getLong(10),rs.getString(11),rs.getString(12)),result);
    }
    public List<IdMapping> mappings(long result) {
        return jdbc.query("""
                SELECT 'student_answer' AS kind,answer_uuid AS uuid,student_answer_id AS id FROM student_answers WHERE test_result_id=?
                UNION SELECT 'omr_detection',d.detection_uuid,d.omr_detection_id FROM test_result_scans l
                  JOIN scan_pages p ON p.scan_session_id=l.scan_session_id JOIN omr_detections d ON d.scan_page_id=p.scan_page_id WHERE l.test_result_id=?
                UNION SELECT 'answer_sheet_region',g.region_uuid,g.answer_sheet_region_id FROM test_result_scans l
                  JOIN scan_pages p ON p.scan_session_id=l.scan_session_id JOIN answer_sheet_regions g ON g.answer_sheet_page_id=p.answer_sheet_page_id WHERE l.test_result_id=?
                UNION SELECT 'scan_verification',v.verification_uuid,v.scan_verification_id FROM test_result_scans l
                  JOIN scan_verifications v ON v.scan_session_id=l.scan_session_id WHERE l.test_result_id=?
                UNION SELECT 'answer_attachment',o.attachment_uuid,o.answer_attachment_id FROM student_answers a
                  JOIN answer_attachments o ON o.student_answer_id=a.student_answer_id WHERE a.test_result_id=?
                UNION SELECT 'answer_attachment',o.attachment_uuid,o.answer_attachment_id FROM test_result_scans l
                  JOIN answer_attachments o ON o.scan_session_id=l.scan_session_id WHERE l.test_result_id=?
                UNION SELECT 'answer_verification',v.verification_uuid,v.answer_verification_id FROM student_answers a
                  JOIN answer_verifications v ON v.student_answer_id=a.student_answer_id WHERE a.test_result_id=?
                ORDER BY kind,uuid LIMIT 1072
                """,(rs,n)->new IdMapping(rs.getString(1),rs.getString(2),rs.getLong(3)),result,result,result,result,result,result,result);
    }
    public List<RubricMapping> rubricMappings(long result) {
        return jdbc.query("""
                SELECT a.answer_uuid,r.rubric_criterion_id,r.score_version,r.answer_rubric_score_id
                FROM student_answers a JOIN answer_rubric_scores r ON r.student_answer_id=a.student_answer_id
                WHERE a.test_result_id=? ORDER BY a.answer_uuid,r.score_version,r.rubric_criterion_id LIMIT 201
                """,(rs,n)->new RubricMapping(rs.getString(1),rs.getLong(2),rs.getInt(3),rs.getLong(4)),result);
    }
    public int missingAnswers(ResultRow r) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM questions q JOIN test_parts p ON p.test_part_id=q.test_part_id
                LEFT JOIN student_answers a ON a.question_id=q.question_id AND a.test_result_id=?
                WHERE p.test_id=? AND (a.student_answer_id IS NULL OR a.evaluation_status<>'finalized' OR a.verified_at IS NULL)
                """,Integer.class,r.id(),r.testId());
    }
    public Optional<SyncRow> sync(V3AuthenticatedUser u,String uuid) {
        return jdbc.query("""
                SELECT s.sync_id,s.sync_uuid,d.assignment_uuid,d.test_assignment_id,a.class_id
                FROM syncs s JOIN test_assignments d ON d.test_assignment_id=s.test_assignment_id
                JOIN class_assignments a ON a.class_assignment_id=d.class_assignment_id JOIN tests t ON t.test_id=d.test_id
                JOIN classes c ON c.class_id=a.class_id JOIN sections section ON section.section_id=c.section_id
                WHERE s.sync_uuid=? AND s.direction='upload' AND s.user_id=? AND a.user_id=? AND t.school_id=? AND section.school_id=?
                """,(rs,n)->new SyncRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getLong(5)),
                uuid,u.userId(),u.userId(),u.schoolId(),u.schoolId()).stream().findFirst();
    }
    public List<Intent> intents(String sync) {
        return jdbc.query("SELECT scan_page_uuid,request_json,upload_state,last_error_code FROM mobile_scan_uploads WHERE sync_uuid=? ORDER BY scan_page_uuid LIMIT 201",
                (rs,n)->new Intent(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),sync);
    }
    public boolean resultExists(String uuid) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM test_results WHERE result_uuid=?",Integer.class,uuid)>0;
    }
    public boolean membership(V3AuthenticatedUser u,SyncRow sync,long membership) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM class_lists c JOIN students s ON s.student_id=c.student_id WHERE c.class_list_id=? AND c.class_id=? AND s.school_id=?",Integer.class,membership,sync.classId(),u.schoolId())==1;
    }
    public int expectedPages(SyncRow sync,String sheet) {
        return jdbc.query("SELECT total_pages FROM answer_sheet_versions WHERE answer_sheet_uuid=? AND test_assignment_id=?",
                (rs,n)->rs.getInt(1),sheet,sync.assignmentId()).stream().findFirst().orElse(0);
    }
    public List<StoredItem> items(long sync) {
        return jdbc.query("""
                SELECT i.result_uuid,i.sync_status,v.response_json FROM sync_items i
                LEFT JOIN mobile_verification_items v ON v.sync_item_id=i.sync_item_id
                WHERE i.sync_id=? ORDER BY i.result_uuid LIMIT 201
                """,(rs,n)->new StoredItem(rs.getString(1),rs.getString(2),rs.getString(3)),sync);
    }
    public Optional<String> verification(long sync) {
        return jdbc.query("SELECT request_json FROM mobile_verification_batches WHERE sync_id=?",(rs,n)->rs.getString(1),sync).stream().findFirst();
    }
    public Optional<String> detectionPage(long sync) {
        return jdbc.query("SELECT p.scan_page_uuid FROM mobile_detection_uploads d JOIN scan_pages p ON p.scan_page_id=d.scan_page_id WHERE d.sync_id=?",
                (rs,n)->rs.getString(1),sync).stream().findFirst();
    }
    public Optional<AttachmentStage> attachment(long sync) {
        return jdbc.query("SELECT p.scan_page_uuid,u.upload_state,u.last_error_code FROM mobile_attachment_uploads u JOIN scan_pages p ON p.scan_page_id=u.scan_page_id WHERE u.sync_id=?",
                (rs,n)->new AttachmentStage(rs.getString(1),rs.getString(2),rs.getString(3)),sync).stream().findFirst();
    }
    public record AttachmentStage(String page,String state,String error) { }
    public Optional<ReopenStage> reopen(long sync) {
        return jdbc.query("""
                SELECT r.result_uuid,e.request_json,e.response_json,e.mobile_revision,e.score_version
                FROM mobile_result_reopens e JOIN test_results r ON r.test_result_id=e.test_result_id WHERE e.sync_id=?
                """,(rs,n)->new ReopenStage(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getInt(5)),sync).stream().findFirst();
    }
    public boolean reopened(ResultRow row) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_reopens WHERE test_result_id=? AND score_version=? AND mobile_revision<=?",
                Integer.class,row.id(),row.scoreVersion(),row.revision())==1;
    }
    public record ReopenStage(String resultUuid,String request,String response,long revision,int version) { }
    public Optional<CorrectionStage> correction(long sync) {
        return jdbc.query("SELECT r.result_uuid,c.response_json,c.score_version FROM mobile_result_corrections c JOIN test_results r ON r.test_result_id=c.test_result_id WHERE c.sync_id=?",
                (rs,n)->new CorrectionStage(rs.getString(1),rs.getString(2),rs.getInt(3)),sync).stream().findFirst();
    }
    public record CorrectionStage(String resultUuid,String response,int version) { }
    public Optional<SupersedeStage> supersede(long sync){
        return jdbc.query("""
                SELECT r.result_uuid,n.result_uuid,s.request_json,s.response_json,s.mobile_revision,s.score_version
                FROM mobile_result_supersessions s JOIN test_results r ON r.test_result_id=s.test_result_id
                JOIN test_results n ON n.test_result_id=s.replacement_result_id WHERE s.sync_id=?
                """,(rs,n)->new SupersedeStage(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getInt(6)),sync).stream().findFirst();
    }
    public record SupersedeStage(String resultUuid,String replacementUuid,String request,String response,long revision,int version) { }
    public record ResultRow(long id,String uuid,String assignmentUuid,long assignmentId,long testId,long classListId,long studentId,int attemptNumber,
            String status,long revision,int scoreVersion,BigDecimal total,BigDecimal max,BigDecimal percentage,int items,
            String performance,Long ruleId,Instant scoredAt,Long receiptRevision,Integer receiptVersion,String receiptJson) { }
    public record PageRow(long scanId,String scanUuid,long pageId,String pageUuid,String status,long sheetId,String sheetUuid,
                          long manifestId,String manifestUuid,Long attachmentId,String attachmentUuid,String linkStatus) { }
    public record SyncRow(long id,String uuid,String assignmentUuid,long assignmentId,long classId) { }
    public record Intent(String pageUuid,String json,String state,String error) { }
    public record StoredItem(String resultUuid,String status,String response) { }
}
