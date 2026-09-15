package com.capstone.assessment.v3.mobile.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

/** Current locking reads; used only by first finalization after the release gate and owner lock. */
@Repository
@Profile("v3")
public class V3WrittenFinalizationRepository {
    private final JdbcTemplate jdbc;
    public V3WrittenFinalizationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String assignmentUuid(long id){return jdbc.queryForObject("SELECT assignment_uuid FROM test_assignments WHERE test_assignment_id=? FOR UPDATE",String.class,id);}
    public List<Audit> audits(long answer) {
        return jdbc.query("""
                SELECT v.*,r.test_version_number,r.evaluation_reference_hash,r.reference_json,
                       i.test_result_id,i.result_uuid,i.sync_status AS item_status,mi.response_json,
                       s.sync_uuid,s.user_id AS sync_user,s.test_assignment_id,
                       b.operation_uuid,b.request_json
                FROM answer_verifications v
                LEFT JOIN mobile_written_references r ON r.sync_item_id=v.mobile_sync_item_id
                LEFT JOIN sync_items i ON i.sync_item_id=r.sync_item_id
                LEFT JOIN mobile_verification_items mi ON mi.sync_item_id=i.sync_item_id
                LEFT JOIN syncs s ON s.sync_id=i.sync_id
                LEFT JOIN mobile_verification_batches b ON b.sync_id=s.sync_id
                WHERE v.student_answer_id=? ORDER BY v.answer_verification_id LIMIT 2 FOR UPDATE
                """,(rs,n)->new Audit(rs.getLong("answer_verification_id"),rs.getString("verification_uuid"),rs.getLong("verified_by_user_id"),
                    rs.getString("verification_action"),rs.getString("new_answer_status"),rs.getString("new_answer_value"),rs.getBigDecimal("new_points"),
                    rs.getString("reason_detail"),rs.getObject("evidence_attachment_id")==null?null:rs.getLong("evidence_attachment_id"),
                    rs.getString("client_decided_at"),rs.getString("evaluation_snapshot_json"),rs.getInt("test_version_number"),
                    rs.getString("evaluation_reference_hash"),rs.getString("reference_json"),rs.getLong("test_result_id"),rs.getString("result_uuid"),
                    rs.getString("item_status"),rs.getString("response_json"),rs.getString("sync_uuid"),rs.getLong("sync_user"),
                    rs.getLong("test_assignment_id"),rs.getString("operation_uuid"),rs.getString("request_json")),answer);
    }
    public AnswerState state(long answer) {
        return jdbc.queryForObject("SELECT question_id,capture_source,response_evidence_attachment_id,teacher_feedback,reopened_at FROM student_answers WHERE student_answer_id=? FOR UPDATE",
                (rs,n)->new AnswerState(rs.getLong(1),rs.getString(2),rs.getObject(3)==null?null:rs.getLong(3),rs.getString(4),rs.getObject(5)!=null),answer);
    }
    public String questionUuid(long question) {
        return jdbc.queryForObject("SELECT question_uuid FROM questions WHERE question_id=? FOR UPDATE",String.class,question);
    }
    public List<Attachment> attachments(long answer) {
        return jdbc.query("SELECT answer_attachment_id,attachment_uuid FROM answer_attachments WHERE student_answer_id=? ORDER BY attachment_uuid LIMIT 21 FOR UPDATE",
                (rs,n)->new Attachment(rs.getLong(1),rs.getString(2)),answer);
    }
    public List<Criterion> criteria(long answer) {
        return jdbc.query("SELECT rubric_criterion_id,answer_verification_id,scored_by_user_id,score_version,points_awarded,criterion_feedback FROM answer_rubric_scores WHERE student_answer_id=? ORDER BY rubric_criterion_id,score_version LIMIT 101 FOR UPDATE",
                (rs,n)->new Criterion(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getInt(4),rs.getBigDecimal(5),rs.getString(6)),answer);
    }
    public record Audit(long id,String uuid,long teacher,String action,String status,String text,BigDecimal points,String comment,
            Long evidenceId,String clientTime,String evaluationJson,int version,String hash,String referenceJson,
            long resultId,String resultUuid,String itemStatus,String receiptJson,String syncUuid,long syncUser,long assignmentId,String operation,String requestJson) { }
    public record AnswerState(long questionId,String source,Long evidenceId,String feedback,boolean reopened) { }
    public record Attachment(long id,String uuid) { }
    public record Criterion(long id,long verification,long teacher,int version,BigDecimal points,String comment) { }
}
