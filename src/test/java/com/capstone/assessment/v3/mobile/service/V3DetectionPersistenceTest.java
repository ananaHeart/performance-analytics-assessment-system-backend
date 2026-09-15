package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3DetectionBatch;
import com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection;
import com.capstone.assessment.v3.mobile.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class V3DetectionPersistenceTest {
    private final V3AuthenticatedUser teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private DriverManagerDataSource source;
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private ValidatorFactory validators;
    private V3DetectionUploadService service;
    private String pageUuid;
    private V3DetectionBatch request;

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
    @AfterEach void close() throws Exception { if(keepAlive!=null)keepAlive.close(); if(validators!=null)validators.close(); }
    private V3DetectionUploadService service(DataSourceTransactionManager manager) {
        return new V3DetectionUploadService(new V3DetectionRepository(jdbc),new V3ScanUploadLedgerRepository(jdbc),validators.getValidator(),mapper,manager);
    }
    private Detection detection(int i,String status,String option) {
        return new Detection(uuid(),jdbc.queryForObject("SELECT region_uuid FROM answer_sheet_regions WHERE answer_sheet_region_id=?",String.class,i),
                jdbc.queryForObject("SELECT question_uuid FROM questions WHERE question_id=?",String.class,i),status,option,new BigDecimal("0.98"));
    }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private V3DetectionBatch batch(List<Detection> rows) { return new V3DetectionBatch("3.0",request.syncUuid(),request.operationUuid(),rows); }
    private void rejected(String code,V3DetectionBatch input) {
        assertEquals(code,assertThrows(V3AuthException.class,()->service.upload(teacher,pageUuid,input)).getCode());
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    private void assertEmpty() { assertEquals(0,count("omr_detections")); assertEquals(0,count("mobile_detection_uploads"));
        assertEquals(0,count("syncs")); assertEquals(1,jdbc.queryForObject("SELECT mobile_revision FROM test_results",Integer.class)); }

    @Test void persistsMcAndTfResolvedIdsWithoutOfficialAnswersOrScores() throws Exception {
        var response=service.upload(teacher,pageUuid,request);
        assertEquals("created",response.disposition()); assertEquals(2,response.revision()); assertEquals(2,response.idMappings().size());
        assertEquals(2,count("omr_detections")); assertEquals(1,count("sync_items"));
        assertEquals("B",jdbc.queryForObject("SELECT detected_option FROM omr_detections WHERE question_id=2",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT total_score FROM test_results",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT items_evaluated FROM test_results",Integer.class));
        assertEquals("draft",jdbc.queryForObject("SELECT result_status FROM test_results",String.class));
        assertEquals("captured",jdbc.queryForObject("SELECT page_status FROM scan_pages",String.class));
        assertEquals("mobile_summary_v3",mapper.readTree(jdbc.queryForObject("SELECT raw_mark FROM omr_detections WHERE question_id=1",String.class)).get("source").asText());
    }
    @Test void replaysOriginalRevisionAndMappingsAfterLifecycleChangesAndReordering() {
        var first=service.upload(teacher,pageUuid,request);
        jdbc.update("UPDATE test_results SET result_status='finalized',mobile_revision=7");
        jdbc.update("UPDATE scan_pages SET page_status='accepted'"); jdbc.update("UPDATE answer_sheet_versions SET generation_status='retired'");
        var reversed=new ArrayList<>(request.detections()); Collections.reverse(reversed);
        var replay=service.upload(teacher,pageUuid,batch(reversed));
        assertEquals(first.replay(),replay); assertEquals(2,count("omr_detections"));
        assertEquals(7,jdbc.queryForObject("SELECT mobile_revision FROM test_results",Integer.class));
    }
    @Test void changedOperationContentConflicts() {
        service.upload(teacher,pageUuid,request); var d=request.detections().get(0);
        rejected("DETECTION_IDENTITY_CONFLICT",batch(List.of(new Detection(d.detectionUuid(),d.regionUuid(),d.questionUuid(),"detected","B",d.confidence()),request.detections().get(1))));
        assertEquals(2,count("omr_detections"));
    }
    @Test void equivalentConfidenceSpellingReplaysButChangedConfidenceConflicts() {
        var d=request.detections().get(0);
        var first=service.upload(teacher,pageUuid,batch(List.of(d)));
        var equivalent=new Detection(d.detectionUuid(),d.regionUuid(),d.questionUuid(),d.detectionStatus(),d.detectedOption(),new BigDecimal("0.9800"));
        assertEquals(first.replay(),service.upload(teacher,pageUuid,batch(List.of(equivalent))));
        var changed=new Detection(d.detectionUuid(),d.regionUuid(),d.questionUuid(),d.detectionStatus(),d.detectedOption(),new BigDecimal("0.98001"));
        rejected("DETECTION_IDENTITY_CONFLICT",batch(List.of(changed)));
    }
    @Test void observationQuestionMustMatchRegionEvenWhenBothBelongToPage() {
        var d=request.detections().get(0);
        rejected("DETECTION_REGION_MISMATCH",batch(List.of(new Detection(d.detectionUuid(),d.regionUuid(),request.detections().get(1).questionUuid(),"detected","A",d.confidence()))));
        assertEmpty();
    }
    @Test void newOperationCannotReplaceAnOccupiedRegion() {
        service.upload(teacher,pageUuid,request);
        rejected("DETECTION_IDENTITY_CONFLICT",new V3DetectionBatch("3.0",uuid(),uuid(),List.of(detection(1,"detected","B"))));
        assertEquals(1,count("mobile_detection_uploads"));
    }
    @Test void disjointOperationsAdvanceRevisionOnceEach() {
        var first=service.upload(teacher,pageUuid,batch(List.of(request.detections().get(0))));
        var next=new V3DetectionBatch("3.0",uuid(),uuid(),List.of(request.detections().get(1)));
        assertEquals(3,service.upload(teacher,pageUuid,next).revision());
        assertEquals(first.replay(),service.upload(teacher,pageUuid,batch(List.of(request.detections().get(0)))));
    }
    @ParameterizedTest @ValueSource(strings={"blank","multiple_marks","uncertain"})
    void preservesAmbiguityWithoutSelectingOption(String status) {
        service.upload(teacher,pageUuid,batch(List.of(detection(1,status,null))));
        assertNull(jdbc.queryForObject("SELECT detected_option FROM omr_detections",String.class));
        assertEquals(status,jdbc.queryForObject("SELECT detection_status FROM omr_detections",String.class));
    }
    @ParameterizedTest @ValueSource(strings={"blank","multiple_marks","uncertain"})
    void rejectsAnOptionForAmbiguousStates(String status) { rejected("VALIDATION_FAILED",batch(List.of(detection(1,status,"A")))); assertEmpty(); }
    @Test void detectedRequiresOption() { rejected("VALIDATION_FAILED",batch(List.of(detection(1,"detected",null)))); assertEmpty(); }
    @ParameterizedTest @ValueSource(strings={"T","F","C","D"})
    void tfUsesOnlyStoredAOrB(String option) { rejected(Set.of("T","F").contains(option)?"VALIDATION_FAILED":"DETECTION_OPTION_MISMATCH",batch(List.of(detection(2,"detected",option)))); assertEmpty(); }
    @Test void duplicateRegionOrDetectionWithinBatchRejected() {
        rejected("VALIDATION_FAILED",batch(List.of(request.detections().get(0),request.detections().get(0)))); assertEmpty();
    }
    @Test void badSecondRegionRollsBackWholeBatch() {
        // UUID sorting puts the valid observation first, ensuring an insert is rolled back.
        var d=request.detections().get(0);
        var good=new Detection("00000000-0000-4000-8000-000000000001",d.regionUuid(),d.questionUuid(),"detected","A",d.confidence());
        var bad=new Detection("ffffffff-ffff-4fff-8fff-ffffffffffff",uuid(),uuid(),"detected","B",d.confidence());
        rejected("DETECTION_REGION_MISMATCH",batch(List.of(good,bad))); assertEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE class_assignments SET user_id=99","UPDATE sections SET school_id='OTHER'","UPDATE students SET school_id='OTHER'","UPDATE tests SET school_id='OTHER'","UPDATE roles SET role_name='principal'","UPDATE statuses SET status_name='disabled'"})
    void rejectsRevokedOrCrossScopeAccess(String sql) {
        jdbc.update("INSERT INTO users VALUES(99,'S1',1,1)"); jdbc.update(sql);
        rejected("SCAN_CONTEXT_NOT_FOUND",request); assertEmpty();
    }
    @Test void replayStillRequiresCurrentOwner() {
        service.upload(teacher,pageUuid,request); jdbc.update("UPDATE sections SET school_id='OTHER'");
        rejected("SCAN_CONTEXT_NOT_FOUND",request);
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE test_results SET result_status='finalized'","UPDATE scan_pages SET page_status='accepted'","UPDATE scan_sessions SET scan_status='verified'","UPDATE test_result_scans SET link_status='rejected'"})
    void newDetectionsCannotMutateLockedCapture(String sql) { jdbc.update(sql); rejected("DETECTION_TARGET_LOCKED",request); assertEmpty(); }
    @Test void requiresCommittedPageDependency() { jdbc.update("DELETE FROM mobile_scan_uploads"); rejected("SCAN_CONTEXT_NOT_FOUND",request); assertEmpty(); }
    @Test void validatesQuestionPairAndObjectiveType() {
        jdbc.update("UPDATE answer_sheet_regions SET question_type_id=3 WHERE answer_sheet_region_id=1");
        rejected("DETECTION_REGION_MISMATCH",request); assertEmpty();
        jdbc.update("UPDATE questions SET question_type_id=3 WHERE question_id=1");
        rejected("OBJECTIVE_REGION_REQUIRED",request); assertEmpty();
    }
    @Test void requiresMatchingImmutableOptionsAndCurrentVersion() {
        jdbc.update("UPDATE question_options SET is_active=FALSE WHERE question_id=1 AND option_key='D'");
        rejected("DETECTION_OPTION_MISMATCH",request); assertEmpty();
        jdbc.update("UPDATE tests SET version_number=2"); rejected("ASSESSMENT_SNAPSHOT_MISMATCH",request); assertEmpty();
    }
    @Test void detectsSyncReuseAcrossStages() {
        jdbc.update("INSERT INTO syncs(sync_uuid,user_id,test_assignment_id,direction,sync_status) VALUES(?,42,100,'upload','success')",request.syncUuid());
        rejected("SYNC_IDENTITY_CONFLICT",request); assertEquals(0,count("omr_detections"));
    }
    @ParameterizedTest @ValueSource(strings={"-0.1","1.01"})
    void rejectsOutOfRangeConfidence(String confidence) { var d=request.detections().get(0);
        rejected("VALIDATION_FAILED",batch(List.of(new Detection(d.detectionUuid(),d.regionUuid(),d.questionUuid(),"detected","A",new BigDecimal(confidence))))); assertEmpty(); }
    @Test void preservesExactConfidenceAlongsideRoundedStorageProjection() throws Exception {
        var d=request.detections().get(0); var precise=new Detection(d.detectionUuid(),d.regionUuid(),d.questionUuid(),"detected","A",new BigDecimal("0.987654"));
        service.upload(teacher,pageUuid,batch(List.of(precise)));
        assertEquals(new BigDecimal("0.9877"),jdbc.queryForObject("SELECT confidence_score FROM omr_detections",BigDecimal.class));
        var raw=mapper.readTree(jdbc.queryForObject("SELECT raw_mark FROM omr_detections",String.class));
        assertEquals("0.987654",raw.get("observation").get("confidence").asText());
    }
    @Test void emptyAndOversizeBatchesRejected() {
        rejected("VALIDATION_FAILED",batch(List.of())); rejected("VALIDATION_FAILED",batch(Collections.nCopies(201,request.detections().get(0)))); assertEmpty();
    }
    @Test void concurrentServiceInstancesCreateOneReceipt() throws Exception {
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try { var a=executor.submit(()->{start.await();return service.upload(teacher,pageUuid,request);});
            var other=service(new DataSourceTransactionManager(source));
            var b=executor.submit(()->{start.await();return other.upload(teacher,pageUuid,request);}); start.countDown();
            var first=a.get(20,TimeUnit.SECONDS);var second=b.get(20,TimeUnit.SECONDS);
            assertEquals(first.idMappings(),second.idMappings()); assertNotEquals(first.disposition(),second.disposition());
            assertEquals(2,count("omr_detections"));assertEquals(1,count("mobile_detection_uploads"));
        } finally {executor.shutdownNow();}
    }
    @Test void failedCommitRollsBackAllRowsAndCanRetry() {
        var manager=new DataSourceTransactionManager(source) { @Override protected void doCommit(DefaultTransactionStatus status) { throw new TransactionSystemException("Injected rollback"); } };
        manager.setRollbackOnCommitFailure(true);
        assertThrows(V3AuthException.class,()->service(manager).upload(teacher,pageUuid,request)); assertEmpty();
        assertEquals("created",service.upload(teacher,pageUuid,request).disposition());
    }
    @Test void lostCommitAcknowledgementReplaysPersistedReceipt() {
        var manager=new DataSourceTransactionManager(source) { @Override protected void doCommit(DefaultTransactionStatus status) { super.doCommit(status);throw new TransactionSystemException("Injected lost response"); } };
        assertThrows(V3AuthException.class,()->service(manager).upload(teacher,pageUuid,request));
        assertEquals("replayed",service.upload(teacher,pageUuid,request).disposition());assertEquals(1,count("mobile_detection_uploads"));
    }
}
