package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3DetectionBatch;
import com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection;
import com.capstone.assessment.v3.mobile.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;

final class V3VerificationTestFixture implements AutoCloseable {
    final V3AuthenticatedUser teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    DriverManagerDataSource source;
    Connection keepAlive;
    JdbcTemplate jdbc;
    ValidatorFactory validators;
    V3DetectionUploadService service;
    String pageUuid;
    V3DetectionBatch request;

    @BeforeEach void setup() throws Exception {
        source=new DriverManagerDataSource("jdbc:h2:mem:det_"+uuid()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa","");
        keepAlive=source.getConnection(); jdbc=new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("contracts/v3/mobile/scan-ingestion-test-schema.sql"),
                new ClassPathResource("contracts/v3/mobile/detection-test-schema.sql")).execute(source);
        validators=Validation.buildDefaultValidatorFactory(); service=service(new DataSourceTransactionManager(source));
        jdbc.update("INSERT INTO statuses VALUES(1,'active')"); jdbc.update("INSERT INTO roles VALUES(1,'teacher')");
        jdbc.update("INSERT INTO users VALUES(42,'S1',1,1)");
        jdbc.update("INSERT INTO sections VALUES(2,'S1')"); jdbc.update("INSERT INTO classes VALUES(3,2,'active')");
        jdbc.update("INSERT INTO class_assignments VALUES(13,3,42,'active')"); jdbc.update("INSERT INTO students VALUES(77,'S1','active')");
        jdbc.update("INSERT INTO class_lists VALUES(41001,3,77,'enrolled')");
        jdbc.update("INSERT INTO tests VALUES(200,'S1',1,2,'active')");
        jdbc.update("INSERT INTO test_assignments VALUES(100,?,200,13,'open',NULL,NULL,FALSE)",uuid());
        jdbc.update("INSERT INTO question_types VALUES(1,'multiple_choice'),(2,'true_false'),(3,'essay')");
        jdbc.update("INSERT INTO test_parts VALUES(10,200)"); jdbc.update("INSERT INTO paper_sizes VALUES(1,'A4')");
        jdbc.update("INSERT INTO omr_templates VALUES(500,'SYNTHETIC-MC-TF','1','3.0.0','active')");
        jdbc.update("INSERT INTO answer_sheet_versions VALUES(300,?,100,1,1,2,1,'ready')",uuid());
        jdbc.update("INSERT INTO answer_sheet_pages VALUES(400,?,300,500,1,1,'{}',?,'ready')",uuid(),"a".repeat(64));
        for(int i=1;i<=2;i++) {
            jdbc.update("INSERT INTO questions VALUES(?,10,?,2,?)",i,i,uuid());
            jdbc.update("INSERT INTO answer_sheet_regions VALUES(?,400,?,?,300,10,?,'objective_bubbles',?)",i,i,uuid(),i,
                    i==1?"{\"option_keys\":[\"A\",\"B\",\"C\",\"D\"]}":"{\"option_keys\":[\"A\",\"B\"]}");
            for(String key:(i==1?List.of("A","B","C","D"):List.of("A","B")))
                jdbc.update("INSERT INTO question_options(question_id,option_key) VALUES(?,?)",i,key);
        }
        jdbc.update("INSERT INTO test_results(test_result_id,result_uuid,test_assignment_id,class_list_id,attempt_number,total_score,max_score,items_evaluated,result_status) VALUES(1,?,100,41001,1,0,4,0,'draft')",uuid());
        jdbc.update("INSERT INTO scan_sessions(scan_session_id,scan_uuid,answer_sheet_version_id,omr_template_id,test_assignment_id,class_list_id,expected_page_count,captured_page_count,scanned_by_user_id,scanner_version,scan_status,scanned_at) VALUES(1,?,300,500,100,41001,1,1,42,'3.0.0','captured',CURRENT_TIMESTAMP)",uuid());
        jdbc.update("INSERT INTO test_result_scans(test_result_id,scan_session_id,link_status,decided_by_user_id) VALUES(1,1,'selected',42)");
        pageUuid=uuid();
        jdbc.update("INSERT INTO scan_pages(scan_page_id,scan_page_uuid,scan_session_id,answer_sheet_page_id,omr_template_id,page_number,capture_number,scanner_version,qr_payload,qr_payload_hash,image_hash,page_status,captured_at) VALUES(1,?,1,400,500,1,1,'3.0.0','{}',?,?,'captured',CURRENT_TIMESTAMP)",pageUuid,"a".repeat(64),"b".repeat(64));
        jdbc.update("INSERT INTO mobile_scan_uploads(scan_page_uuid,teacher_user_id,school_id,sync_uuid,request_hash,request_json,attachment_uuid,storage_key,file_size_bytes,content_hash,width_pixels,height_pixels,upload_state,backend_scan_page_id,receipt_page_status,committed_at) VALUES(?,42,'S1',?,?,'{}',?,'fixture.jpg',100,?,10,10,'committed',1,'captured',CURRENT_TIMESTAMP)",pageUuid,uuid(),"c".repeat(64),uuid(),"b".repeat(64));
        request=new V3DetectionBatch("3.0",uuid(),uuid(),List.of(detection(1,"detected","A"),detection(2,"detected","B")));
    }
    @Override public void close() throws Exception { if(keepAlive!=null)keepAlive.close(); if(validators!=null)validators.close(); }
    V3DetectionUploadService service(DataSourceTransactionManager manager) {
        return new V3DetectionUploadService(new V3DetectionRepository(jdbc),new V3ScanUploadLedgerRepository(jdbc),validators.getValidator(),mapper,manager);
    }
    Detection detection(int i,String status,String option) {
        return new Detection(uuid(),jdbc.queryForObject("SELECT region_uuid FROM answer_sheet_regions WHERE answer_sheet_region_id=?",String.class,i),
                jdbc.queryForObject("SELECT question_uuid FROM questions WHERE question_id=?",String.class,i),status,option,new BigDecimal("0.98"));
    }
    static String uuid() { return UUID.randomUUID().toString(); }
}
