package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ScoringRow;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
@Profile("v3")
public class V3ResultCorrectionRepository {
    private final JdbcTemplate jdbc;
    public V3ResultCorrectionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<Receipt> receipt(String operation){
        return jdbc.query("""
                SELECT c.test_result_id,c.corrected_by_user_id,c.request_hash,c.response_json,c.score_version,s.sync_uuid
                FROM mobile_result_corrections c JOIN syncs s ON s.sync_id=c.sync_id WHERE c.operation_uuid=? FOR UPDATE
                """,(rs,n)->new Receipt(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getString(6)),operation).stream().findFirst();
    }
    public Optional<Reopen> reopen(String operation){
        return jdbc.query("SELECT test_result_id,score_version,previous_revision,mobile_revision,official_score_json,reopened_at FROM mobile_result_reopens WHERE operation_uuid=? FOR UPDATE",
                (rs,n)->new Reopen(rs.getLong(1),rs.getInt(2),rs.getLong(3),rs.getLong(4),rs.getString(5),rs.getTimestamp(6).toInstant()),operation).stream().findFirst();
    }
    public boolean usedReopen(String operation){return jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_corrections WHERE reopen_operation_uuid=?",Integer.class,operation)>0;}
    public boolean verificationExists(String uuid){return jdbc.queryForObject("SELECT COUNT(*) FROM mobile_answer_corrections WHERE verification_uuid=?",Integer.class,uuid)>0;}
    public String assignmentUuid(long id){return jdbc.queryForObject("SELECT assignment_uuid FROM test_assignments WHERE test_assignment_id=?",String.class,id);}
    public String questionUuid(long id){return jdbc.queryForObject("SELECT question_uuid FROM questions WHERE question_id=?",String.class,id);}
    public Optional<Long> option(long question,String key){
        return jdbc.query("SELECT question_option_id FROM question_options WHERE question_id=? AND option_key=? AND is_active=TRUE FOR UPDATE",(rs,n)->rs.getLong(1),question,key).stream().findFirst();
    }
    public boolean originalDetection(long answer,long detection){return jdbc.queryForObject("SELECT COUNT(*) FROM mobile_objective_verifications WHERE student_answer_id=? AND omr_detection_id=? FOR UPDATE",Integer.class,answer,detection)==1;}
    public Map<String,Object> snapshot(long answer){
        var row=jdbc.queryForMap("SELECT a.*,o.option_key AS selected_option_key,o.option_text AS selected_option_text FROM student_answers a LEFT JOIN question_options o ON o.question_option_id=a.selected_question_option_id WHERE a.student_answer_id=? FOR UPDATE",answer);
        int version=((Number)row.get("score_version")).intValue();
        return Map.of("answer",row,"rubricScores",jdbc.queryForList("SELECT * FROM answer_rubric_scores WHERE student_answer_id=? AND score_version=? ORDER BY rubric_criterion_id FOR UPDATE",answer,version),
                "linkedEvidence",jdbc.queryForList("SELECT answer_attachment_id,attachment_uuid,attachment_type,content_hash,purge_status FROM answer_attachments WHERE student_answer_id=? ORDER BY attachment_uuid FOR UPDATE",answer));
    }
    public void linkEvidence(long answer,long attachment){
        var owner=jdbc.queryForObject("SELECT student_answer_id FROM answer_attachments WHERE answer_attachment_id=? FOR UPDATE",Long.class,attachment);
        if(owner!=null && owner!=answer)throw new com.capstone.assessment.v3.auth.exception.V3AuthException("EVIDENCE_ALREADY_LINKED","Evidence belongs to another answer.",org.springframework.http.HttpStatus.CONFLICT);
        if(owner==null)jdbc.update("UPDATE answer_attachments SET student_answer_id=? WHERE answer_attachment_id=?",answer,attachment);
    }
    public Long updateAnswer(V3AuthenticatedUser user,ScoringRow old,V3CorrectionRequest.Answer answer,int version,
            Long option,BigDecimal points,List<Long> attachments,Instant reopened,String reason,String evaluationJson){
        boolean objective=answer.evaluation() instanceof V3CorrectionRequest.Objective;
        var written=objective?null:V3CorrectionRequest.written(answer.evaluation());
        Long primary=attachments.isEmpty()?null:attachments.get(0);
        int updated=jdbc.update("""
                UPDATE student_answers SET selected_question_option_id=?,response_text=?,response_evidence_attachment_id=?,capture_source=?,
                    answer_status=?,evaluation_status='finalized',points_earned=?,is_correct=NULL,teacher_feedback=?,
                    verified_by_user_id=?,verified_at=CURRENT_TIMESTAMP,finalized_at=CURRENT_TIMESTAMP,reopened_at=?,score_version=?
                WHERE student_answer_id=? AND score_version=?
                """,option,objective?null:written.responseText(),primary,objective?"omr":"manual",answer.evaluation().answerStatus(),points,
                answer.comment(),user.userId(),Timestamp.from(reopened),version,old.studentAnswerId(),old.answerScoreVersion());
        if(updated!=1)throw new org.springframework.dao.OptimisticLockingFailureException("Answer version changed during correction");
        if(objective)return null;
        long verification=insert("""
                INSERT INTO answer_verifications(verification_uuid,student_answer_id,verified_by_user_id,verification_action,
                    previous_answer_status,previous_answer_value,new_answer_status,new_answer_value,previous_points,new_points,
                    reason_code,reason_detail,evidence_attachment_id,client_decided_at,evaluation_snapshot_json)
                VALUES(?,?,?,'manual_scored',?,?,?,?,?,?,?,?,?,?,?)
                """,answer.verificationUuid(),old.studentAnswerId(),user.userId(),old.answerStatus(),old.responseText(),written.answerStatus(),written.responseText(),old.pointsEarned(),
                points,reason,answer.comment(),primary,answer.clientDecidedAt().toString(),evaluationJson);
        if(written instanceof V3WrittenVerificationBatch.Rubric rubric)for(var score:rubric.criterionScores())
            jdbc.update("INSERT INTO answer_rubric_scores(student_answer_id,rubric_criterion_id,answer_verification_id,scored_by_user_id,score_version,points_awarded,criterion_feedback) VALUES(?,?,?,?,?,?,?)",
                    old.studentAnswerId(),score.rubricCriterionId(),verification,user.userId(),version,score.pointsAwarded(),score.comment());
        return verification;
    }
    public void save(V3AuthenticatedUser u,long result,long assignment,V3CorrectionRequest r,String hash,String requestJson,String referenceJson,String responseJson,long revision,int version){
        long sync=insert("INSERT INTO syncs(sync_uuid,user_id,test_assignment_id,direction,sync_status,payload_hash,request_item_count,completed_at) VALUES(?,?,?,'upload','success',?,1,CURRENT_TIMESTAMP)",r.syncUuid(),u.userId(),assignment,hash);
        String resultUuid=jdbc.queryForObject("SELECT result_uuid FROM test_results WHERE test_result_id=?",String.class,result);
        jdbc.update("INSERT INTO sync_items(sync_id,result_uuid,test_result_id,sync_action,sync_status,attempt_count,last_attempt_at,processed_at,synced_at) VALUES(?,?,?,'upsert','success',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",sync,resultUuid,result);
        long audit=insert("INSERT INTO audit_logs(audit_uuid,user_id,action,entity_type,entity_id,outcome,details) VALUES(?,?,'V3_MOBILE_RESULT_CORRECTED','test_result',?,'success',?)",UUID.randomUUID().toString(),u.userId(),resultUuid,requestJson);
        jdbc.update("""
                INSERT INTO mobile_result_corrections(operation_uuid,reopen_operation_uuid,sync_id,test_result_id,corrected_by_user_id,audit_log_id,
                    previous_score_version,score_version,mobile_revision,request_hash,request_json,reference_json,response_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,r.operationUuid(),r.reopenOperationUuid(),sync,result,u.userId(),audit,r.expectedScoreVersion(),version,revision,hash,requestJson,referenceJson,responseJson);
    }
    public void answerAudit(String operation,V3CorrectionRequest.Answer a,long answer,Long detection,Long verification,String before,String after){
        jdbc.update("INSERT INTO mobile_answer_corrections(verification_uuid,operation_uuid,student_answer_id,omr_detection_id,answer_verification_id,before_json,after_json,client_decided_at) VALUES(?,?,?,?,?,?,?,?)",
                a.verificationUuid(),operation,answer,detection,verification,before,after,a.clientDecidedAt().toString());
    }
    private long insert(String sql,Object... args){var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int n=0;n<args.length;n++)s.setObject(n+1,args[n]);return s;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
    public record Receipt(long result,long teacher,String hash,String json,int version,String syncUuid) { }
    public record Reopen(long result,int version,long previousRevision,long revision,String official,Instant at) { }
}
