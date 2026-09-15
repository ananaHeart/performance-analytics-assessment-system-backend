package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.dto.V3VerificationBatch.*;
import com.capstone.assessment.v3.mobile.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class V3TeacherVerificationPersistenceTest {
    V3VerificationTestFixture f;
    V3TeacherVerificationService service;
    V3VerificationBatch batch;
    @BeforeEach void setup() throws Exception {
        f=new V3VerificationTestFixture();f.setup();
        new ResourceDatabasePopulator(new ClassPathResource("contracts/v3/mobile/verification-test-schema.sql")).execute(f.source);
        f.service.upload(f.teacher,f.pageUuid,f.request);
        service=service(new DataSourceTransactionManager(f.source));
        String result=f.jdbc.queryForObject("SELECT result_uuid FROM test_results",String.class);
        String assignment=f.jdbc.queryForObject("SELECT assignment_uuid FROM test_assignments",String.class);
        var page=new PageDecision(uuid(),f.pageUuid,"accepted",null,"Reviewed",Instant.parse("2026-01-01T01:02:03.123456789Z"));
        var answers=f.request.detections().stream().map(d->new Answer(uuid(),uuid(),d.questionUuid(),d.regionUuid(),f.pageUuid,
                new ObjectiveEvaluation("objective",d.detectionUuid()),"Teacher comment",page.clientDecidedAt())).toList();
        batch=new V3VerificationBatch("3.0",uuid(),uuid(),assignment,List.of(new Item(result,2L,List.of(page),answers)));
    }
    @AfterEach void close() throws Exception { if(f!=null)f.close(); }
    String uuid(){return UUID.randomUUID().toString();}
    V3TeacherVerificationService service(DataSourceTransactionManager manager) {
        return new V3TeacherVerificationService(new V3VerificationRepository(f.jdbc),new V3DetectionRepository(f.jdbc),
                new V3ScanUploadLedgerRepository(f.jdbc),f.validators.getValidator(),f.mapper,manager);
    }
    V3VerificationBatch items(List<Item> items){return new V3VerificationBatch("3.0",batch.syncUuid(),batch.operationUuid(),batch.assignmentUuid(),items);}
    Item item(){return batch.items().get(0);}
    int count(String table){return f.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    V3VerificationResponse.Outcome outcome(V3VerificationBatch input){return service.verify(f.teacher,input).items().get(0);}
    void noDecisions(){assertEquals(0,count("scan_verifications"));assertEquals(0,count("student_answers"));assertEquals(0,count("mobile_objective_verifications"));
        assertEquals("captured",f.jdbc.queryForObject("SELECT page_status FROM scan_pages WHERE scan_page_id=1",String.class));}
    void failed(String code,V3VerificationBatch input){var response=outcome(input);assertEquals("failed",response.status());assertEquals(code,response.error().code());}

    @Test void acceptsMcTfAndAppendsAuditWithoutComputingOfficialScores() {
        var response=outcome(batch);assertEquals("created",response.disposition());assertEquals(3,response.revision());
        assertEquals(2,count("student_answers"));assertEquals(2,count("mobile_objective_verifications"));assertEquals(1,count("scan_verifications"));
        assertEquals(0,count("answer_verifications"));assertEquals(0,f.jdbc.queryForObject("SELECT total_score FROM test_results",Integer.class));
        assertEquals(0,f.jdbc.queryForObject("SELECT items_evaluated FROM test_results",Integer.class));
        assertEquals("pending_verification",f.jdbc.queryForObject("SELECT result_status FROM test_results",String.class));
        assertEquals("accepted",f.jdbc.queryForObject("SELECT scan_status FROM scan_sessions",String.class));
        assertEquals(2,f.jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE evaluation_status='finalized' AND verified_by_user_id=42 AND verified_at IS NOT NULL AND finalized_at IS NOT NULL AND is_correct IS NULL AND points_earned=0",Integer.class));
        assertEquals(item().pageDecisions().get(0).clientDecidedAt().toString(),f.jdbc.queryForObject("SELECT client_decided_at FROM scan_verifications",String.class));
        assertNotNull(f.jdbc.queryForObject("SELECT decided_at FROM scan_verifications",java.sql.Timestamp.class));
    }
    @Test void replayPreservesRevisionAndIdsAfterResultFinalization() {
        var first=outcome(batch);f.jdbc.update("UPDATE test_results SET mobile_revision=9,result_status='finalized'");
        var second=outcome(batch);assertEquals(first.replay(),second);assertEquals(2,count("student_answers"));
        assertEquals(9,f.jdbc.queryForObject("SELECT mobile_revision FROM test_results",Integer.class));
    }
    @Test void reorderedObservationsReplay() {
        var first=outcome(batch);var answers=new ArrayList<>(item().answers());Collections.reverse(answers);
        assertEquals(first.replay(),outcome(items(List.of(new Item(item().resultUuid(),2L,item().pageDecisions(),answers)))));
    }
    @Test void changedImmutableBatchIsTopLevelConflict() {
        outcome(batch);var changed=items(List.of(new Item(item().resultUuid(),3L,item().pageDecisions(),item().answers())));
        assertEquals("VERIFICATION_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->outcome(changed)).getCode());
    }
    @Test void staleRevisionHasPermanentItemOutcomeWithoutMutation() {
        failed("REVISION_CONFLICT",items(List.of(new Item(item().resultUuid(),1L,item().pageDecisions(),item().answers()))));noDecisions();
    }
    @ParameterizedTest @ValueSource(strings={"uncertain","multiple_marks"})
    void unresolvedMarksRollbackEvenThePageDecision(String state) {
        f.jdbc.update("UPDATE omr_detections SET detection_status=?,detected_option=NULL WHERE question_id=2",state);
        failed("OBJECTIVE_RESCAN_REQUIRED",batch);noDecisions();
    }
    @Test void verifiedBlankRetainsNullOption() {
        f.jdbc.update("UPDATE omr_detections SET detection_status='blank',detected_option=NULL WHERE question_id=2");
        outcome(batch);assertEquals("blank",f.jdbc.queryForObject("SELECT answer_status FROM student_answers WHERE question_id=2",String.class));
        assertNull(f.jdbc.queryForObject("SELECT selected_question_option_id FROM student_answers WHERE question_id=2",Long.class));
    }
    @ParameterizedTest @ValueSource(strings={"rescan_requested","rejected"})
    void recordsReasonAndStopsNewAnswerAcceptance(String action) {
        var p=item().pageDecisions().get(0);var d=new PageDecision(p.verificationUuid(),p.scanPageUuid(),action,"UNCERTAIN_MARK","r".repeat(4000),p.clientDecidedAt());
        outcome(items(List.of(new Item(item().resultUuid(),2L,List.of(d),List.of()))));
        assertEquals(action,f.jdbc.queryForObject("SELECT page_status FROM scan_pages",String.class));assertEquals(0,count("student_answers"));
        assertEquals(4000,f.jdbc.queryForObject("SELECT reason_detail FROM scan_verifications",String.class).length());
        assertEquals(action,f.jdbc.queryForObject("SELECT scan_status FROM scan_sessions",String.class));
    }
    @Test void pageMustBeAcceptedBeforeAnswers() {
        failed("PAGE_NOT_ACCEPTED",items(List.of(new Item(item().resultUuid(),2L,List.of(),item().answers()))));noDecisions();
    }
    @Test void acceptsAnswersInLaterOperationAfterPageOnlyAcceptance() {
        outcome(items(List.of(new Item(item().resultUuid(),2L,item().pageDecisions(),List.of()))));
        var later=new V3VerificationBatch("3.0",uuid(),uuid(),batch.assignmentUuid(),List.of(new Item(item().resultUuid(),3L,List.of(),item().answers())));
        assertEquals(4,outcome(later).revision());assertEquals(2,count("student_answers"));
    }
    @Test void missingDetectionIsRetryableAndNoPageDecisionSurvives() {
        f.jdbc.update("DELETE FROM omr_detections WHERE question_id=2");
        var response=outcome(batch);assertTrue(response.error().retryable());assertEquals("DEPENDENCY_NOT_READY",response.error().code());noDecisions();
    }
    @Test void wrongRegionQuestionPairRollsBackWholeResult() {
        var a=item().answers().get(0);var invalid=new Answer(a.answerUuid(),a.verificationUuid(),item().answers().get(1).questionUuid(),a.regionUuid(),a.scanPageUuid(),a.evaluation(),a.comment(),a.clientDecidedAt());
        failed("VERIFICATION_REGION_MISMATCH",items(List.of(new Item(item().resultUuid(),2L,item().pageDecisions(),List.of(invalid)))));noDecisions();
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE roles SET role_name='principal'","UPDATE sections SET school_id='OTHER'","UPDATE students SET school_id='OTHER'","UPDATE tests SET school_id='OTHER'"})
    void unauthorizedBatchWritesNoRegistrationOrItems(String sql) {
        f.jdbc.update(sql);assertEquals("VERIFICATION_FORBIDDEN",assertThrows(V3AuthException.class,()->outcome(batch)).getCode());
        assertEquals(0,count("mobile_verification_batches"));noDecisions();
    }
    @Test void foreignResultInBatchPreventsAllItems() {
        f.jdbc.update("INSERT INTO test_assignments VALUES(101,?,200,13,'open',NULL,NULL,FALSE)",uuid());
        String foreign=uuid();f.jdbc.update("INSERT INTO test_results(result_uuid,test_assignment_id,class_list_id,attempt_number,total_score,max_score,items_evaluated,result_status) VALUES(?,101,41001,1,0,4,0,'draft')",foreign);
        var bad=new Item(foreign,1L,List.of(new PageDecision(uuid(),uuid(),"accepted",null,null,Instant.now())),List.of());
        assertEquals("VERIFICATION_FORBIDDEN",assertThrows(V3AuthException.class,()->outcome(items(List.of(item(),bad)))).getCode());
        assertEquals(0,count("mobile_verification_batches"));noDecisions();
    }
    @Test void partialSuccessPersistsGoodResultAndRetriesOnlyTransientFailure() {
        String missing=uuid();var unknown=new Item(missing,1L,List.of(new PageDecision(uuid(),uuid(),"accepted",null,null,Instant.now())),List.of());
        var request=items(List.of(item(),unknown));var first=service.verify(f.teacher,request);
        assertEquals("partial_success",first.syncStatus());assertEquals(2,count("student_answers"));
        var second=service.verify(f.teacher,request);
        assertEquals("partial_success",second.syncStatus());assertEquals("replayed",second.items().stream().filter(o->o.resultUuid().equals(item().resultUuid())).findFirst().orElseThrow().disposition());
        assertEquals(3,f.jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=1",Integer.class));
        assertEquals("partial_success",f.jdbc.queryForObject("SELECT sync_status FROM syncs WHERE sync_uuid=?",String.class,batch.syncUuid()));
    }
    @Test void invalidBatchReasonAndDuplicateIdsAreTopLevelRejections() {
        var p=item().pageDecisions().get(0);var invalid=new PageDecision(p.verificationUuid(),p.scanPageUuid(),"rejected",null,null,p.clientDecidedAt());
        assertThrows(V3AuthException.class,()->outcome(items(List.of(new Item(item().resultUuid(),2L,List.of(invalid),List.of())))));
        assertThrows(V3AuthException.class,()->outcome(items(List.of(item(),item()))));assertEquals(0,count("mobile_verification_batches"));
    }
    @Test void existingAnswerCannotBeOverwrittenByNewOperation() {
        outcome(batch);var a=item().answers().get(0);var changed=new Answer(a.answerUuid(),uuid(),a.questionUuid(),a.regionUuid(),a.scanPageUuid(),a.evaluation(),a.comment(),a.clientDecidedAt());
        var next=new V3VerificationBatch("3.0",uuid(),uuid(),batch.assignmentUuid(),List.of(new Item(item().resultUuid(),3L,List.of(),List.of(changed))));
        failed("ANSWER_ALREADY_VERIFIED",next);assertEquals(2,count("student_answers"));
    }
    @Test void revokedOwnerCannotReplay() {
        outcome(batch);f.jdbc.update("UPDATE roles SET role_name='principal'");assertThrows(V3AuthException.class,()->outcome(batch));
    }
    @Test void resultMustRemainUnfinalizedForNewOperation() {
        f.jdbc.update("UPDATE test_results SET result_status='finalized'");failed("RESULT_LOCKED",batch);noDecisions();
    }
    @Test void concurrentIdenticalCallsCreateOneDecisionSet() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {var a=executor.submit(()->{start.await();return service.verify(f.teacher,batch);});
            var other=service(new DataSourceTransactionManager(f.source));var b=executor.submit(()->{start.await();return other.verify(f.teacher,batch);});start.countDown();
            var first=a.get(25,TimeUnit.SECONDS).items().get(0);var second=b.get(25,TimeUnit.SECONDS).items().get(0);
            assertEquals(first.idMappings(),second.idMappings());assertNotEquals(first.disposition(),second.disposition());
            assertEquals(2,count("student_answers"));assertEquals(1,count("scan_verifications"));
        }finally{executor.shutdownNow();}
    }
    @Test void failedItemCommitRollsBackThenSameOperationRetries() {
        var manager=new DataSourceTransactionManager(f.source){int commits;
            @Override protected void doCommit(DefaultTransactionStatus status){if(++commits==2)throw new TransactionSystemException("Injected item commit rollback");super.doCommit(status);}};
        manager.setRollbackOnCommitFailure(true);
        var failed=service(manager).verify(f.teacher,batch).items().get(0);assertTrue(failed.error().retryable());noDecisions();
        assertEquals("created",outcome(batch).disposition());assertEquals(2,count("student_answers"));
    }
    @Test void concurrentDifferentOperationsCannotBothConsumeTheSameRevision() throws Exception {
        var otherBatch=new V3VerificationBatch("3.0",uuid(),uuid(),batch.assignmentUuid(),batch.items());
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {var a=executor.submit(()->{start.await();return service.verify(f.teacher,batch).items().get(0);});
            var other=service(new DataSourceTransactionManager(f.source));
            var b=executor.submit(()->{start.await();return other.verify(f.teacher,otherBatch).items().get(0);});start.countDown();
            var outcomes=List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS));
            assertEquals(1,outcomes.stream().filter(o->"success".equals(o.status())).count());
            assertEquals("REVISION_CONFLICT",outcomes.stream().filter(o->"failed".equals(o.status())).findFirst().orElseThrow().error().code());
            assertEquals(3,f.jdbc.queryForObject("SELECT mobile_revision FROM test_results",Integer.class));assertEquals(1,count("scan_verifications"));
        }finally{executor.shutdownNow();}
    }
    @Test void lostItemCommitAcknowledgementResolvesSavedSuccess() {
        var manager=new DataSourceTransactionManager(f.source){int commits;
            @Override protected void doCommit(DefaultTransactionStatus status){super.doCommit(status);if(++commits==2)throw new TransactionSystemException("Injected lost item acknowledgement");}};
        assertEquals("replayed",service(manager).verify(f.teacher,batch).items().get(0).disposition());
        assertEquals(2,count("student_answers"));assertEquals("replayed",outcome(batch).disposition());
    }
    @Test void lostRegistrationAcknowledgementLeavesResumableBatch() {
        var manager=new DataSourceTransactionManager(f.source){@Override protected void doCommit(DefaultTransactionStatus status){super.doCommit(status);throw new TransactionSystemException("Injected lost batch acknowledgement");}};
        assertThrows(V3AuthException.class,()->service(manager).verify(f.teacher,batch));noDecisions();
        assertEquals(1,count("mobile_verification_batches"));assertEquals("created",outcome(batch).disposition());
    }
}
