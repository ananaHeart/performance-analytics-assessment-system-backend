package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3WrittenVerificationBatch.*;
import com.capstone.assessment.v3.mobile.repository.V3DetectionRepository.Page;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
@Profile("v3")
public class V3WrittenVerificationRepository {
    private final JdbcTemplate jdbc;
    public V3WrittenVerificationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Integer maximumResponseLength(long question) {
        return jdbc.queryForObject("SELECT maximum_response_length FROM questions WHERE question_id=? FOR UPDATE",Integer.class,question);
    }
    public void reference(long itemId,int version,String hash,String json) {
        jdbc.update("INSERT INTO mobile_written_references(sync_item_id,test_version_number,evaluation_reference_hash,reference_json) VALUES(?,?,?,?)",itemId,version,hash,json);
    }
    public long answer(V3AuthenticatedUser user,Page page,long question,long itemId,Answer answer,BigDecimal points,List<Long> attachments,String snapshot) {
        Long primary=attachments.isEmpty()?null:attachments.get(0);
        long id=insert("student_answer_id","""
                INSERT INTO student_answers(test_result_id,question_id,verified_by_user_id,answer_uuid,response_text,
                    response_evidence_attachment_id,capture_source,verified_at,answer_status,evaluation_status,
                    points_earned,teacher_feedback,finalized_at)
                VALUES(?,?,?,?,?,?,'manual',CURRENT_TIMESTAMP,?,'finalized',?,?,CURRENT_TIMESTAMP)
                """,page.resultId(),question,user.userId(),answer.answerUuid(),answer.evaluation().responseText(),primary,
                answer.evaluation().answerStatus(),points,answer.comment());
        for(long attachment:attachments) {
            int changed=jdbc.update("UPDATE answer_attachments SET student_answer_id=? WHERE answer_attachment_id=? AND student_answer_id IS NULL",id,attachment);
            if(changed!=1)throw new com.capstone.assessment.v3.auth.exception.V3AuthException("EVIDENCE_ALREADY_LINKED","Evidence already belongs to another answer.",org.springframework.http.HttpStatus.CONFLICT);
        }
        long verification=insert("answer_verification_id","""
                INSERT INTO answer_verifications(verification_uuid,student_answer_id,verified_by_user_id,verification_action,
                    new_answer_status,new_answer_value,new_points,reason_detail,evidence_attachment_id,mobile_sync_item_id,
                    client_decided_at,evaluation_snapshot_json)
                VALUES(?,?,?,'manual_scored',?,?,?,?,?,?,?,?)
                """,answer.verificationUuid(),id,user.userId(),answer.evaluation().answerStatus(),answer.evaluation().responseText(),points,
                answer.comment(),primary,itemId,answer.clientDecidedAt().toString(),snapshot);
        if(answer.evaluation() instanceof Rubric rubric)for(var score:rubric.criterionScores())
            jdbc.update("""
                    INSERT INTO answer_rubric_scores(student_answer_id,rubric_criterion_id,answer_verification_id,
                        scored_by_user_id,score_version,points_awarded,criterion_feedback) VALUES(?,?,?,?,1,?,?)
                    """,id,score.rubricCriterionId(),verification,user.userId(),score.pointsAwarded(),score.comment());
        return id;
    }
    private long insert(String key,String sql,Object...values){var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,new String[]{key});for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);return s;},keys);return keys.getKey().longValue();}
}
