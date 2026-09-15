package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3AttachmentMetadata;
import com.capstone.assessment.v3.mobile.repository.V3DetectionRepository.Page;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage.OriginalImage;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@Profile("v3")
public class V3AttachmentUploadRepository {
    private final JdbcTemplate jdbc;
    public V3AttachmentUploadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Intent> intent(String operation) {
        return jdbc.query("SELECT * FROM mobile_attachment_uploads WHERE operation_uuid=? FOR UPDATE", (rs,n) ->
            new Intent(rs.getLong("teacher_user_id"),rs.getLong("sync_id"),rs.getLong("scan_page_id"),rs.getString("request_hash"),
                rs.getString("upload_state"),rs.getString("response_json"),rs.getObject("backend_attachment_id")==null?null:rs.getLong("backend_attachment_id"),
                new OriginalImage(rs.getString("image_uuid"),"local",rs.getString("storage_key"),rs.getString("mime_type"),
                    rs.getLong("file_size_bytes"),rs.getString("content_hash"),rs.getInt("width_pixels"),rs.getInt("height_pixels"))),operation).stream().findFirst();
    }
    public boolean attachmentOccupied(String uuid) {
        return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM answer_attachments WHERE attachment_uuid=?) + (SELECT COUNT(*) FROM mobile_attachment_uploads WHERE attachment_uuid=?)",Integer.class,uuid,uuid)>0;
    }
    public Optional<Long> region(Page p, String uuid) {
        return jdbc.query("""
                SELECT g.answer_sheet_region_id FROM answer_sheet_regions g
                JOIN questions q ON q.question_id=g.question_id AND q.test_part_id=g.test_part_id AND q.question_type_id=g.question_type_id
                JOIN test_parts part ON part.test_part_id=q.test_part_id
                WHERE g.region_uuid=? AND g.answer_sheet_page_id=? AND g.answer_sheet_version_id=? AND part.test_id=? FOR UPDATE
                """,(rs,n)->rs.getLong(1),uuid,p.manifestPageId(),p.sheetId(),p.testId()).stream().findFirst();
    }
    public Optional<Evidence> evidence(Page p, String uuid) {
        return jdbc.query("""
                SELECT a.*,u.image_uuid,u.width_pixels,u.height_pixels,u.upload_state,
                       original.width_pixels AS original_width,original.height_pixels AS original_height,
                       original.upload_state AS original_state
                FROM answer_attachments a
                LEFT JOIN mobile_attachment_uploads u ON u.backend_attachment_id=a.answer_attachment_id AND u.scan_page_id=a.scan_page_id
                LEFT JOIN mobile_scan_uploads original ON original.attachment_uuid=a.attachment_uuid AND original.backend_scan_page_id=a.scan_page_id
                WHERE a.attachment_uuid=? AND a.scan_page_id=? AND a.scan_session_id=? FOR UPDATE
                """,(rs,n)->new Evidence(rs.getLong("answer_attachment_id"),rs.getString("attachment_type"),rs.getString("purge_status"),
                    rs.getObject("answer_sheet_region_id")==null?null:rs.getLong("answer_sheet_region_id"),
                    rs.getObject("source_answer_attachment_id")==null?null:rs.getLong("source_answer_attachment_id"),
                    "committed".equals(rs.getString("upload_state")) || "committed".equals(rs.getString("original_state")),
                    new OriginalImage(rs.getString("image_uuid")==null?rs.getString("attachment_uuid"):rs.getString("image_uuid"),
                        rs.getString("storage_provider"),rs.getString("storage_key"),rs.getString("mime_type"),rs.getLong("file_size_bytes"),
                        rs.getString("content_hash"),rs.getString("image_uuid")==null?rs.getInt("original_width"):rs.getInt("width_pixels"),
                        rs.getString("image_uuid")==null?rs.getInt("original_height"):rs.getInt("height_pixels"))),uuid,p.pageId(),p.scanId()).stream().findFirst();
    }
    public String originalUuid(Page p) {
        return jdbc.query("SELECT attachment_uuid FROM mobile_scan_uploads WHERE backend_scan_page_id=? AND upload_state='committed' FOR UPDATE",
                (rs,n)->rs.getString(1),p.pageId()).stream().findFirst().orElse(null);
    }
    public Optional<String> committedRequest(String uuid) {
        return jdbc.query("SELECT request_json FROM mobile_attachment_uploads WHERE attachment_uuid=? AND upload_state='committed' FOR UPDATE",
                (rs,n)->rs.getString(1),uuid).stream().findFirst();
    }
    public long reserve(V3AuthenticatedUser u, Page p, V3AttachmentMetadata r, String hash, String json, OriginalImage image) {
        long sync=insert("sync_id","""
                INSERT INTO syncs(sync_uuid,user_id,test_assignment_id,direction,sync_status,payload_hash,request_item_count)
                VALUES (?,?,?,'upload','in_progress',?,1)
                """,r.syncUuid(),u.userId(),p.assignmentId(),hash);
        jdbc.update("INSERT INTO sync_items(sync_id,result_uuid,test_result_id,sync_status) VALUES (?,?,?,'pending')",sync,p.resultUuid(),p.resultId());
        jdbc.update("""
                INSERT INTO mobile_attachment_uploads(operation_uuid,attachment_uuid,teacher_user_id,sync_id,scan_page_id,
                    request_hash,request_json,image_uuid,storage_key,mime_type,file_size_bytes,content_hash,width_pixels,height_pixels)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,r.operationUuid(),r.attachmentUuid(),u.userId(),sync,p.pageId(),hash,json,image.attachmentUuid(),image.storageKey(),
                image.mimeType(),image.fileSizeBytes(),image.contentHash(),image.width(),image.height());
        return sync;
    }
    public long insertEvidence(Page p, V3AttachmentMetadata r, OriginalImage image, Long region, Long source, String crop) {
        return insert("answer_attachment_id","""
                INSERT INTO answer_attachments(attachment_uuid,scan_session_id,scan_page_id,answer_sheet_region_id,
                    source_answer_attachment_id,attachment_type,storage_provider,storage_key,mime_type,file_size_bytes,
                    content_hash,crop_coordinates,captured_at,retention_until)
                VALUES (?,?,?,?,?,?,'local',?,?,?,?,?,?,TIMESTAMPADD(DAY,365,CURRENT_TIMESTAMP))
                """,r.attachmentUuid(),p.scanId(),p.pageId(),region,source,r.attachmentType(),image.storageKey(),image.mimeType(),
                image.fileSizeBytes(),image.contentHash(),crop,java.sql.Timestamp.from(r.capturedAt()));
    }
    public void committed(Page p, String operation, long sync, long attachment, String response) {
        jdbc.update("UPDATE mobile_attachment_uploads SET upload_state='committed',backend_attachment_id=?,response_json=?,committed_at=CURRENT_TIMESTAMP,last_error_code=NULL WHERE operation_uuid=?",attachment,response,operation);
        jdbc.update("UPDATE test_results SET mobile_revision=mobile_revision+1 WHERE test_result_id=?",p.resultId());
        jdbc.update("UPDATE syncs SET sync_status='success',completed_at=CURRENT_TIMESTAMP,error_message=NULL WHERE sync_id=?",sync);
        jdbc.update("UPDATE sync_items SET sync_status='success',attempt_count=LEAST(attempt_count+1,65535),last_attempt_at=CURRENT_TIMESTAMP,processed_at=CURRENT_TIMESTAMP,synced_at=CURRENT_TIMESTAMP,error_code=NULL,error_message=NULL WHERE sync_id=?",sync);
    }
    public void failed(String operation,String code) {
        jdbc.update("UPDATE syncs s JOIN mobile_attachment_uploads u ON u.sync_id=s.sync_id SET s.sync_status='failed' WHERE u.operation_uuid=? AND u.upload_state='pending'",operation);
        jdbc.update("UPDATE sync_items i JOIN mobile_attachment_uploads u ON u.sync_id=i.sync_id SET i.sync_status='failed',i.error_code=?,i.attempt_count=LEAST(i.attempt_count+1,65535),i.last_attempt_at=CURRENT_TIMESTAMP WHERE u.operation_uuid=? AND u.upload_state='pending'",code,operation);
        jdbc.update("UPDATE mobile_attachment_uploads SET last_error_code=? WHERE operation_uuid=? AND upload_state='pending'",code,operation);
    }
    private long insert(String key,String sql,Object...values) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement(sql,new String[]{key});for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);return s;},keys);
        return keys.getKey().longValue();
    }
    public record Intent(long teacherId,long syncId,long pageId,String hash,String state,String response,Long attachmentId,OriginalImage image) { }
    public record Evidence(long id,String type,String purge,Long regionId,Long sourceId,boolean committed,OriginalImage image) { }
}
