package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ResultContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/** Called under the scorer's owner/result locks and transaction. */
@Repository
@Profile("v3")
public class V3MobileFinalizationRepository {
    private final JdbcTemplate jdbc;
    public V3MobileFinalizationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Only V3_014 tables: the disabled release gate never queries draft columns/tables.
    public boolean hasPaperCapture(long result) {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM test_result_scans WHERE test_result_id=?)
                     + (SELECT COUNT(*) FROM student_answers WHERE test_result_id=? AND capture_source='omr')
                """, Integer.class, result, result) > 0;
    }
    public long revision(long result) {
        return jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=?", Long.class, result);
    }
    public Optional<Receipt> receipt(long result) {
        return jdbc.query("SELECT mobile_revision,score_version,response_json FROM mobile_result_finalizations WHERE test_result_id=? FOR UPDATE",
                (rs,n)->new Receipt(rs.getLong(1),rs.getInt(2),rs.getString(3)),result).stream().findFirst();
    }
    public boolean completeSelectedCapture(ResultContext c) {
        // One selected session must contain every immutable sheet page; each page may have audited predecessors.
        int selected = jdbc.queryForObject("SELECT COUNT(*) FROM test_result_scans WHERE test_result_id=? AND link_status='selected'",Integer.class,c.testResultId());
        if (selected != 1) return false;
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_result_scans l
                JOIN scan_sessions s ON s.scan_session_id=l.scan_session_id
                JOIN answer_sheet_versions v ON v.answer_sheet_version_id=s.answer_sheet_version_id
                JOIN tests t ON t.test_id=?
                WHERE l.test_result_id=? AND l.link_status='selected'
                  AND s.test_assignment_id=? AND s.class_list_id=? AND s.scanned_by_user_id=?
                  AND s.scan_status='accepted' AND s.verified_by_user_id=? AND s.verified_at IS NOT NULL
                  AND s.expected_page_count=v.total_pages AND s.captured_page_count=v.total_pages
                  AND v.test_assignment_id=s.test_assignment_id AND v.total_pages BETWEEN 1 AND 12
                  AND v.test_version_number=t.version_number AND v.generation_status='ready'
                  AND v.total_questions=t.total_items AND v.total_questions=(
                      SELECT COUNT(*) FROM questions q JOIN test_parts p ON p.test_part_id=q.test_part_id WHERE p.test_id=t.test_id)
                  AND (SELECT COUNT(*) FROM scan_pages p WHERE p.scan_session_id=s.scan_session_id AND p.page_status<>'superseded')=v.total_pages
                  AND (SELECT COUNT(*) FROM scan_pages p WHERE p.scan_session_id=s.scan_session_id) BETWEEN v.total_pages AND v.total_pages*5
                  AND (SELECT COUNT(*) FROM scan_pages p JOIN answer_sheet_pages a ON a.answer_sheet_page_id=p.answer_sheet_page_id
                       WHERE p.scan_session_id=s.scan_session_id AND p.page_status='accepted' AND p.capture_number BETWEEN 1 AND 5
                         AND a.answer_sheet_version_id=v.answer_sheet_version_id AND a.page_status='ready'
                         AND a.page_number=p.page_number AND p.page_number BETWEEN 1 AND v.total_pages AND a.total_pages=v.total_pages
                         AND a.qr_payload_hash=p.qr_payload_hash
                         AND EXISTS(SELECT 1 FROM scan_verifications d WHERE d.scan_page_id=p.scan_page_id
                             AND d.scan_session_id=s.scan_session_id AND d.verification_action='accepted'
                             AND d.verified_by_user_id=? AND d.mobile_operation_uuid IS NOT NULL))=v.total_pages
                  AND (SELECT COUNT(DISTINCT p.page_number) FROM scan_pages p WHERE p.scan_session_id=s.scan_session_id AND p.page_status='accepted')=v.total_pages
                """,Integer.class,c.testId(),c.testResultId(),c.testAssignmentId(),c.classListId(),c.teacherUserId(),c.teacherUserId(),c.teacherUserId())==1;
    }
    public boolean validCaptureHistory(long result){
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_result_scans l JOIN scan_pages p ON p.scan_session_id=l.scan_session_id
                LEFT JOIN scan_pages old ON old.scan_page_id=p.supersedes_scan_page_id
                WHERE l.test_result_id=? AND l.link_status='selected' AND (
                    (p.capture_number=1 AND p.supersedes_scan_page_id IS NOT NULL)
                    OR (p.capture_number>1 AND (old.scan_page_id IS NULL OR old.scan_session_id<>p.scan_session_id
                        OR old.answer_sheet_page_id<>p.answer_sheet_page_id OR old.page_number<>p.page_number
                        OR old.capture_number+1<>p.capture_number OR old.page_status<>'superseded'
                        OR NOT EXISTS(SELECT 1 FROM scan_verifications v WHERE v.scan_page_id=old.scan_page_id
                            AND v.verification_action='rescan_requested' AND v.mobile_operation_uuid IS NOT NULL)))
                    OR (p.page_status='superseded' AND NOT EXISTS(SELECT 1 FROM scan_pages child WHERE child.supersedes_scan_page_id=p.scan_page_id)))
                """,Integer.class,result)==0;
    }
    public int expectedPages(long result) {
        return jdbc.queryForObject("SELECT s.expected_page_count FROM scan_sessions s JOIN test_result_scans l ON l.scan_session_id=s.scan_session_id WHERE l.test_result_id=? AND l.link_status='selected'",Integer.class,result);
    }
    public List<Evidence> originals(long result) {
        return jdbc.query("""
                SELECT p.scan_page_uuid,p.scan_page_id,p.image_hash,a.attachment_uuid,a.storage_provider,a.storage_key,
                       a.mime_type,a.file_size_bytes,a.content_hash,a.purge_status,a.source_answer_attachment_id
                FROM test_result_scans l JOIN scan_pages p ON p.scan_session_id=l.scan_session_id
                JOIN answer_attachments a ON a.scan_page_id=p.scan_page_id AND a.scan_session_id=p.scan_session_id
                WHERE l.test_result_id=? AND l.link_status='selected' AND a.attachment_type='original_page' AND p.page_status='accepted' FOR UPDATE
                """,(rs,n)->new Evidence(rs.getString(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getString(6),rs.getString(7),rs.getLong(8),rs.getString(9),rs.getString(10),rs.getObject(11)!=null),result);
    }
    public List<String> lockQuestionTypes(long test) {
        return jdbc.query("SELECT t.question_type_code FROM test_parts p JOIN questions q ON q.test_part_id=p.test_part_id JOIN question_types t ON t.question_type_id=q.question_type_id WHERE p.test_id=? ORDER BY q.question_id LIMIT 201 FOR UPDATE",
                (rs,n)->rs.getString(1),test);
    }
    public boolean verifiedObjectiveCoverage(ResultContext c,int expected) {
        int matched=jdbc.queryForObject("""
                SELECT COUNT(DISTINCT a.question_id) FROM student_answers a
                JOIN mobile_objective_verifications v ON v.student_answer_id=a.student_answer_id
                JOIN omr_detections d ON d.omr_detection_id=v.omr_detection_id AND d.question_id=a.question_id
                JOIN answer_sheet_regions g ON g.answer_sheet_region_id=d.answer_sheet_region_id AND g.question_id=a.question_id
                JOIN scan_pages p ON p.scan_page_id=d.scan_page_id AND p.answer_sheet_page_id=g.answer_sheet_page_id
                JOIN scan_sessions s ON s.scan_session_id=p.scan_session_id AND s.answer_sheet_version_id=g.answer_sheet_version_id
                JOIN test_result_scans l ON l.scan_session_id=s.scan_session_id AND l.test_result_id=a.test_result_id AND l.link_status='selected'
                JOIN questions q ON q.question_id=a.question_id JOIN question_types k ON k.question_type_id=q.question_type_id
                LEFT JOIN question_options o ON o.question_option_id=a.selected_question_option_id AND o.question_id=a.question_id AND o.is_active=TRUE
                WHERE a.test_result_id=? AND a.capture_source='omr' AND a.evaluation_status='finalized'
                  AND a.verified_by_user_id=? AND v.verified_by_user_id=? AND a.verified_at IS NOT NULL AND a.finalized_at IS NOT NULL
                  AND p.page_status='accepted' AND k.question_type_code IN ('multiple_choice','true_false')
                  AND g.region_type='objective_bubbles' AND g.question_type_id=q.question_type_id AND g.test_part_id=q.test_part_id
                  AND ((d.detection_status='blank' AND d.detected_option IS NULL AND a.answer_status='blank' AND a.selected_question_option_id IS NULL)
                    OR (d.detection_status='detected' AND a.answer_status='answered' AND d.detected_option=o.option_key)) FOR UPDATE
                """,Integer.class,c.testResultId(),c.teacherUserId(),c.teacherUserId());
        return matched == expected;
    }
    public boolean invalidObjectiveKeys(long test) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM questions q JOIN test_parts p ON p.test_part_id=q.test_part_id
                JOIN question_types type ON type.question_type_id=q.question_type_id
                JOIN answer_keys k ON k.question_id=q.question_id
                LEFT JOIN question_options o ON o.question_option_id=k.correct_question_option_id
                    AND o.question_id=q.question_id AND o.is_active=TRUE
                WHERE p.test_id=? AND type.question_type_code IN ('multiple_choice','true_false')
                  AND (k.answer_key_type<>'option' OR o.question_option_id IS NULL) FOR UPDATE
                """,Integer.class,test)>0;
    }
    public void save(long result,long revision,int scoreVersion,String json) {
        int updated=jdbc.update("UPDATE test_results SET mobile_revision=mobile_revision+1 WHERE test_result_id=? AND mobile_revision=? AND result_status='finalized'",result,revision);
        if(updated!=1) throw new IllegalStateException("Finalization revision changed inside the locked transaction.");
        jdbc.update("INSERT INTO mobile_result_finalizations(test_result_id,mobile_revision,score_version,response_json) VALUES(?,?,?,?)",result,revision+1,scoreVersion,json);
    }
    public record Receipt(long revision,int scoreVersion,String json) { }
    public void saveCorrection(com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse r,long revision,String json) {
        int updated=jdbc.update("UPDATE test_results SET mobile_revision=mobile_revision+1,finalized_at=?,verification_completed_at=? WHERE test_result_id=? AND mobile_revision=? AND score_version=? AND result_status='finalized'",
                java.sql.Timestamp.from(r.scoredAt()),java.sql.Timestamp.from(r.scoredAt()),r.testResultId(),revision,r.scoreVersion());
        if(updated!=1)throw new IllegalStateException("Correction revision changed inside the locked transaction.");
        updated=jdbc.update("UPDATE mobile_result_finalizations SET mobile_revision=?,score_version=?,response_json=?,finalized_at=CURRENT_TIMESTAMP WHERE test_result_id=? AND score_version=?",
                revision+1,r.scoreVersion(),json,r.testResultId(),r.scoreVersion()-1);
        if(updated!=1)throw new IllegalStateException("Previous official receipt changed inside the locked transaction.");
    }
    public record Evidence(String pageUuid,long pageId,String imageHash,String uuid,String provider,String key,
                           String mime,long size,String hash,String purgeStatus,boolean derived) { }
}
