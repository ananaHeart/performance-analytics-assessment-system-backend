package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3VerificationBatch;
import com.capstone.assessment.v3.mobile.dto.V3VerificationBatch.*;
import com.capstone.assessment.v3.mobile.dto.V3VerificationResponse.Outcome;
import com.capstone.assessment.v3.mobile.repository.V3DetectionRepository.Page;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@Profile("v3")
public class V3VerificationRepository {
    private final JdbcTemplate jdbc;
    public V3VerificationRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Optional<Long> assignment(V3AuthenticatedUser user,String uuid) {
        return jdbc.query("""
                SELECT d.test_assignment_id FROM test_assignments d
                JOIN class_assignments a ON a.class_assignment_id=d.class_assignment_id
                JOIN classes c ON c.class_id=a.class_id JOIN sections s ON s.section_id=c.section_id
                JOIN tests t ON t.test_id=d.test_id
                WHERE d.assignment_uuid=? AND a.user_id=? AND s.school_id=? AND t.school_id=? FOR UPDATE
                """,(rs,n)->rs.getLong(1),uuid,user.userId(),user.schoolId(),user.schoolId()).stream().findFirst();
    }
    public Optional<Result> result(String uuid) {
        return jdbc.query("SELECT test_result_id,test_assignment_id,mobile_revision,result_status FROM test_results WHERE result_uuid=? FOR UPDATE",
                (rs,n)->new Result(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4)),uuid).stream().findFirst();
    }
    public boolean resultInSchool(long resultId,String school) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_results r JOIN test_assignments d ON d.test_assignment_id=r.test_assignment_id
                JOIN class_assignments a ON a.class_assignment_id=d.class_assignment_id
                JOIN class_lists cl ON cl.class_list_id=r.class_list_id AND cl.class_id=a.class_id
                JOIN students s ON s.student_id=cl.student_id WHERE r.test_result_id=? AND s.school_id=?
                """,Integer.class,resultId,school)==1;
    }
    public Optional<Batch> batch(String operation) {
        return jdbc.query("SELECT sync_id,request_hash FROM mobile_verification_batches WHERE operation_uuid=? FOR UPDATE",
                (rs,n)->new Batch(rs.getLong(1),rs.getString(2)),operation).stream().findFirst();
    }
    public boolean syncExists(String uuid) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM syncs WHERE sync_uuid=?",Integer.class,uuid)>0;
    }
    public boolean pageExists(String uuid) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM scan_pages WHERE scan_page_uuid=?",Integer.class,uuid)>0;
    }
    public long register(V3AuthenticatedUser user,long assignment,V3VerificationBatch batch,String hash,String json) {
        return register(user,assignment,batch.syncUuid(),batch.operationUuid(),batch.items().stream().map(Item::resultUuid).toList(),hash,json);
    }
    public long register(V3AuthenticatedUser user,long assignment,String syncUuid,String operationUuid,List<String> results,String hash,String json) {
        long sync=insert("sync_id","""
                INSERT INTO syncs(sync_uuid,user_id,test_assignment_id,direction,sync_status,payload_hash,request_item_count)
                VALUES (?,?,?,'upload','in_progress',?,?)
                """,syncUuid,user.userId(),assignment,hash,results.size());
        jdbc.update("INSERT INTO mobile_verification_batches(operation_uuid,sync_id,request_hash,request_json) VALUES(?,?,?,?)",operationUuid,sync,hash,json);
        for(String result:results) {
            long id=insert("sync_item_id","INSERT INTO sync_items(sync_id,result_uuid,sync_action,sync_status) VALUES(?,?,'upsert','pending')",sync,result);
            jdbc.update("INSERT INTO mobile_verification_items(sync_item_id) VALUES(?)",id);
        }
        return sync;
    }
    public ItemReceipt item(long sync,String result) {
        return jdbc.queryForObject("""
                SELECT i.sync_item_id,v.response_json FROM sync_items i
                JOIN mobile_verification_items v ON v.sync_item_id=i.sync_item_id WHERE i.sync_id=? AND i.result_uuid=? FOR UPDATE
                """,(rs,n)->new ItemReceipt(rs.getLong(1),rs.getString(2)),sync,result);
    }
    public void outcome(long sync,long itemId,Long resultId,Outcome outcome,String json) {
        boolean success="success".equals(outcome.status());
        jdbc.update("UPDATE mobile_verification_items SET response_json=? WHERE sync_item_id=?",json,itemId);
        jdbc.update("""
                UPDATE sync_items SET test_result_id=?,sync_status=?,attempt_count=LEAST(attempt_count+1,65535),
                    last_attempt_at=CURRENT_TIMESTAMP,processed_at=CURRENT_TIMESTAMP,
                    synced_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,error_code=?,error_message=? WHERE sync_item_id=?
                """,resultId,outcome.status(),success,success?null:outcome.error().code(),success?null:outcome.error().message(),itemId);
        int pending=jdbc.queryForObject("SELECT COUNT(*) FROM sync_items WHERE sync_id=? AND sync_status='pending'",Integer.class,sync);
        int failed=jdbc.queryForObject("SELECT COUNT(*) FROM sync_items WHERE sync_id=? AND sync_status='failed'",Integer.class,sync);
        int succeeded=jdbc.queryForObject("SELECT COUNT(*) FROM sync_items WHERE sync_id=? AND sync_status='success'",Integer.class,sync);
        String state=pending>0?"in_progress":failed==0?"success":succeeded>0?"partial_success":"failed";
        jdbc.update("UPDATE syncs SET sync_status=?,completed_at=CASE WHEN ? THEN NULL ELSE CURRENT_TIMESTAMP END WHERE sync_id=?",state,pending>0,sync);
    }
    public boolean verificationExists(String uuid) {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM scan_verifications WHERE verification_uuid=?)
                    +(SELECT COUNT(*) FROM mobile_objective_verifications WHERE verification_uuid=?)
                    +(SELECT COUNT(*) FROM answer_verifications WHERE verification_uuid=?)
                """,Integer.class,uuid,uuid,uuid)>0;
    }
    public long pageDecision(V3AuthenticatedUser user,String operation,Page page,PageDecision d) {
        long id=insert("scan_verification_id","""
                INSERT INTO scan_verifications(verification_uuid,scan_session_id,scan_page_id,verified_by_user_id,
                    verification_action,reason_code,reason_detail,client_decided_at,mobile_operation_uuid)
                VALUES(?,?,?,?,?,?,?,?,?)
                """,d.verificationUuid(),page.scanId(),page.pageId(),user.userId(),d.action(),d.reasonCode(),d.comment(),d.clientDecidedAt().toString(),operation);
        jdbc.update("UPDATE scan_pages SET page_status=? WHERE scan_page_id=?",d.action(),page.pageId());
        return id;
    }
    public Optional<Detection> detection(String uuid,long pageId,long regionId,long questionId) {
        return jdbc.query("""
                SELECT d.omr_detection_id,d.detection_status,d.detected_option,o.question_option_id FROM omr_detections d
                LEFT JOIN question_options o ON o.question_id=d.question_id AND o.option_key=d.detected_option AND o.is_active=TRUE
                WHERE d.detection_uuid=? AND d.scan_page_id=? AND d.answer_sheet_region_id=? AND d.question_id=? FOR UPDATE
                """,(rs,n)->new Detection(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getObject(4)==null?null:rs.getLong(4)),uuid,pageId,regionId,questionId).stream().findFirst();
    }
    public boolean answerExists(String uuid,long result,long question) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE answer_uuid=? OR (test_result_id=? AND question_id=?)",Integer.class,uuid,result,question)>0;
    }
    public long answer(V3AuthenticatedUser user,String operation,Page page,long question,Answer answer,Detection detection) {
        long id=insert("student_answer_id","""
                INSERT INTO student_answers(test_result_id,question_id,verified_by_user_id,answer_uuid,selected_question_option_id,
                    capture_source,verified_at,answer_status,evaluation_status,is_correct,points_earned,teacher_feedback,finalized_at)
                VALUES(?,?,?,?,?,'omr',CURRENT_TIMESTAMP,?,'finalized',NULL,0,?,CURRENT_TIMESTAMP)
                """,page.resultId(),question,user.userId(),answer.answerUuid(),detection.optionId(),
                "blank".equals(detection.status())?"blank":"answered",answer.comment());
        jdbc.update("""
                INSERT INTO mobile_objective_verifications(verification_uuid,student_answer_id,omr_detection_id,operation_uuid,
                    verified_by_user_id,comment,client_decided_at) VALUES(?,?,?,?,?,?,?)
                """,answer.verificationUuid(),id,detection.id(),operation,user.userId(),answer.comment(),answer.clientDecidedAt().toString());
        return id;
    }
    public void finishResult(long resultId,V3AuthenticatedUser user,Set<Long> scans) {
        jdbc.update("UPDATE test_results SET mobile_revision=mobile_revision+1,result_status='pending_verification' WHERE test_result_id=?",resultId);
        for(long scan:scans) {
            int expected=jdbc.queryForObject("SELECT expected_page_count FROM scan_sessions WHERE scan_session_id=?",Integer.class,scan);
            var states=jdbc.queryForList("SELECT page_status FROM scan_pages WHERE scan_session_id=? AND page_status<>'superseded'",String.class,scan);
            String status=states.contains("rescan_requested")?"rescan_requested":states.contains("rejected")?"rejected":
                    states.size()==expected && states.stream().allMatch("accepted"::equals)?"accepted":"needs_verification";
            jdbc.update("UPDATE scan_sessions SET scan_status=?,verified_by_user_id=?,verified_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END WHERE scan_session_id=?",
                    status,"accepted".equals(status)?user.userId():null,"accepted".equals(status),scan);
        }
    }
    private long insert(String key,String sql,Object... values) {
        var holder=new GeneratedKeyHolder(); jdbc.update(c->{var statement=c.prepareStatement(sql,new String[]{key});
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;},holder);return holder.getKey().longValue();
    }
    public record Result(long id,long assignmentId,long revision,String status) { }
    public record Batch(long syncId,String hash) { }
    public record ItemReceipt(long id,String response) { }
    public record Detection(long id,String status,String option,Long optionId) { }
}
