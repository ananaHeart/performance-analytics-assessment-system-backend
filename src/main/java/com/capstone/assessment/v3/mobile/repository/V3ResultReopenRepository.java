package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.util.*;

@Repository
@Profile("v3")
public class V3ResultReopenRepository {
    private final JdbcTemplate jdbc;
    public V3ResultReopenRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<Long> ownedResult(V3AuthenticatedUser u,String uuid){
        return jdbc.query("""
                SELECT r.test_result_id FROM test_results r
                JOIN test_assignments d ON d.test_assignment_id=r.test_assignment_id
                JOIN class_assignments a ON a.class_assignment_id=d.class_assignment_id
                JOIN classes c ON c.class_id=a.class_id JOIN sections section ON section.section_id=c.section_id
                JOIN tests t ON t.test_id=d.test_id JOIN class_lists cl ON cl.class_list_id=r.class_list_id AND cl.class_id=a.class_id
                JOIN students student ON student.student_id=cl.student_id
                WHERE r.result_uuid=? AND a.user_id=? AND t.school_id=? AND student.school_id=? AND section.school_id=? FOR UPDATE
                """,(rs,n)->rs.getLong(1),uuid,u.userId(),u.schoolId(),u.schoolId(),u.schoolId()).stream().findFirst();
    }
    public Optional<Saved> saved(String operation){
        return jdbc.query("""
                SELECT r.test_result_id,r.reopened_by_user_id,r.request_hash,r.response_json,r.mobile_revision,r.score_version,s.sync_uuid
                FROM mobile_result_reopens r JOIN syncs s ON s.sync_id=r.sync_id WHERE r.operation_uuid=? FOR UPDATE
                """,(rs,n)->new Saved(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getInt(6),rs.getString(7)),operation).stream().findFirst();
    }
    public boolean syncExists(String uuid){return jdbc.queryForObject("SELECT COUNT(*) FROM syncs WHERE sync_uuid=?",Integer.class,uuid)>0;}
    public void commit(V3AuthenticatedUser u,long result,long assignment,V3ReopenRequest request,V3LifecycleAck ack,
            String hash,String requestJson,String scoreJson,String responseJson){
        int changed=jdbc.update("""
                UPDATE test_results SET result_status='pending_verification',mobile_revision=mobile_revision+1
                WHERE test_result_id=? AND result_status='finalized' AND mobile_revision=? AND score_version=?
                """,result,request.expectedRevision(),request.expectedScoreVersion());
        if(changed!=1)throw new IllegalStateException("Reopen state changed inside the locked transaction.");
        long sync=insert("""
                INSERT INTO syncs(sync_uuid,user_id,test_assignment_id,direction,sync_status,payload_hash,request_item_count,completed_at)
                VALUES(?,?,?,'upload','success',?,1,CURRENT_TIMESTAMP)
                """,request.syncUuid(),u.userId(),assignment,hash);
        jdbc.update("""
                INSERT INTO sync_items(sync_id,result_uuid,test_result_id,sync_action,sync_status,attempt_count,last_attempt_at,processed_at,synced_at)
                VALUES(?,?,?,'upsert','success',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """,sync,ack.resultUuid(),result);
        long audit=insert("""
                INSERT INTO audit_logs(audit_uuid,user_id,action,entity_type,entity_id,outcome,details)
                VALUES(?,?,'V3_MOBILE_RESULT_REOPENED','test_result',?,'success',?)
                """,UUID.randomUUID().toString(),u.userId(),ack.resultUuid(),requestJson);
        jdbc.update("""
                INSERT INTO mobile_result_reopens(operation_uuid,sync_id,test_result_id,reopened_by_user_id,audit_log_id,
                    previous_revision,mobile_revision,score_version,reason_code,comment,request_hash,request_json,official_score_json,response_json,reopened_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,request.operationUuid(),sync,result,u.userId(),audit,request.expectedRevision(),ack.revision(),ack.scoreVersion(),
                request.reasonCode(),request.comment(),hash,requestJson,scoreJson,responseJson,java.sql.Timestamp.from(ack.acknowledgedAt()));
    }
    private long insert(String sql,Object... args){
        var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int n=0;n<args.length;n++)s.setObject(n+1,args[n]);return s;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    public record Saved(long result,long teacher,String hash,String json,long revision,int version,String syncUuid){}
}
