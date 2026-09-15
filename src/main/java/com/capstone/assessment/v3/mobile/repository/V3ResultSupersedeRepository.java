package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.*;

@Repository
@Profile("v3")
public class V3ResultSupersedeRepository {
    private final JdbcTemplate jdbc;
    public V3ResultSupersedeRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<Saved> saved(String operation){return savedWhere("s.operation_uuid",operation);}
    public Optional<Saved> bySource(String uuid){return savedWhere("r.result_uuid",uuid);}
    private Optional<Saved> savedWhere(String column,String value){
        return jdbc.query("""
                SELECT s.test_result_id,s.replacement_result_id,s.superseded_by_user_id,s.request_hash,s.response_json,
                       s.mobile_revision,s.score_version,y.sync_uuid,r.result_uuid,n.result_uuid AS replacement_uuid
                FROM mobile_result_supersessions s JOIN syncs y ON y.sync_id=s.sync_id
                JOIN test_results r ON r.test_result_id=s.test_result_id JOIN test_results n ON n.test_result_id=s.replacement_result_id
                WHERE 
                """+column+"=? FOR UPDATE",(rs,n)->new Saved(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),rs.getString(5),
                rs.getLong(6),rs.getInt(7),rs.getString(8),rs.getString(9),rs.getString(10)),value).stream().findFirst();
    }
    public boolean latest(long assignment,long membership,int attempt){return jdbc.queryForObject("SELECT COUNT(*) FROM test_results WHERE test_assignment_id=? AND class_list_id=? AND result_status<>'superseded' AND attempt_number>?",Integer.class,assignment,membership,attempt)==0;}
    public boolean replacementUsed(long result){return jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_supersessions WHERE replacement_result_id=?",Integer.class,result)>0;}
    public boolean reopened(long result,int version,long revision,String official){return jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_reopens WHERE test_result_id=? AND score_version=? AND mobile_revision<=? AND official_score_json=?",Integer.class,result,version,revision,official)==1;}
    public boolean distinctCaptures(long old,long replacement){
        var scans=jdbc.queryForList("SELECT scan_session_id FROM test_result_scans WHERE test_result_id IN (?,?) AND link_status='selected' FOR UPDATE",Long.class,old,replacement);
        return scans.size()==2 && new HashSet<>(scans).size()==2;
    }
    public void commit(V3AuthenticatedUser u,long result,long replacement,long assignment,V3SupersedeRequest request,
            V3LifecycleAck ack,String hash,String requestJson,String oldScore,String newScore,String response){
        if(jdbc.update("UPDATE test_results SET result_status='superseded',mobile_revision=mobile_revision+1 WHERE test_result_id=? AND mobile_revision=? AND score_version=? AND result_status IN ('finalized','pending_verification')",
                result,request.expectedRevision(),request.expectedScoreVersion())!=1)throw new org.springframework.dao.OptimisticLockingFailureException("Supersession source changed");
        jdbc.update("""
                UPDATE scan_sessions n JOIN test_result_scans nl ON nl.scan_session_id=n.scan_session_id
                JOIN test_result_scans ol ON ol.test_result_id=? AND ol.link_status='selected'
                SET n.supersedes_scan_session_id=ol.scan_session_id
                WHERE nl.test_result_id=? AND nl.link_status='selected'
                """,result,replacement);
        long sync=insert("INSERT INTO syncs(sync_uuid,user_id,test_assignment_id,direction,sync_status,payload_hash,request_item_count,completed_at) VALUES(?,?,?,'upload','success',?,1,CURRENT_TIMESTAMP)",request.syncUuid(),u.userId(),assignment,hash);
        jdbc.update("INSERT INTO sync_items(sync_id,result_uuid,test_result_id,sync_action,sync_status,attempt_count,last_attempt_at,processed_at,synced_at) VALUES(?,?,?,'upsert','success',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",sync,ack.resultUuid(),result);
        long audit=insert("INSERT INTO audit_logs(audit_uuid,user_id,action,entity_type,entity_id,outcome,details) VALUES(?,?,'V3_MOBILE_RESULT_SUPERSEDED','test_result',?,'success',?)",UUID.randomUUID().toString(),u.userId(),ack.resultUuid(),requestJson);
        jdbc.update("""
                INSERT INTO mobile_result_supersessions(operation_uuid,sync_id,test_result_id,replacement_result_id,superseded_by_user_id,audit_log_id,
                    previous_revision,mobile_revision,score_version,request_hash,request_json,previous_score_json,replacement_score_json,response_json,superseded_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,request.operationUuid(),sync,result,replacement,u.userId(),audit,request.expectedRevision(),ack.revision(),ack.scoreVersion(),hash,requestJson,oldScore,newScore,response,Timestamp.from(ack.acknowledgedAt()));
    }
    private long insert(String sql,Object...args){var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int n=0;n<args.length;n++)s.setObject(n+1,args[n]);return s;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
    public record Saved(long result,long replacement,long teacher,String hash,String json,long revision,int version,String syncUuid,String resultUuid,String replacementUuid) { }
}
