package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.capstone.assessment.v3.mobile.dto.V3AttachmentMetadata;
import com.capstone.assessment.v3.mobile.dto.V3WrittenVerificationBatch;
import com.capstone.assessment.v3.mobile.dto.V3CorrectionRequest;
import com.capstone.assessment.v3.mobile.dto.V3AttachmentMetadata.Crop;
import com.capstone.assessment.v3.mobile.repository.V3AttachmentUploadRepository;
import com.capstone.assessment.v3.mobile.dto.V3VerificationBatch;
import com.capstone.assessment.v3.mobile.dto.V3VerificationBatch.*;
import com.capstone.assessment.v3.mobile.repository.V3VerificationRepository;
import com.capstone.assessment.v3.mobile.repository.V3DetectionRepository;
import com.capstone.assessment.v3.mobile.repository.V3ScanPageRepository;
import com.capstone.assessment.v3.mobile.repository.V3ScanUploadLedgerRepository;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, synthetic fixtures only. Requires the full canonical schema + applied deltas in the temporary instance. */
@EnabledIfSystemProperty(named = "v3.scan.mariadb.database", matches = "v3_scan_validation_full_[0-9]{6}")
class V3ScanUploadMariaDbTest {
    @TempDir Path directory;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource source;
    private ValidatorFactory validators;
    private V3OriginalScanImageStorage storage;
    private V3ScanPageIngestionService service;
    private V3AuthenticatedUser teacher;
    private V3ScanPageUploadMetadata request;
    private byte[] image;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach void setup() throws Exception {
        String database = System.getProperty("v3.scan.mariadb.database");
        assertTrue(database.matches("v3_scan_validation_full_[0-9]{6}"));
        // Host and port are deliberately fixed to the separate validation instance, never app datasource settings.
        source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:33317/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", "root", "scan_validation_only");
        jdbc = new JdbcTemplate(source);
        assertEquals(79, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'", Integer.class));
        validators = Validation.buildDefaultValidatorFactory();
        storage = new V3OriginalScanImageStorage(directory.toString());
        service = service(new DataSourceTransactionManager(source));
        seed();
    }

    @AfterEach void close() { if (validators != null) validators.close(); }

    @Test void simultaneousRetriesCommitOnePageAndReplayAfterLifecycleChanges() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> { start.await(); return service.ingest(teacher, request, upload()); });
            var b = executor.submit(() -> { start.await(); return service.ingest(teacher, request, upload()); });
            start.countDown();
            var first = a.get(25, TimeUnit.SECONDS);
            var second = b.get(25, TimeUnit.SECONDS);
            assertEquals(first.backendScanPageId(), second.backendScanPageId());
            assertTrue(List.of(first.uploadStatus(), second.uploadStatus()).containsAll(List.of("created", "replayed")));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM scan_pages WHERE scan_page_uuid = ?", Integer.class, request.scanPageUuid()));
            jdbc.update("UPDATE test_results SET result_status = 'finalized' WHERE result_uuid = ?", request.resultUuid());
            jdbc.update("UPDATE scan_pages SET page_status = 'accepted' WHERE scan_page_uuid = ?", request.scanPageUuid());
            jdbc.update("UPDATE test_assignments SET assignment_status = 'closed' WHERE assignment_uuid = ?", request.assignmentUuid());
            var replay = service.ingest(teacher, request, upload());
            assertEquals("replayed", replay.uploadStatus());
            assertEquals("captured", replay.pageStatus());
            assertEquals(first.backendScanPageId(), replay.backendScanPageId());
            var changed = new V3ScanPageUploadMetadata(request.contractVersion(), request.syncUuid(), request.resultUuid(),
                    request.scanUuid(), request.scanPageUuid(), request.answerSheetUuid(), request.pageUuid(), request.assignmentUuid(),
                    request.classListId(), 1, 1, request.scannerVersion(), request.qrPayloadHash(), request.imageHash(), request.capturedAt().plusSeconds(1));
            assertEquals("SCAN_IDENTITY_CONFLICT", assertThrows(V3AuthException.class, () -> service.ingest(teacher, changed, upload())).getCode());
        } finally { executor.shutdownNow(); }
    }

    @Test void databaseRollbackAfterPublicationIsRecoverableWithTheSameAttachment() {
        var manager = new DataSourceTransactionManager(source) {
            int commits;
            @Override protected void doCommit(DefaultTransactionStatus status) {
                if (++commits == 3) throw new TransactionSystemException("Injected pre-commit connection failure");
                super.doCommit(status);
            }
        };
        manager.setRollbackOnCommitFailure(true);
        assertThrows(V3AuthException.class, () -> service(manager).ingest(teacher, request, upload()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM scan_pages WHERE scan_page_uuid = ?", Integer.class, request.scanPageUuid()));
        String attachment = jdbc.queryForObject("SELECT attachment_uuid FROM mobile_scan_uploads WHERE scan_page_uuid = ?", String.class, request.scanPageUuid());
        var conflictingBatch = new V3ScanPageUploadMetadata(request.contractVersion(), request.syncUuid(), uuid(),
                uuid(), uuid(), request.answerSheetUuid(), request.pageUuid(), request.assignmentUuid(), request.classListId(),
                1, 1, request.scannerVersion(), request.qrPayloadHash(), request.imageHash(), request.capturedAt());
        assertEquals("SYNC_IDENTITY_CONFLICT", assertThrows(V3AuthException.class,
                () -> service.ingest(teacher, conflictingBatch, upload())).getCode());
        var recovered = service.recover(request.scanPageUuid());
        assertEquals("created", recovered.uploadStatus());
        assertEquals(attachment, jdbc.queryForObject("SELECT attachment_uuid FROM answer_attachments WHERE scan_page_id = ?", String.class, recovered.backendScanPageId()));
        assertEquals("replayed", service.ingest(teacher, request, upload()).uploadStatus());
    }

    @Test void lostCommitAcknowledgementReplaysWithoutDuplicateRows() {
        var manager = new DataSourceTransactionManager(source) {
            int commits;
            @Override protected void doCommit(DefaultTransactionStatus status) {
                super.doCommit(status);
                if (++commits == 3) throw new TransactionSystemException("Injected lost acknowledgement after actual commit");
            }
        };
        assertThrows(V3AuthException.class, () -> service(manager).ingest(teacher, request, upload()));
        assertEquals("committed", jdbc.queryForObject("SELECT upload_state FROM mobile_scan_uploads WHERE scan_page_uuid = ?", String.class, request.scanPageUuid()));
        assertEquals("replayed", service.ingest(teacher, request, upload()).uploadStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_results WHERE result_uuid = ?", Integer.class, request.resultUuid()));
    }

    @Test void concurrentConflictingPayloadHasOneWinnerAndDatabaseEnforcesReceiptIntegrity() throws Exception {
        var changed = new V3ScanPageUploadMetadata(request.contractVersion(), request.syncUuid(), request.resultUuid(),
                request.scanUuid(), request.scanPageUuid(), request.answerSheetUuid(), request.pageUuid(), request.assignmentUuid(),
                request.classListId(), 1, 1, request.scannerVersion(), request.qrPayloadHash(), request.imageHash(), request.capturedAt().plusSeconds(1));
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return outcome(request); });
            var second = executor.submit(() -> { start.await(); return outcome(changed); });
            start.countDown();
            var outcomes = List.of(first.get(25, TimeUnit.SECONDS), second.get(25, TimeUnit.SECONDS));
            assertTrue(outcomes.containsAll(List.of("created", "SCAN_IDENTITY_CONFLICT")));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM scan_pages WHERE scan_page_uuid = ?", Integer.class, request.scanPageUuid()));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE mobile_scan_uploads SET request_json='not json' WHERE scan_page_uuid=?", request.scanPageUuid()));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE mobile_scan_uploads SET upload_state='pending' WHERE scan_page_uuid=?", request.scanPageUuid()));
        } finally { executor.shutdownNow(); }
    }

    private String outcome(V3ScanPageUploadMetadata input) {
        try { return service.ingest(teacher, input, upload()).uploadStatus(); }
        catch (V3AuthException error) { return error.getCode(); }
    }

    @Test void mcDetectionsCommitAtomicallyAndReplayTheirOriginalReceipt() {
        var batch = detectionFixture(false);
        var detections = detectionService(new DataSourceTransactionManager(source));
        var first = detections.upload(teacher, request.scanPageUuid(), batch);
        assertEquals(2, first.idMappings().size());
        assertEquals(2, first.revision());
        assertEquals(0, jdbc.queryForObject("SELECT total_score FROM test_results WHERE result_uuid=?", Integer.class, request.resultUuid()));
        assertEquals(0, jdbc.queryForObject("SELECT items_evaluated FROM test_results WHERE result_uuid=?", Integer.class, request.resultUuid()));
        jdbc.update("UPDATE test_results SET result_status='finalized',mobile_revision=6 WHERE result_uuid=?",request.resultUuid());
        assertEquals(first.replay(), detections.upload(teacher, request.scanPageUuid(), batch));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE mobile_detection_uploads SET response_json='invalid json' WHERE operation_uuid=?",batch.operationUuid()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE test_results SET mobile_revision=0 WHERE result_uuid=?",request.resultUuid()));
    }

    @Test void tfDetectionUsesStoredBAndPreservesUncertainty() {
        var batch = detectionFixture(true);
        var second = batch.detections().get(1);
        var uncertain = new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection(second.detectionUuid(),
                second.regionUuid(),second.questionUuid(),"uncertain",null,new java.math.BigDecimal("0.432167"));
        batch = new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch("3.0",batch.syncUuid(),batch.operationUuid(),
                List.of(batch.detections().get(0),uncertain));
        var response = detectionService(new DataSourceTransactionManager(source)).upload(teacher, request.scanPageUuid(), batch);
        assertEquals(2,response.idMappings().size());
        assertEquals("B",jdbc.queryForObject("SELECT detected_option FROM omr_detections WHERE detection_uuid=?",String.class,batch.detections().get(0).detectionUuid()));
        assertNull(jdbc.queryForObject("SELECT detected_option FROM omr_detections WHERE detection_uuid=?",String.class,uncertain.detectionUuid()));
        assertEquals(new java.math.BigDecimal("0.4322"),jdbc.queryForObject("SELECT confidence_score FROM omr_detections WHERE detection_uuid=?",java.math.BigDecimal.class,uncertain.detectionUuid()));
    }

    @Test void invalidSecondDetectionRollsBackInMariaDb() {
        var batch = detectionFixture(false);
        var d = batch.detections().get(0);
        var good = new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection("00000000-0000-4000-8000-"+UUID.randomUUID().toString().substring(24),d.regionUuid(),d.questionUuid(),"detected","A",d.confidence());
        var bad = new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection("ffffffff-ffff-4fff-8fff-"+UUID.randomUUID().toString().substring(24),uuid(),uuid(),"detected","A",d.confidence());
        var invalid = new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch("3.0",batch.syncUuid(),batch.operationUuid(),List.of(good,bad));
        assertEquals("DETECTION_REGION_MISMATCH",assertThrows(V3AuthException.class,()->detectionService(new DataSourceTransactionManager(source)).upload(teacher,request.scanPageUuid(),invalid)).getCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM omr_detections WHERE detection_uuid=?",Integer.class,good.detectionUuid()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM syncs WHERE sync_uuid=?",Integer.class,batch.syncUuid()));
        assertEquals(1,jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE result_uuid=?",Integer.class,request.resultUuid()));
    }

    @Test void concurrentDetectionRetriesAndChangedReuseHaveOneReceipt() throws Exception {
        var batch = detectionFixture(false);
        var aService=detectionService(new DataSourceTransactionManager(source));
        var bService=detectionService(new DataSourceTransactionManager(source));
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=executor.submit(()->{start.await();return aService.upload(teacher,request.scanPageUuid(),batch);});
            var b=executor.submit(()->{start.await();return bService.upload(teacher,request.scanPageUuid(),batch);});start.countDown();
            var first=a.get(25,TimeUnit.SECONDS);var second=b.get(25,TimeUnit.SECONDS);
            assertEquals(first.idMappings(),second.idMappings());assertNotEquals(first.disposition(),second.disposition());
            var changed=new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch("3.0",batch.syncUuid(),batch.operationUuid(),List.of(batch.detections().get(0)));
            assertEquals("DETECTION_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->aService.upload(teacher,request.scanPageUuid(),changed)).getCode());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_detection_uploads WHERE operation_uuid=?",Integer.class,batch.operationUuid()));
        } finally {executor.shutdownNow();}
    }

    @Test void detectionCommitFailureRollsBackThenRetrySucceeds() {
        var batch=detectionFixture(false);
        var manager=new DataSourceTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status) {throw new TransactionSystemException("Injected detection commit failure");}
        };
        manager.setRollbackOnCommitFailure(true);
        assertThrows(V3AuthException.class,()->detectionService(manager).upload(teacher,request.scanPageUuid(),batch));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_detection_uploads WHERE operation_uuid=?",Integer.class,batch.operationUuid()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM omr_detections WHERE detection_uuid=?",Integer.class,batch.detections().get(0).detectionUuid()));
        assertEquals("created",detectionService(new DataSourceTransactionManager(source)).upload(teacher,request.scanPageUuid(),batch).disposition());
    }

    @Test void lostDetectionCommitAcknowledgementReplaysInMariaDb() {
        var batch=detectionFixture(false);
        var manager=new DataSourceTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status) {super.doCommit(status);throw new TransactionSystemException("Injected lost detection acknowledgement");}
        };
        assertThrows(V3AuthException.class,()->detectionService(manager).upload(teacher,request.scanPageUuid(),batch));
        assertEquals("replayed",detectionService(new DataSourceTransactionManager(source)).upload(teacher,request.scanPageUuid(),batch).disposition());
        assertEquals(2,jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE result_uuid=?",Integer.class,request.resultUuid()));
    }

    private V3DetectionUploadService detectionService(DataSourceTransactionManager manager) {
        return new V3DetectionUploadService(new com.capstone.assessment.v3.mobile.repository.V3DetectionRepository(jdbc),
                new V3ScanUploadLedgerRepository(jdbc),validators.getValidator(),mapper,manager);
    }

    private V3TeacherVerificationService verificationService(DataSourceTransactionManager manager) {
        return new V3TeacherVerificationService(new V3VerificationRepository(jdbc),new V3DetectionRepository(jdbc),
                new V3ScanUploadLedgerRepository(jdbc),validators.getValidator(),mapper,manager);
    }
    private V3VerificationBatch verificationFixture() {
        var detections=detectionFixture(false);
        detectionService(new DataSourceTransactionManager(source)).upload(teacher,request.scanPageUuid(),detections);
        var instant=Instant.parse("2026-01-01T01:02:03.123456789Z");
        var page=new PageDecision(uuid(),request.scanPageUuid(),"accepted",null,"Review " + "x".repeat(3000),instant);
        var answers=detections.detections().stream().map(d->new Answer(uuid(),uuid(),d.questionUuid(),d.regionUuid(),request.scanPageUuid(),
                new ObjectiveEvaluation("objective",d.detectionUuid()),"Reviewed",instant)).toList();
        return new V3VerificationBatch("3.0",uuid(),uuid(),request.assignmentUuid(),List.of(new Item(request.resultUuid(),2L,List.of(page),answers)));
    }

    @Test void teacherVerificationPersistsAuditsAndScorerRequiredFieldsWithoutResultFinalization() {
        var batch=verificationFixture();var verifier=verificationService(new DataSourceTransactionManager(source));
        var first=verifier.verify(teacher,batch).items().get(0);
        assertEquals("created",first.disposition());assertEquals(3,first.revision());
        long result=jdbc.queryForObject("SELECT test_result_id FROM test_results WHERE result_uuid=?",Long.class,request.resultUuid());
        long test=jdbc.queryForObject("SELECT test_id FROM test_assignments WHERE assignment_uuid=?",Long.class,request.assignmentUuid());
        var rows=new com.capstone.assessment.v3.scoring.repository.V3ScoringRepository(jdbc).findScoringRowsForUpdate(test,result);
        assertEquals(2,rows.stream().filter(row->row.studentAnswerId()!=null && "finalized".equals(row.evaluationStatus())
                && row.verifiedByUserId().equals(teacher.userId()) && row.verifiedAt()!=null && row.finalizedAt()!=null).count());
        assertEquals("pending_verification",jdbc.queryForObject("SELECT result_status FROM test_results WHERE test_result_id=?",String.class,result));
        assertEquals(0,jdbc.queryForObject("SELECT total_score FROM test_results WHERE test_result_id=?",Integer.class,result));
        assertEquals(batch.items().get(0).pageDecisions().get(0).clientDecidedAt().toString(),jdbc.queryForObject(
                "SELECT client_decided_at FROM scan_verifications WHERE verification_uuid=?",String.class,batch.items().get(0).pageDecisions().get(0).verificationUuid()));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_objective_verifications WHERE operation_uuid=?",Integer.class,batch.operationUuid()));
        jdbc.update("UPDATE test_results SET result_status='finalized',mobile_revision=8 WHERE test_result_id=?",result);
        assertEquals(first.replay(),verifier.verify(teacher,batch).items().get(0));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update(
                "UPDATE mobile_verification_batches SET request_json='invalid' WHERE operation_uuid=?",batch.operationUuid()));
    }

    @Test void uncertainVerificationRollsBackPageDecisionInMariaDb() {
        var batch=verificationFixture();
        jdbc.update("UPDATE omr_detections SET detection_status='uncertain',detected_option=NULL WHERE detection_uuid=?",
                batch.items().get(0).answers().get(1).evaluation().detectionUuid());
        var response=verificationService(new DataSourceTransactionManager(source)).verify(teacher,batch).items().get(0);
        assertEquals("OBJECTIVE_RESCAN_REQUIRED",response.error().code());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM scan_verifications WHERE mobile_operation_uuid=?",Integer.class,batch.operationUuid()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_objective_verifications WHERE operation_uuid=?",Integer.class,batch.operationUuid()));
        assertEquals("captured",jdbc.queryForObject("SELECT page_status FROM scan_pages WHERE scan_page_uuid=?",String.class,request.scanPageUuid()));
    }

    @Test void recordsRescanReasonWithoutAcceptingAnyAnswer() {
        var batch=verificationFixture();var item=batch.items().get(0);var p=item.pageDecisions().get(0);
        var decision=new PageDecision(p.verificationUuid(),p.scanPageUuid(),"rescan_requested","UNCERTAIN_MARK",p.comment(),p.clientDecidedAt());
        var rescan=new V3VerificationBatch("3.0",batch.syncUuid(),batch.operationUuid(),batch.assignmentUuid(),
                List.of(new Item(item.resultUuid(),2L,List.of(decision),List.of())));
        assertEquals("success",verificationService(new DataSourceTransactionManager(source)).verify(teacher,rescan).syncStatus());
        assertEquals("rescan_requested",jdbc.queryForObject("SELECT scan_status FROM scan_sessions WHERE scan_uuid=?",String.class,request.scanUuid()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_objective_verifications WHERE operation_uuid=?",Integer.class,batch.operationUuid()));
    }

    @Test void concurrentTeacherVerificationProducesOneAuditSet() throws Exception {
        var batch=verificationFixture();var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {var a=executor.submit(()->{start.await();return verificationService(new DataSourceTransactionManager(source)).verify(teacher,batch);});
            var b=executor.submit(()->{start.await();return verificationService(new DataSourceTransactionManager(source)).verify(teacher,batch);});start.countDown();
            var first=a.get(25,TimeUnit.SECONDS).items().get(0);var second=b.get(25,TimeUnit.SECONDS).items().get(0);
            assertEquals(first.idMappings(),second.idMappings());assertNotEquals(first.disposition(),second.disposition());
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_objective_verifications WHERE operation_uuid=?",Integer.class,batch.operationUuid()));
        }finally{executor.shutdownNow();}
    }

    @Test void lostVerificationCommitAcknowledgementResolvesDurableItem() {
        var batch=verificationFixture();var manager=new DataSourceTransactionManager(source){int commits;
            @Override protected void doCommit(DefaultTransactionStatus status){super.doCommit(status);if(++commits==2)throw new TransactionSystemException("Injected lost verification commit acknowledgement");}};
        assertEquals("replayed",verificationService(manager).verify(teacher,batch).items().get(0).disposition());
        assertEquals("replayed",verificationService(new DataSourceTransactionManager(source)).verify(teacher,batch).items().get(0).disposition());
        assertEquals(3,jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE result_uuid=?",Integer.class,request.resultUuid()));
    }

    @Test void partialResultFailurePreservesSuccessAndRetryFinishesRemainingLearner() {
        var batch=verificationFixture();var second=secondLearnerCapture();service.ingest(teacher,second,upload());
        var next=new Item(second.resultUuid(),1L,List.of(new PageDecision(uuid(),second.scanPageUuid(),"accepted",null,null,Instant.now())),List.of());
        var combined=new V3VerificationBatch("3.0",batch.syncUuid(),batch.operationUuid(),batch.assignmentUuid(),List.of(batch.items().get(0),next));
        var manager=new DataSourceTransactionManager(source){int commits;
            @Override protected void doCommit(DefaultTransactionStatus status){if(++commits==3)throw new TransactionSystemException("Injected second learner transaction failure");super.doCommit(status);}};
        manager.setRollbackOnCommitFailure(true);
        var first=verificationService(manager).verify(teacher,combined);assertEquals("partial_success",first.syncStatus());
        assertEquals(1,first.items().stream().filter(i->"success".equals(i.status())).count());
        var partialReadback=reader().sync(teacher,combined.syncUuid());assertEquals("partial_success",partialReadback.syncStatus());
        assertEquals(1,partialReadback.items().stream().filter(i->"success".equals(i.status())).count());
        var retry=verificationService(new DataSourceTransactionManager(source)).verify(teacher,combined);
        assertEquals("success",retry.syncStatus());assertEquals(1,retry.items().stream().filter(i->"replayed".equals(i.disposition())).count());
        assertEquals("success",reader().sync(teacher,combined.syncUuid()).syncStatus());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM scan_verifications WHERE mobile_operation_uuid=?",Integer.class,batch.operationUuid()));
    }

    private V3ScanPageUploadMetadata secondLearnerCapture() {
        long address=jdbc.queryForObject("SELECT address_id FROM users WHERE user_id=?",Long.class,teacher.userId());
        long cohort=jdbc.queryForObject("SELECT class_id FROM class_lists WHERE class_list_id=?",Long.class,request.classListId());
        long year=jdbc.queryForObject("SELECT academic_year_id FROM class_lists WHERE class_list_id=?",Long.class,request.classListId());
        String lrn=String.format("%012d",Math.abs(UUID.randomUUID().getLeastSignificantBits()%1000000000000L));
        long student=insert("student_id","INSERT INTO students(school_id,address_id,gender_id,student_lrn,first_name,last_name) VALUES(?,?,1,?,'Synthetic','Second')",teacher.schoolId(),address,lrn);
        long member=insert("class_list_id","INSERT INTO class_lists(membership_uuid,class_id,student_id,academic_year_id) VALUES(?,?,?,?)",uuid(),cohort,student,year);
        return new V3ScanPageUploadMetadata("3.0",uuid(),uuid(),uuid(),uuid(),request.answerSheetUuid(),request.pageUuid(),request.assignmentUuid(),
                member,1,1,request.scannerVersion(),request.qrPayloadHash(),request.imageHash(),request.capturedAt());
    }

    private com.capstone.assessment.v3.mobile.dto.V3DetectionBatch detectionFixture(boolean syntheticTf) {
        return detectionFixture(syntheticTf,2);
    }
    private com.capstone.assessment.v3.mobile.dto.V3DetectionBatch detectionFixture(boolean syntheticTf,int count) {
        // The normal upload path still only accepts the approved MC template. TF tests below
        // arrange synthetic stored relationships; they do not validate a TF printed sheet or scanner.
        long pageId=service.ingest(teacher,request,upload()).backendScanPageId();
        var rows=jdbc.queryForList("""
                SELECT g.answer_sheet_region_id,g.region_uuid,q.question_id,q.question_uuid
                FROM scan_pages p JOIN answer_sheet_regions g ON g.answer_sheet_page_id=p.answer_sheet_page_id
                JOIN questions q ON q.question_id=g.question_id WHERE p.scan_page_id=? ORDER BY g.global_item_number LIMIT ?
                """,pageId,count);
        List<com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection> detections=new java.util.ArrayList<>();
        for(var row:rows) {
            long question=((Number)row.get("question_id")).longValue();
            if(syntheticTf) {
                long type=jdbc.queryForObject("SELECT question_type_id FROM question_types WHERE question_type_code='true_false'",Long.class);
                jdbc.update("UPDATE questions SET question_type_id=? WHERE question_id=?",type,question);
                jdbc.update("UPDATE answer_sheet_regions SET question_type_id=?,geometry_snapshot=? WHERE answer_sheet_region_id=?",
                        type,"{\"option_keys\":[\"A\",\"B\"]}",row.get("answer_sheet_region_id"));
            }
            int order=0;
            for(String key:(syntheticTf?List.of("A","B"):List.of("A","B","C","D")))
                jdbc.update("INSERT INTO question_options(question_id,option_key,option_text,option_order) VALUES(?,?,?,?)",question,key,key,++order);
            detections.add(new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection(uuid(),row.get("region_uuid").toString(),
                    row.get("question_uuid").toString(),"detected",syntheticTf?"B":"A",new java.math.BigDecimal("0.98")));
        }
        return new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch("3.0",uuid(),uuid(),List.copyOf(detections));
    }

    private long verifiedResult(boolean syntheticTf) {
        return verifiedResult(syntheticTf,true);
    }
    private long verifiedResult(boolean syntheticTf,boolean complete) {
        var batch=detectionFixture(syntheticTf,10);
        var detections=new java.util.ArrayList<>(batch.detections());
        var first=detections.get(0);
        detections.set(0,new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection(first.detectionUuid(),
                first.regionUuid(),first.questionUuid(),"blank",null,first.confidence()));
        batch=new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch("3.0",batch.syncUuid(),batch.operationUuid(),List.copyOf(detections));
        detectionService(new DataSourceTransactionManager(source)).upload(teacher,request.scanPageUuid(),batch);
        var instant=Instant.parse("2026-01-01T01:02:03Z");
        var answers=batch.detections().stream().map(d->new Answer(uuid(),uuid(),d.questionUuid(),d.regionUuid(),request.scanPageUuid(),
                new ObjectiveEvaluation("objective",d.detectionUuid()),"Reviewed",instant)).toList();
        var verification=new V3VerificationBatch("3.0",uuid(),uuid(),request.assignmentUuid(),List.of(new Item(request.resultUuid(),2L,
                List.of(new PageDecision(uuid(),request.scanPageUuid(),"accepted",null,null,instant)),complete?answers:answers.subList(0,9))));
        assertEquals("success",verificationService(new DataSourceTransactionManager(source)).verify(teacher,verification).items().get(0).status());
        long result=resultId();
        jdbc.update("""
                INSERT INTO answer_keys(question_id,answer_key_type,correct_question_option_id,scoring_method)
                SELECT q.question_id,'option',o.question_option_id,'exact' FROM questions q
                JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id
                JOIN question_options o ON o.question_id=q.question_id AND o.option_key=? WHERE d.assignment_uuid=?
                """,syntheticTf?"B":"A",request.assignmentUuid());
        return result;
    }
    private long resultId() { return jdbc.queryForObject("SELECT test_result_id FROM test_results WHERE result_uuid=?",Long.class,request.resultUuid()); }
    private com.capstone.assessment.v3.scoring.service.V3ScoringService scorer(boolean enabled) {
        return scorer(enabled,evaluationReader());
    }
    private com.capstone.assessment.v3.scoring.service.V3ScoringService scorer(boolean enabled,V3EvaluationReferenceService references) {
        return new com.capstone.assessment.v3.scoring.service.V3ScoringService(
                new com.capstone.assessment.v3.scoring.repository.V3ScoringRepository(jdbc),
                new com.capstone.assessment.v3.auth.service.V3AuditService(new com.capstone.assessment.v3.auth.repository.V3AuthRepository(jdbc),mapper),mapper,
                new V3MobileFinalizationService(new com.capstone.assessment.v3.mobile.repository.V3MobileFinalizationRepository(jdbc),
                        new V3ScanUploadLedgerRepository(jdbc),storage,mapper,enabled,writtenFinalizer(references)));
    }
    private V3WrittenFinalizationService writtenFinalizer(V3EvaluationReferenceService references) {
        return new V3WrittenFinalizationService(new com.capstone.assessment.v3.mobile.repository.V3WrittenFinalizationRepository(jdbc),
                new com.capstone.assessment.v3.scoring.repository.V3ScoringRepository(jdbc),references,attachments(),new V3DetectionRepository(jdbc),mapper,validators.getValidator());
    }
    private com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse finalizeScore(long result) {
        return new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(source))
                .execute(s->scorer(true).finalizeResult(teacher,result,null));
    }
    private void assertUnfinalized(long result) {
        assertEquals("pending_verification",jdbc.queryForObject("SELECT result_status FROM test_results WHERE test_result_id=?",String.class,result));
        assertEquals(3L,jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=?",Long.class,result));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_finalizations WHERE test_result_id=?",Integer.class,result));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE test_result_id=? AND is_correct IS NOT NULL",Integer.class,result));
    }
    @Test void mobileFinalizationScoresTenMcIncludingBlankAndFreezesReplayAcrossRuleAndKeyChanges() {
        long result=verifiedResult(false);
        var scored=finalizeScore(result);
        assertEquals(0,new java.math.BigDecimal("18.00").compareTo(scored.totalScore()));
        assertEquals(0,new java.math.BigDecimal("20.00").compareTo(scored.maxScore()));
        assertEquals(10,scored.itemsEvaluated());assertEquals(1,scored.scoreVersion());assertTrue(scored.scoreChanged());
        assertEquals(4L,jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=?",Long.class,result));
        assertEquals(9,jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE test_result_id=? AND is_correct=TRUE",Integer.class,result));
        jdbc.update("""
                UPDATE answer_keys k JOIN student_answers a ON a.question_id=k.question_id
                JOIN question_options o ON o.question_id=a.question_id AND o.option_key='B'
                SET k.correct_question_option_id=o.question_option_id WHERE a.test_result_id=?
                """,result);
        jdbc.update("""
                INSERT INTO performance_rule_sets(rule_set_uuid,school_id,rule_set_name,rule_version,metric_scope,rule_definition,rule_status)
                VALUES(?,?,'Changed rule','1','student_score','{}','active')
                """,uuid(),teacher.schoolId());
        var retry=finalizeScore(result);
        assertFalse(retry.scoreChanged());assertEquals(scored.totalScore(),retry.totalScore());assertEquals(scored.scoredAt(),retry.scoredAt());
        assertEquals(scored.parts(),retry.parts());assertEquals(scored.scoreVersion(),retry.scoreVersion());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='result.score.finalize' AND entity_id=?",Integer.class,Long.toString(result)));
    }
    @Test void mobileFinalizationScoresSyntheticTrueFalseUsingTheBackendKey() {
        long result=verifiedResult(true);var scored=finalizeScore(result);
        assertEquals(0,new java.math.BigDecimal("18.00").compareTo(scored.totalScore()));assertEquals(10,scored.itemsEvaluated());
    }
    @Test void mobileFinalizationDefaultGateStopsScanResultsBeforeDraftSchemaReads() {
        long result=verifiedResult(false);
        assertEquals("MOBILE_FINALIZATION_UNAVAILABLE",assertThrows(V3AuthException.class,()->
                new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(source))
                        .execute(s->scorer(false).finalizeResult(teacher,result,null))).getCode());
        assertUnfinalized(result);
    }
    @Test void mobileFinalizationRejectsMissingAnswers() {
        var verification=verificationFixture();
        verificationService(new DataSourceTransactionManager(source)).verify(teacher,verification);
        long result=resultId();
        assertEquals("OBJECTIVE_VERIFICATION_INCOMPLETE",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());
        assertUnfinalized(result);
    }
    @Test void mobileFinalizationRejectsUnacceptedPageAndStaleSheet() {
        long result=verifiedResult(false);
        jdbc.update("UPDATE scan_pages SET page_status='rescan_requested' WHERE scan_page_uuid=?",request.scanPageUuid());
        assertEquals("SCAN_VERIFICATION_INCOMPLETE",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());
        jdbc.update("UPDATE scan_pages SET page_status='accepted' WHERE scan_page_uuid=?",request.scanPageUuid());
        jdbc.update("UPDATE answer_sheet_versions SET test_version_number=2 WHERE answer_sheet_uuid=?",request.answerSheetUuid());
        assertEquals("SCAN_VERIFICATION_INCOMPLETE",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());assertUnfinalized(result);
    }
    @Test void mobileFinalizationRejectsUnresolvedOrChangedAnswerDespiteTeacherFields() {
        long result=verifiedResult(false);
        jdbc.update("""
                UPDATE student_answers a JOIN question_options o ON o.question_id=a.question_id AND o.option_key='B'
                SET a.selected_question_option_id=o.question_option_id WHERE a.test_result_id=? AND a.answer_status='answered'
                """,result);
        assertEquals("OBJECTIVE_VERIFICATION_INCOMPLETE",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());assertUnfinalized(result);
    }
    @Test void mobileFinalizationRequiresOriginalBytesAndAllowsExactUploadRecovery() throws Exception {
        long result=verifiedResult(false);
        String key=jdbc.queryForObject("SELECT storage_key FROM mobile_scan_uploads WHERE scan_page_uuid=?",String.class,request.scanPageUuid());
        java.nio.file.Files.delete(directory.resolve(key));
        assertEquals("SCAN_EVIDENCE_NOT_FOUND",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());assertUnfinalized(result);
        service.ingest(teacher,request,upload());
        assertEquals("finalized",finalizeScore(result).resultStatus());
    }
    @Test void mobileFinalizationRejectsChangedOriginalBytes() throws Exception {
        long result=verifiedResult(false);
        String key=jdbc.queryForObject("SELECT storage_key FROM mobile_scan_uploads WHERE scan_page_uuid=?",String.class,request.scanPageUuid());
        java.nio.file.Files.write(directory.resolve(key),new byte[]{1,2,3});
        assertEquals("SCAN_EVIDENCE_INTEGRITY_FAILED",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());assertUnfinalized(result);
    }
    @Test void mobileFinalizationRollbackLeavesScoresAuditAndRevisionUnchanged() {
        long result=verifiedResult(false);
        var manager=new DataSourceTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status) { throw new TransactionSystemException("Injected commit failure"); }
        };
        manager.setRollbackOnCommitFailure(true);
        assertThrows(TransactionSystemException.class,()->new org.springframework.transaction.support.TransactionTemplate(manager)
                .execute(s->scorer(true).finalizeResult(teacher,result,null)));
        assertUnfinalized(result);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='result.score.finalize' AND entity_id=?",Integer.class,Long.toString(result)));
        assertTrue(finalizeScore(result).scoreChanged());
    }
    @Test void mobileFinalizationLostCommitAcknowledgementReplaysOneOfficialScore() {
        long result=verifiedResult(false);
        var manager=new DataSourceTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status) {
                super.doCommit(status);throw new TransactionSystemException("Injected lost acknowledgement");
            }
        };
        assertThrows(TransactionSystemException.class,()->new org.springframework.transaction.support.TransactionTemplate(manager)
                .execute(s->scorer(true).finalizeResult(teacher,result,null)));
        assertFalse(finalizeScore(result).scoreChanged());
        assertEquals(4L,jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=?",Long.class,result));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_finalizations WHERE test_result_id=?",Integer.class,result));
    }
    @Test void mobileFinalizationConcurrentRequestsCommitOnce() throws Exception {
        long result=verifiedResult(false);var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=executor.submit(()->{start.await();return finalizeScore(result);});
            var b=executor.submit(()->{start.await();return finalizeScore(result);});start.countDown();
            var first=a.get(25,TimeUnit.SECONDS);var second=b.get(25,TimeUnit.SECONDS);
            assertNotEquals(first.scoreChanged(),second.scoreChanged());assertEquals(first.totalScore(),second.totalScore());
            assertEquals(first.scoredAt(),second.scoredAt());assertEquals(4L,jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=?",Long.class,result));
        } finally { executor.shutdownNow(); }
    }
    @Test void mobileFinalizationRechecksLiveOwnerAndRejectsUnauditedReopen() {
        long result=verifiedResult(false);
        jdbc.update("UPDATE users SET status_id=(SELECT status_id FROM statuses WHERE status_name='inactive') WHERE user_id=?",teacher.userId());
        assertEquals("RESULT_ACCESS_DENIED",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());
        jdbc.update("UPDATE users SET status_id=(SELECT status_id FROM statuses WHERE status_name='active') WHERE user_id=?",teacher.userId());
        finalizeScore(result);
        jdbc.update("UPDATE test_results SET result_status='pending_verification' WHERE test_result_id=?",result);
        assertEquals("FINALIZATION_STATE_CONFLICT",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());
    }

    @Test void mobileFinalizationRejectsMissingTeacherAuditAndUnresolvedDetection() {
        long result=verifiedResult(false);
        jdbc.update("UPDATE omr_detections d JOIN scan_pages p ON p.scan_page_id=d.scan_page_id SET d.detection_status='uncertain',d.detected_option=NULL WHERE p.scan_page_uuid=?",request.scanPageUuid());
        assertEquals("OBJECTIVE_VERIFICATION_INCOMPLETE",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());
        jdbc.update("DELETE FROM scan_verifications WHERE scan_page_id=(SELECT scan_page_id FROM scan_pages WHERE scan_page_uuid=?)",request.scanPageUuid());
        assertEquals("SCAN_VERIFICATION_INCOMPLETE",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());assertUnfinalized(result);
    }
    @Test void mobileFinalizationRejectsMissingOrForeignQuestionAnswerKeyAtomically() {
        long result=verifiedResult(false);
        long key=jdbc.queryForObject("SELECT MAX(k.answer_key_id) FROM answer_keys k JOIN student_answers a ON a.question_id=k.question_id WHERE a.test_result_id=?",Long.class,result);
        jdbc.update("UPDATE answer_keys SET correct_question_option_id=(SELECT MIN(o.question_option_id) FROM question_options o JOIN student_answers a ON a.question_id=o.question_id WHERE a.test_result_id=?) WHERE answer_key_id=?",result,key);
        assertEquals("ANSWER_KEY_INVALID",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());
        jdbc.update("DELETE FROM answer_keys WHERE answer_key_id=?",key);
        assertEquals("ANSWER_KEY_MISSING",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());assertUnfinalized(result);
    }
    @Test void mobileFinalizationRejectsRevisionOverflowAndUnreceiptedOfficialResult() {
        long result=verifiedResult(false);
        jdbc.update("UPDATE test_results SET mobile_revision=9007199254740991 WHERE test_result_id=?",result);
        assertEquals("RESULT_REVISION_EXHAUSTED",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());
        jdbc.update("UPDATE test_results SET mobile_revision=3,result_status='finalized' WHERE test_result_id=?",result);
        assertEquals("AUDITED_REOPEN_REQUIRED",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());
    }
    @Test void mobileFinalizationRejectsChangedReceiptMetadataAndUnauthorizedTeacher() {
        long result=verifiedResult(false);finalizeScore(result);
        var other=new V3AuthenticatedUser(teacher.userId(),"OTHER-SCHOOL","","teacher","active","");
        assertEquals("RESULT_ACCESS_DENIED",assertThrows(V3AuthException.class,()->
                new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(source))
                    .execute(s->scorer(true).finalizeResult(other,result,null))).getCode());
        jdbc.update("UPDATE test_results SET items_evaluated=9 WHERE test_result_id=?",result);
        assertEquals("FINALIZATION_STATE_CONFLICT",assertThrows(V3AuthException.class,()->finalizeScore(result)).getCode());
    }

    @Test void lastTeacherAnswerAndFinalizationSerializeWithoutLosingTheReview() throws Exception {
        long result=verifiedResult(false,false);
        var missing=jdbc.queryForMap("""
                SELECT q.question_uuid,g.region_uuid,d.detection_uuid FROM omr_detections d
                JOIN questions q ON q.question_id=d.question_id JOIN answer_sheet_regions g ON g.answer_sheet_region_id=d.answer_sheet_region_id
                JOIN scan_pages p ON p.scan_page_id=d.scan_page_id
                WHERE p.scan_page_uuid=? AND NOT EXISTS(SELECT 1 FROM student_answers a WHERE a.test_result_id=? AND a.question_id=q.question_id)
                """,request.scanPageUuid(),result);
        var answer=new Answer(uuid(),uuid(),missing.get("question_uuid").toString(),missing.get("region_uuid").toString(),request.scanPageUuid(),
                new ObjectiveEvaluation("objective",missing.get("detection_uuid").toString()),"Last review",Instant.parse("2026-01-01T01:02:03Z"));
        var batch=new V3VerificationBatch("3.0",uuid(),uuid(),request.assignmentUuid(),List.of(new Item(request.resultUuid(),3L,List.of(),List.of(answer))));
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var scoring=executor.submit(()->{start.await();try { return finalizeScore(result).resultStatus(); }
                catch(V3AuthException e) { return e.getCode(); }});
            var review=executor.submit(()->{start.await();return verificationService(new DataSourceTransactionManager(source)).verify(teacher,batch);});
            start.countDown();
            assertTrue(List.of("finalized","OBJECTIVE_VERIFICATION_INCOMPLETE").contains(scoring.get(25,TimeUnit.SECONDS)));
            assertEquals("success",review.get(25,TimeUnit.SECONDS).items().get(0).status());
            assertEquals(10,finalizeScore(result).itemsEvaluated());
            assertEquals(5L,jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=?",Long.class,result));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_finalizations WHERE test_result_id=?",Integer.class,result));
        } finally { executor.shutdownNow(); }
    }

    private V3MobileReadbackService reader() {
        return new V3MobileReadbackService(new com.capstone.assessment.v3.mobile.repository.V3MobileReadbackRepository(jdbc),
                mapper,new DataSourceTransactionManager(source),true);
    }
    @Test void readbackShowsDraftIdentityWithoutInventedScoresOrAnalytics() {
        var upload=service.ingest(teacher,request,upload());
        var read=reader().result(teacher,request.resultUuid());
        assertEquals(resultId(),read.testResultId());assertEquals(1,read.revision());assertEquals("draft",read.resultStatus());
        assertNull(read.officialScore());assertEquals("pending",read.analytics().status());assertNull(read.analytics().scoreVersion());
        assertTrue(read.pendingReasons().containsAll(List.of("PAGE_VERIFICATION_PENDING","ANSWER_VERIFICATION_PENDING","FINALIZATION_PENDING")));
        assertEquals(request.scanPageUuid(),read.pages().get(0).scanPageUuid());
        assertTrue(read.idMappings().stream().anyMatch(m->m.entityType().equals("scan_page") && m.centralId()==upload.backendScanPageId()));
        var analytics=reader().analytics(teacher,request.resultUuid());assertNull(analytics.metrics());assertNull(analytics.generatedAt());
        assertEquals(4,analytics.unavailableModules().size());
        assertEquals("success",reader().sync(teacher,request.syncUuid()).syncStatus());
        assertEquals("draft",jdbc.queryForObject("SELECT result_status FROM test_results WHERE result_uuid=?",String.class,request.resultUuid()));
    }
    @Test void readbackReturnsOfficialSnapshotAndBasicAnalyticsAfterFinalization() throws Exception {
        long result=verifiedResult(false);var finalScore=finalizeScore(result);
        var read=reader().result(teacher,request.resultUuid());var analytics=reader().analytics(teacher,request.resultUuid());
        assertEquals(4,read.revision());assertEquals("ready",read.analytics().status());assertTrue(read.pendingReasons().isEmpty());
        assertFalse(read.officialScore().scoreChanged());assertEquals(finalScore.totalScore(),read.officialScore().totalScore());
        assertEquals(finalScore.scoredAt(),analytics.generatedAt());assertEquals(finalScore.percentage(),analytics.metrics().percentage());
        assertEquals(1,analytics.scoreVersion());assertNull(analytics.reasonCode());assertNull(analytics.metrics().mastery());
        assertEquals(10,read.idMappings().stream().filter(m->m.entityType().equals("student_answer")).count());
        assertEquals(10,read.idMappings().stream().filter(m->m.entityType().equals("omr_detection")).count());
        assertEquals(10,read.idMappings().stream().filter(m->m.entityType().equals("answer_sheet_region")).count());
        assertTrue(read.rubricScoreMappings().isEmpty());
        String serialized=mapper.writeValueAsString(read);
        for(String forbidden:List.of("storageKey","storage_key","request_json","password","correctQuestionOptionId","content_hash","rawMark"))
            assertFalse(serialized.contains(forbidden),forbidden);
        // Persist only synthetic contract-check samples, never evidence bytes or school records.
        var folder=Path.of("target/mobile-readback-contract-samples");java.nio.file.Files.createDirectories(folder);
        var wire=mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        wire.writeValue(folder.resolve("result.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(read));
        wire.writeValue(folder.resolve("analytics.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(analytics));
        wire.writeValue(folder.resolve("sync.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(reader().sync(teacher,request.syncUuid())));
    }
    @Test void readbackSuppressesReopenedSupersededAndMismatchedOfficialScores() {
        long result=verifiedResult(false);finalizeScore(result);
        for(String state:List.of("pending_verification","superseded")) {
            jdbc.update("UPDATE test_results SET result_status=? WHERE test_result_id=?",state,result);
            var read=reader().result(teacher,request.resultUuid());assertNull(read.officialScore());assertEquals("stale",read.analytics().status());
            assertNull(reader().analytics(teacher,request.resultUuid()).metrics());
        }
        jdbc.update("UPDATE test_results SET result_status='finalized',mobile_revision=5 WHERE test_result_id=?",result);
        assertEquals("SCORE_SNAPSHOT_MISMATCH",reader().analytics(teacher,request.resultUuid()).reasonCode());
        jdbc.update("UPDATE test_results SET mobile_revision=4,total_score=1 WHERE test_result_id=?",result);
        assertNull(reader().result(teacher,request.resultUuid()).officialScore());
    }
    @Test void readbackDoesNotRecomputeFromChangedRulesAndNeverInventsMissingReceipts() {
        long result=verifiedResult(false);var score=finalizeScore(result);
        jdbc.update("UPDATE answer_keys k JOIN student_answers a ON a.question_id=k.question_id JOIN question_options o ON o.question_id=k.question_id AND o.option_key='B' SET k.correct_question_option_id=o.question_option_id WHERE a.test_result_id=?",result);
        assertEquals(score.totalScore(),reader().analytics(teacher,request.resultUuid()).metrics().totalScore());
        jdbc.update("DELETE FROM mobile_result_finalizations WHERE test_result_id=?",result);
        assertEquals("unavailable",reader().analytics(teacher,request.resultUuid()).status());assertNull(reader().result(teacher,request.resultUuid()).officialScore());
    }
    @Test void readbackRejectsForeignUnknownAndInactiveOwners() {
        verifiedResult(false);
        var foreign=new V3AuthenticatedUser(teacher.userId(),"OTHER", "","teacher","active","");
        assertEquals("RESULT_ACCESS_DENIED",assertThrows(V3AuthException.class,()->reader().result(foreign,request.resultUuid())).getCode());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->reader().result(teacher,uuid())).getCode());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->reader().sync(teacher,uuid())).getCode());
        jdbc.update("UPDATE users SET status_id=5 WHERE user_id=?",teacher.userId());
        assertEquals("RESULT_ACCESS_DENIED",assertThrows(V3AuthException.class,()->reader().analytics(teacher,request.resultUuid())).getCode());
    }
    @Test void readbackSyncExposesPendingIntentBeforeAnyResultExistsAndThenRecovery() {
        var manager=new DataSourceTransactionManager(source){int commits;
            @Override protected void doCommit(DefaultTransactionStatus status) {
                if(++commits==3)throw new TransactionSystemException("Injected completion failure");super.doCommit(status);
            }};
        manager.setRollbackOnCommitFailure(true);
        assertThrows(V3AuthException.class,()->service(manager).ingest(teacher,request,upload()));
        var read=reader().sync(teacher,request.syncUuid());
        assertEquals(request.resultUuid(),read.items().get(0).resultUuid());assertEquals("failed",read.items().get(0).status());
        assertTrue(read.items().get(0).error().retryable());assertEquals(request.scanPageUuid(),read.items().get(0).pageOutcomes().get(0).scanPageUuid());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->reader().result(teacher,request.resultUuid())).getCode());
        jdbc.update("UPDATE mobile_scan_uploads SET last_error_code=NULL WHERE scan_page_uuid=?",request.scanPageUuid());
        assertEquals("in_progress",reader().sync(teacher,request.syncUuid()).syncStatus());
        service.recover(request.scanPageUuid());assertEquals("success",reader().sync(teacher,request.syncUuid()).syncStatus());
    }
    @Test void readbackReportsDetectionAndVerificationStageSuccessWithoutFinalization() {
        var batch=verificationFixture();var detectionSync=jdbc.queryForObject("SELECT s.sync_uuid FROM mobile_detection_uploads d JOIN syncs s ON s.sync_id=d.sync_id JOIN scan_pages p ON p.scan_page_id=d.scan_page_id WHERE p.scan_page_uuid=?",String.class,request.scanPageUuid());
        var read=reader().sync(teacher,detectionSync);assertEquals("success",read.syncStatus());assertEquals(1,read.items().get(0).pageOutcomes().size());
        verificationService(new DataSourceTransactionManager(source)).verify(teacher,batch);
        var verified=reader().sync(teacher,batch.syncUuid());assertEquals("success",verified.syncStatus());assertEquals(1,verified.items().get(0).pageOutcomes().size());
        assertNull(reader().result(teacher,request.resultUuid()).officialScore());assertEquals(3,reader().result(teacher,request.resultUuid()).revision());
    }
    @Test void readbackKeepsPendingVerificationRegistrationVisible() {
        var batch=verificationFixture();var manager=new DataSourceTransactionManager(source){
            @Override protected void doCommit(DefaultTransactionStatus status) {super.doCommit(status);throw new TransactionSystemException("Lost registration acknowledgement");}
        };
        assertThrows(V3AuthException.class,()->verificationService(manager).verify(teacher,batch));
        var read=reader().sync(teacher,batch.syncUuid());assertEquals("in_progress",read.syncStatus());assertEquals("pending",read.items().get(0).status());
        assertNull(read.items().get(0).error());assertEquals("pending",read.items().get(0).pageOutcomes().get(0).status());
    }
    @Test void readbackVerificationFailuresRemainStageFailures() {
        var batch=verificationFixture();var item=batch.items().get(0);
        var stale=new V3VerificationBatch("3.0",batch.syncUuid(),batch.operationUuid(),batch.assignmentUuid(),
                List.of(new Item(item.resultUuid(),1L,item.pageDecisions(),item.answers())));
        verificationService(new DataSourceTransactionManager(source)).verify(teacher,stale);
        var read=reader().sync(teacher,batch.syncUuid());assertEquals("failed",read.syncStatus());assertEquals("REVISION_CONFLICT",read.items().get(0).error().code());
        assertFalse(read.items().get(0).error().retryable());assertEquals("failed",read.items().get(0).pageOutcomes().get(0).status());
    }
    @Test void readbackUsesOneSnapshotWhenFinalizationCommitsDuringRead() throws Exception {
        long result=verifiedResult(false);var executor=Executors.newSingleThreadExecutor();
        try {
            var repository=new com.capstone.assessment.v3.mobile.repository.V3MobileReadbackRepository(jdbc) {
                @Override public java.util.Optional<ResultRow> result(V3AuthenticatedUser u,String uuid) {
                    var before=super.result(u,uuid);
                    try {executor.submit(()->finalizeScore(result)).get(25,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}
                    return before;
                }
            };
            var snapshotReader=new V3MobileReadbackService(repository,mapper,new DataSourceTransactionManager(source),true);
            var snapshot=snapshotReader.result(teacher,request.resultUuid());assertEquals(3,snapshot.revision());assertNull(snapshot.officialScore());
            var fresh=reader().result(teacher,request.resultUuid());assertEquals(4,fresh.revision());assertEquals("ready",fresh.analytics().status());
        } finally {executor.shutdownNow();}
    }
    @Test void readbackMissingOriginalIsAnExplicitStateErrorRatherThanDroppedPage() {
        verifiedResult(false);
        jdbc.update("DELETE FROM answer_attachments WHERE scan_page_id=(SELECT scan_page_id FROM scan_pages WHERE scan_page_uuid=?)",request.scanPageUuid());
        assertEquals("READBACK_STATE_INCONSISTENT",assertThrows(V3AuthException.class,()->reader().result(teacher,request.resultUuid())).getCode());
    }

    @Test void readbackHidesResultsAndSyncsAfterAssignmentReassignment() {
        verifiedResult(false);
        long other=insert("user_id","""
                INSERT INTO users(school_id,address_id,gender_id,role_id,status_id,first_name,last_name,email,contact_number,password_hash)
                SELECT school_id,address_id,gender_id,role_id,status_id,'Other','Teacher',?,?,'TEST_ONLY'
                FROM users WHERE user_id=?
                """,uuid()+"@example.invalid",uuid().substring(0,12),teacher.userId());
        jdbc.update("UPDATE class_assignments a JOIN test_assignments d ON d.class_assignment_id=a.class_assignment_id SET a.user_id=? WHERE d.assignment_uuid=?",other,request.assignmentUuid());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->reader().result(teacher,request.resultUuid())).getCode());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->reader().analytics(teacher,request.resultUuid())).getCode());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->reader().sync(teacher,request.syncUuid())).getCode());
    }
    @Test void readbackRejectsOversizedMappingSetsInsteadOfSilentlyTruncating() {
        verifiedResult(false);
        for(int i=0;i<201;i++) jdbc.update("""
                INSERT INTO scan_verifications(verification_uuid,scan_session_id,scan_page_id,verified_by_user_id,verification_action)
                SELECT ?,p.scan_session_id,p.scan_page_id,?,'accepted' FROM scan_pages p WHERE p.scan_page_uuid=?
                """,uuid(),teacher.userId(),request.scanPageUuid());
        assertEquals("READBACK_LIMIT_EXCEEDED",assertThrows(V3AuthException.class,()->reader().result(teacher,request.resultUuid())).getCode());
    }
    @Test void readbackDoesNotTreatOneSubmittedPageAsCompleteForAnExpandedManifest() {
        service.ingest(teacher,request,upload());
        jdbc.update("UPDATE answer_sheet_versions SET total_pages=2 WHERE answer_sheet_uuid=?",request.answerSheetUuid());
        var sync=reader().sync(teacher,request.syncUuid());
        assertEquals("in_progress",sync.syncStatus());assertEquals("pending",sync.items().get(0).status());
        assertEquals("MANIFEST_PAGES_PENDING",sync.items().get(0).error().code());assertEquals("success",sync.items().get(0).pageOutcomes().get(0).status());
    }

    @Test void readbackRejectsMalformedSavedOfficialScoreRatherThanReturningInvalidDto() {
        long result=verifiedResult(false);finalizeScore(result);
        jdbc.update("UPDATE mobile_result_finalizations SET response_json='{}' WHERE test_result_id=?",result);
        assertEquals("READBACK_STATE_INCONSISTENT",assertThrows(V3AuthException.class,()->reader().result(teacher,request.resultUuid())).getCode());
        assertEquals("READBACK_STATE_INCONSISTENT",assertThrows(V3AuthException.class,()->reader().analytics(teacher,request.resultUuid())).getCode());
    }

    private V3EvaluationReferenceService evaluationReader() {
        return new V3EvaluationReferenceService(new com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository(jdbc),new DataSourceTransactionManager(source),true);
    }
    private WrittenRefs writtenReferences() {
        var questions=jdbc.queryForList("SELECT q.question_id FROM questions q JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id WHERE d.assignment_uuid=? ORDER BY q.question_uuid LIMIT 3",Long.class,request.assignmentUuid());
        long rubric=insert("rubric_id","INSERT INTO rubrics(rubric_uuid,school_id,created_by_user_id,rubric_name,total_points,rubric_status) VALUES(?,?,?,'Synthetic writing rubric',10,'active')",uuid(),teacher.schoolId(),teacher.userId());
        jdbc.update("INSERT INTO rubric_criteria(rubric_id,criterion_order,criterion_name,criterion_description,maximum_points,is_required) VALUES(?,1,'Content','Private descriptor',6,TRUE),(?,2,'Organization','Private descriptor',4,FALSE)",rubric,rubric);
        jdbc.update("UPDATE questions SET question_type_id=(SELECT question_type_id FROM question_types WHERE question_type_code='essay'),rubric_id=?,maximum_points=10 WHERE question_id=?",rubric,questions.get(0));
        jdbc.update("UPDATE questions SET question_type_id=(SELECT question_type_id FROM question_types WHERE question_type_code='identification') WHERE question_id=?",questions.get(1));
        jdbc.update("UPDATE questions SET question_type_id=(SELECT question_type_id FROM question_types WHERE question_type_code='enumeration'),expected_response_count=3 WHERE question_id=?",questions.get(2));
        jdbc.update("INSERT INTO answer_keys(question_id,answer_key_type,scoring_method,rubric_id,answer_explanation) VALUES(?,'rubric','rubric',?,'SECRET MODEL SOLUTION'),(?,'manual','manual',NULL,'SECRET ANSWER'),(?,'accepted_text','normalized',NULL,'SECRET ACCEPTED ANSWER')",
                questions.get(0),rubric,questions.get(1),questions.get(2));
        return new WrittenRefs(rubric,questions.get(0),questions.get(1),questions.get(2));
    }
    private record WrittenRefs(long rubric,long essay,long manual,long enumeration) { }
    @Test void evaluationReferenceReturnsOwnedScoringBoundsWithoutSolutions() throws Exception {
        writtenReferences();var reference=evaluationReader().get(teacher,request.assignmentUuid());
        assertEquals(10,reference.questions().size());assertEquals(1,reference.rubrics().size());assertEquals(1,reference.testVersionNumber());
        assertEquals(2,reference.rubrics().get(0).criteria().size());assertFalse(reference.rubrics().get(0).criteria().get(1).isRequired());
        assertTrue(reference.questions().stream().anyMatch(q->Integer.valueOf(3).equals(q.expectedResponseCount())));
        assertTrue(reference.evaluationReferenceHash().matches("[0-9a-f]{64}"));
        String json=mapper.writeValueAsString(reference);
        for(String secret:List.of("SECRET","answerKey","correctQuestion","accepted_text","Private descriptor","levelDefinition","password"))assertFalse(json.contains(secret),secret);
        var folder=Path.of("target/mobile-evaluation-contract-samples");java.nio.file.Files.createDirectories(folder);
        mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValue(folder.resolve("evaluation-reference.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(reference));
    }
    @Test void evaluationReferenceHashIsStableAndTracksPublicReferenceChanges() {
        var refs=writtenReferences();String first=evaluationReader().get(teacher,request.assignmentUuid()).evaluationReferenceHash();
        assertEquals(first,evaluationReader().get(teacher,request.assignmentUuid()).evaluationReferenceHash());
        jdbc.update("UPDATE answer_keys SET answer_explanation='A DIFFERENT SECRET' WHERE question_id=?",refs.manual());
        assertEquals(first,evaluationReader().get(teacher,request.assignmentUuid()).evaluationReferenceHash());
        jdbc.update("UPDATE rubric_criteria SET criterion_name='Pagkakaisa — 組織' WHERE rubric_id=? AND criterion_order=2",refs.rubric());
        String renamed=evaluationReader().get(teacher,request.assignmentUuid()).evaluationReferenceHash();assertNotEquals(first,renamed);
        jdbc.update("UPDATE tests t JOIN test_assignments d ON d.test_id=t.test_id SET t.version_number=t.version_number+1 WHERE d.assignment_uuid=?",request.assignmentUuid());
        var versioned=evaluationReader().get(teacher,request.assignmentUuid());assertEquals(2,versioned.testVersionNumber());assertNotEquals(renamed,versioned.evaluationReferenceHash());
    }
    @Test void evaluationReferenceSupportsObjectiveOnlyAndManualEssayWithoutRubric() {
        var objective=evaluationReader().get(teacher,request.assignmentUuid());assertTrue(objective.rubrics().isEmpty());
        var refs=writtenReferences();jdbc.update("UPDATE questions SET rubric_id=NULL WHERE question_id=?",refs.essay());
        jdbc.update("UPDATE answer_keys SET answer_key_type='manual',scoring_method='manual',rubric_id=NULL WHERE question_id=?",refs.essay());
        assertTrue(evaluationReader().get(teacher,request.assignmentUuid()).rubrics().isEmpty());
    }
    @Test void evaluationReferenceDeduplicatesSharedRubrics() {
        var refs=writtenReferences();
        jdbc.update("UPDATE questions SET rubric_id=?,maximum_points=10,question_type_id=(SELECT question_type_id FROM question_types WHERE question_type_code='essay') WHERE question_id=?",refs.rubric(),refs.manual());
        jdbc.update("UPDATE answer_keys SET answer_key_type='rubric',scoring_method='rubric',rubric_id=? WHERE question_id=?",refs.rubric(),refs.manual());
        var read=evaluationReader().get(teacher,request.assignmentUuid());assertEquals(1,read.rubrics().size());assertEquals(2,read.questions().stream().filter(q->q.rubricId()!=null).count());
    }
    @Test void evaluationReferenceRejectsArchivedRubricsAndPointScaleMismatch() {
        var refs=writtenReferences();
        jdbc.update("UPDATE rubrics SET rubric_status='archived' WHERE rubric_id=?",refs.rubric());
        assertEquals("EVALUATION_REFERENCE_INVALID",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
        jdbc.update("UPDATE rubrics SET rubric_status='active',total_points=9 WHERE rubric_id=?",refs.rubric());
        assertEquals("EVALUATION_REFERENCE_INVALID",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
        jdbc.update("UPDATE rubrics SET total_points=10 WHERE rubric_id=?",refs.rubric());
        jdbc.update("UPDATE questions SET maximum_points=9 WHERE question_id=?",refs.essay());
        assertEquals("EVALUATION_REFERENCE_INVALID",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
    }
    @Test void evaluationReferenceRejectsMissingKeysAndRubricKeyMismatch() {
        var refs=writtenReferences();jdbc.update("DELETE FROM answer_keys WHERE question_id=?",refs.essay());
        assertEquals("EVALUATION_REFERENCE_INVALID",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
        jdbc.update("INSERT INTO answer_keys(question_id,answer_key_type,scoring_method) VALUES(?,'manual','manual')",refs.essay());
        assertEquals("EVALUATION_REFERENCE_INVALID",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
    }
    @Test void evaluationReferenceDoesNotExposeCrossSchoolRubricContent() {
        var refs=writtenReferences();long address=jdbc.queryForObject("SELECT address_id FROM users WHERE user_id=?",Long.class,teacher.userId());
        String school="OTHER"+uuid().substring(0,8);jdbc.update("INSERT INTO school_profiles(school_id,address_id,school_name) VALUES(?,?,'Foreign school')",school,address);
        jdbc.update("UPDATE rubrics SET school_id=?,rubric_name='FOREIGN PRIVATE RUBRIC' WHERE rubric_id=?",school,refs.rubric());
        var error=assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid()));
        assertEquals("EVALUATION_REFERENCE_INVALID",error.getCode());assertFalse(error.getMessage().contains("FOREIGN"));
    }
    @Test void evaluationReferenceRechecksAssignmentOwnershipAndAccount() {
        writtenReferences();assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,uuid())).getCode());
        long other=insert("user_id","INSERT INTO users(school_id,address_id,gender_id,role_id,status_id,first_name,last_name,email,contact_number,password_hash) SELECT school_id,address_id,gender_id,role_id,status_id,'Other','Teacher',?,?,'TEST_ONLY' FROM users WHERE user_id=?",uuid()+"@example.invalid",uuid().substring(0,12),teacher.userId());
        jdbc.update("UPDATE class_assignments a JOIN test_assignments d ON d.class_assignment_id=a.class_assignment_id SET a.user_id=? WHERE d.assignment_uuid=?",other,request.assignmentUuid());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
        jdbc.update("UPDATE users SET status_id=5 WHERE user_id=?",teacher.userId());
        assertEquals("EVALUATION_REFERENCE_ACCESS_DENIED",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
    }
    @Test void evaluationReferenceRejectsMissingAndOversizedCriteriaWithoutTruncation() {
        var refs=writtenReferences();jdbc.update("DELETE FROM rubric_criteria WHERE rubric_id=?",refs.rubric());
        assertEquals("EVALUATION_REFERENCE_INVALID",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
        for(int i=1;i<=101;i++)jdbc.update("INSERT INTO rubric_criteria(rubric_id,criterion_order,criterion_name,criterion_description,maximum_points) VALUES(?,?,'Criterion','Description',1)",refs.rubric(),i);
        assertEquals("EVALUATION_REFERENCE_LIMIT_EXCEEDED",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
    }
    @Test void evaluationReferenceKeepsOneSnapshotAcrossConcurrentRubricChanges() throws Exception {
        var refs=writtenReferences();String before=evaluationReader().get(teacher,request.assignmentUuid()).evaluationReferenceHash();
        var executor=Executors.newSingleThreadExecutor();
        try {
            var repository=new com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository(jdbc){
                @Override public java.util.List<QuestionRow> questions(long test) {
                    var rows=super.questions(test);
                    try{executor.submit(()->jdbc.update("UPDATE rubric_criteria SET criterion_name='Renamed concurrently' WHERE rubric_id=? AND criterion_order=1",refs.rubric())).get(25,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}
                    return rows;
                }
            };
            var reference=new V3EvaluationReferenceService(repository,new DataSourceTransactionManager(source),true).get(teacher,request.assignmentUuid());
            assertEquals(before,reference.evaluationReferenceHash());assertNotEquals(before,evaluationReader().get(teacher,request.assignmentUuid()).evaluationReferenceHash());
        }finally{executor.shutdownNow();}
    }
    @Test void actualSchemaStillRejectsCropOnlyAnsweredRowsEvenWithRetainedEvidence() {
        long page=service.ingest(teacher,request,upload()).backendScanPageId();var refs=writtenReferences();
        long answer=insert("student_answer_id","INSERT INTO student_answers(test_result_id,question_id,answer_uuid,capture_source,answer_status) VALUES(?,?,?,'manual','pending_manual')",resultId(),refs.manual(),uuid());
        jdbc.update("INSERT INTO answer_attachments(attachment_uuid,student_answer_id,scan_session_id,scan_page_id,attachment_type,storage_provider,storage_key,mime_type,file_size_bytes,content_hash) SELECT ?,?,p.scan_session_id,p.scan_page_id,'teacher_evidence','local','synthetic-evidence.jpg','image/jpeg',1,? FROM scan_pages p WHERE p.scan_page_id=?",uuid(),answer,"a".repeat(64),page);
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE student_answers SET answer_status='answered',response_text=NULL,selected_question_option_id=NULL WHERE student_answer_id=?",answer));
        assertEquals("pending_manual",jdbc.queryForObject("SELECT answer_status FROM student_answers WHERE student_answer_id=?",String.class,answer));
    }

    @Test void evaluationReferenceRejectsUnavailableAssignmentLifecycle() {
        writtenReferences();jdbc.update("UPDATE test_assignments SET assignment_status='archived' WHERE assignment_uuid=?",request.assignmentUuid());
        assertEquals("EVALUATION_REFERENCE_NOT_READY",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
    }
    @Test void evaluationReferenceRejectsMoreThanTwoHundredQuestions() {
        long part=jdbc.queryForObject("SELECT p.test_part_id FROM test_parts p JOIN test_assignments d ON d.test_id=p.test_id WHERE d.assignment_uuid=?",Long.class,request.assignmentUuid());
        long type=jdbc.queryForObject("SELECT question_type_id FROM question_types WHERE question_type_code='multiple_choice'",Long.class);
        for(int i=11;i<=201;i++)jdbc.update("INSERT INTO questions(question_uuid,test_part_id,question_type_id,item_number,question_text,maximum_points) VALUES(?,?,?,?,'Synthetic overflow',2)",uuid(),part,type,i);
        assertEquals("EVALUATION_REFERENCE_LIMIT_EXCEEDED",assertThrows(V3AuthException.class,()->evaluationReader().get(teacher,request.assignmentUuid())).getCode());
    }

    private V3AttachmentUploadService attachments() { return attachments(new DataSourceTransactionManager(source)); }
    private V3AttachmentUploadService attachments(DataSourceTransactionManager manager) {
        return new V3AttachmentUploadService(new V3AttachmentUploadRepository(jdbc),new V3DetectionRepository(jdbc),new V3ScanUploadLedgerRepository(jdbc),storage,mapper,manager);
    }
    private V3AttachmentMetadata attachment(String type) {
        service.ingest(teacher,request,upload());
        String original=jdbc.queryForObject("SELECT attachment_uuid FROM mobile_scan_uploads WHERE scan_page_uuid=?",String.class,request.scanPageUuid());
        String region=jdbc.queryForObject("SELECT MIN(g.region_uuid) FROM answer_sheet_regions g JOIN answer_sheet_pages p ON p.answer_sheet_page_id=g.answer_sheet_page_id WHERE p.page_uuid=?",String.class,request.pageUuid());
        return new V3AttachmentMetadata("3.0",uuid(),uuid(),uuid(),request.resultUuid(),request.scanPageUuid(),type,
            "answer_crop".equals(type)?region:null,"normalized_page".equals(type)?original:null,
            "answer_crop".equals(type)?new Crop(original,"image_pixels_top_left",20,20,2,3,10,10):null,
            request.imageHash(),image.length,"image/jpeg",request.capturedAt());
    }
    private V3AttachmentMetadata editAttachment(V3AttachmentMetadata r,java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> edit) throws Exception {
        var tree=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(r);edit.accept(tree);return mapper.treeToValue(tree,V3AttachmentMetadata.class);
    }
    @Test void attachmentPersistsCropWithUuidAndPixelLineageAndReadback() throws Exception {
        var r=attachment("answer_crop");var response=attachments().upload(teacher,r,upload());
        assertEquals(r.attachmentUuid(),response.attachmentUuid());assertEquals("created",response.uploadStatus());
        var row=jdbc.queryForMap("SELECT * FROM answer_attachments WHERE answer_attachment_id=?",response.backendAttachmentId());
        assertNull(row.get("student_answer_id"));assertNull(row.get("source_answer_attachment_id"));
        assertEquals(mapper.readTree(mapper.writeValueAsString(r.crop())),mapper.readTree((String)row.get("crop_coordinates")));
        assertArrayEquals(image,java.nio.file.Files.readAllBytes(directory.resolve((String)row.get("storage_key"))));
        assertEquals(2,reader().result(teacher,request.resultUuid()).revision());
        assertTrue(reader().result(teacher,request.resultUuid()).idMappings().stream().anyMatch(m->m.uuid().equals(r.attachmentUuid())&&m.centralId()==response.backendAttachmentId()));
        assertEquals("success",reader().sync(teacher,r.syncUuid()).syncStatus());
        var samples=Path.of("target/mobile-attachment-contract-samples");java.nio.file.Files.createDirectories(samples);
        var wire=mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        wire.writeValue(samples.resolve("metadata.json").toFile(),r);
        wire.writeValue(samples.resolve("created.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success("Attachment acknowledged.",response));
        wire.writeValue(samples.resolve("sync.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success("Sync state.",reader().sync(teacher,r.syncUuid())));
    }
    @Test void attachmentNormalizedAndCropUseDifferentLineageRepresentations() throws Exception {
        var normalized=attachment("normalized_page");var first=attachments().upload(teacher,normalized,upload());
        assertNotNull(jdbc.queryForObject("SELECT source_answer_attachment_id FROM answer_attachments WHERE answer_attachment_id=?",Long.class,first.backendAttachmentId()));
        var crop=editAttachment(attachment("answer_crop"),n->((com.fasterxml.jackson.databind.node.ObjectNode)n.get("crop")).put("baseAttachmentUuid",normalized.attachmentUuid()));
        var second=attachments().upload(teacher,crop,upload());
        assertNull(jdbc.queryForObject("SELECT source_answer_attachment_id FROM answer_attachments WHERE answer_attachment_id=?",Long.class,second.backendAttachmentId()));
    }
    @Test void attachmentTeacherEvidenceAcceptsPngAndPreservesBytes() throws Exception {
        var buffer=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(8,7,BufferedImage.TYPE_INT_ARGB),"png",buffer);byte[] png=buffer.toByteArray();String digest=hash(png);
        var r=editAttachment(attachment("teacher_evidence"),n->{n.put("mimeType","image/png");n.put("fileSizeBytes",png.length);n.put("contentHash",digest);});
        var response=attachments().upload(teacher,r,new MockMultipartFile("file","../../unsafe.png","image/png",png));
        String key=jdbc.queryForObject("SELECT storage_key FROM answer_attachments WHERE answer_attachment_id=?",String.class,response.backendAttachmentId());
        assertTrue(key.endsWith(".png"));assertFalse(key.contains("unsafe"));assertArrayEquals(png,java.nio.file.Files.readAllBytes(directory.resolve(key)));
        assertEquals("replayed",attachments().upload(teacher,r,new MockMultipartFile("file","x.png","image/png",png)).uploadStatus());
    }
    @Test void attachmentRetriesReplayAfterFinalizationAndRejectNewEvidence() {
        var r=attachment("teacher_evidence");var first=attachments().upload(teacher,r,upload());
        var next=attachment("teacher_evidence");jdbc.update("UPDATE test_results SET result_status='finalized' WHERE test_result_id=?",resultId());
        var replay=attachments().upload(teacher,r,upload());assertEquals("replayed",replay.uploadStatus());assertEquals(first.acknowledgedAt(),replay.acknowledgedAt());assertEquals(first.backendAttachmentId(),replay.backendAttachmentId());
        assertEquals("ATTACHMENT_STATE_CONFLICT",assertThrows(V3AuthException.class,()->attachments().upload(teacher,next,upload())).getCode());
        assertEquals(2,jdbc.queryForObject("SELECT mobile_revision FROM test_results WHERE test_result_id=?",Integer.class,resultId()));
    }
    @Test void attachmentSimultaneousRetriesProduceOneReceipt() throws Exception {
        var r=attachment("answer_crop");var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=executor.submit(()->{start.await();return attachments().upload(teacher,r,upload());});
            var b=executor.submit(()->{start.await();return attachments().upload(teacher,r,upload());});start.countDown();
            var first=a.get(25,TimeUnit.SECONDS);var second=b.get(25,TimeUnit.SECONDS);
            assertEquals(first.backendAttachmentId(),second.backendAttachmentId());assertTrue(List.of(first.uploadStatus(),second.uploadStatus()).containsAll(List.of("created","replayed")));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM answer_attachments WHERE attachment_uuid=?",Integer.class,r.attachmentUuid()));
        }finally{executor.shutdownNow();}
    }
    @Test void attachmentRollbackAfterPublishRecoversUsingDurableIdentity() throws Exception {
        var r=attachment("answer_crop");
        var manager=new DataSourceTransactionManager(source){int commits;@Override protected void doCommit(DefaultTransactionStatus status){if(++commits==3)throw new TransactionSystemException("Injected before commit");super.doCommit(status);}};
        manager.setRollbackOnCommitFailure(true);
        assertEquals("ATTACHMENT_STORAGE_UNAVAILABLE",assertThrows(V3AuthException.class,()->attachments(manager).upload(teacher,r,upload())).getCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM answer_attachments WHERE attachment_uuid=?",Integer.class,r.attachmentUuid()));
        assertEquals("failed",reader().sync(teacher,r.syncUuid()).syncStatus());
        String key=jdbc.queryForObject("SELECT storage_key FROM mobile_attachment_uploads WHERE operation_uuid=?",String.class,r.operationUuid());
        assertTrue(java.nio.file.Files.exists(directory.resolve(key)));
        var response=attachments().upload(teacher,r,upload());assertEquals("created",response.uploadStatus());
        assertEquals(key,jdbc.queryForObject("SELECT storage_key FROM answer_attachments WHERE answer_attachment_id=?",String.class,response.backendAttachmentId()));
        assertEquals("success",reader().sync(teacher,r.syncUuid()).syncStatus());
    }
    @Test void attachmentLostCommitAcknowledgementReplaysWithoutDuplicate() {
        var r=attachment("teacher_evidence");
        var manager=new DataSourceTransactionManager(source){int commits;@Override protected void doCommit(DefaultTransactionStatus status){super.doCommit(status);if(++commits==3)throw new TransactionSystemException("Injected lost acknowledgement");}};
        assertThrows(V3AuthException.class,()->attachments(manager).upload(teacher,r,upload()));
        assertEquals("replayed",attachments().upload(teacher,r,upload()).uploadStatus());assertEquals("success",reader().sync(teacher,r.syncUuid()).syncStatus());
    }
    @Test void attachmentMissingPendingBytesCanBeRestoredButCorruptPublishedBytesCannot() throws Exception {
        var r=attachment("teacher_evidence");
        var manager=new DataSourceTransactionManager(source){int commits;@Override protected void doCommit(DefaultTransactionStatus status){if(++commits==3)throw new TransactionSystemException("Injected");super.doCommit(status);}};manager.setRollbackOnCommitFailure(true);
        assertThrows(V3AuthException.class,()->attachments(manager).upload(teacher,r,upload()));
        String key=jdbc.queryForObject("SELECT storage_key FROM mobile_attachment_uploads WHERE operation_uuid=?",String.class,r.operationUuid());
        java.nio.file.Files.delete(directory.resolve(key));
        assertEquals("created",attachments().upload(teacher,r,upload()).uploadStatus());
        java.nio.file.Files.write(directory.resolve(key),new byte[]{9});
        assertEquals("SCAN_EVIDENCE_INTEGRITY_FAILED",assertThrows(V3AuthException.class,()->attachments().upload(teacher,r,upload())).getCode());assertArrayEquals(new byte[]{9},java.nio.file.Files.readAllBytes(directory.resolve(key)));
    }
    @Test void attachmentRejectsChangedOperationAttachmentAndSyncIdentity() throws Exception {
        var r=attachment("teacher_evidence");attachments().upload(teacher,r,upload());
        var changed=editAttachment(r,n->n.put("capturedAt","2026-01-01T00:00:00Z"));
        assertEquals("ATTACHMENT_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->attachments().upload(teacher,changed,upload())).getCode());
        var reused=editAttachment(r,n->{n.put("operationUuid",uuid());n.put("syncUuid",uuid());});
        assertEquals("ATTACHMENT_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->attachments().upload(teacher,reused,upload())).getCode());
        var sync=editAttachment(r,n->{n.put("operationUuid",uuid());n.put("attachmentUuid",uuid());});
        assertEquals("ATTACHMENT_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->attachments().upload(teacher,sync,upload())).getCode());
    }
    @Test void attachmentRejectsWrongHashMimeSizeAndMalformedPng() throws Exception {
        var r=attachment("teacher_evidence");
        var badHash=editAttachment(r,n->n.put("contentHash","0".repeat(64)));
        assertEquals("IMAGE_HASH_MISMATCH",assertThrows(V3AuthException.class,()->attachments().upload(teacher,badHash,upload())).getCode());
        var wrongSize=editAttachment(r,n->n.put("fileSizeBytes",image.length+1));assertThrows(V3AuthException.class,()->attachments().upload(teacher,wrongSize,upload()));
        var wrongMime=editAttachment(r,n->n.put("mimeType","image/png"));assertThrows(V3AuthException.class,()->attachments().upload(teacher,wrongMime,upload()));
        assertThrows(V3AuthException.class,()->attachments().upload(teacher,wrongMime,new MockMultipartFile("file","x","image/png",image)));
        var buffer=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",buffer);byte[] png=buffer.toByteArray();png[png.length-1]^=1;String hash=hash(png);
        var damaged=editAttachment(r,n->{n.put("mimeType","image/png");n.put("fileSizeBytes",png.length);n.put("contentHash",hash);});
        assertThrows(V3AuthException.class,()->attachments().upload(teacher,damaged,new MockMultipartFile("file","x","image/png",png)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_attachment_uploads WHERE operation_uuid=?",Integer.class,r.operationUuid()));
    }
    @Test void attachmentRejectsInvalidRegionBoundsBaseDimensionsAndSourceFk() throws Exception {
        var r=attachment("answer_crop");
        for(String field:List.of("x","baseWidthPx","width")) {
            var invalid=editAttachment(r,n->((com.fasterxml.jackson.databind.node.ObjectNode)n.get("crop")).put(field,field.equals("baseWidthPx")?21:Long.MAX_VALUE));
            assertEquals("ATTACHMENT_LINEAGE_INVALID",assertThrows(V3AuthException.class,()->attachments().upload(teacher,invalid,upload())).getCode());
        }
        var foreignRegion=editAttachment(r,n->n.put("regionUuid",uuid()));assertThrows(V3AuthException.class,()->attachments().upload(teacher,foreignRegion,upload()));
        var wrongSource=editAttachment(r,n->n.put("sourceAttachmentUuid",r.crop().baseAttachmentUuid()));assertThrows(V3AuthException.class,()->attachments().upload(teacher,wrongSource,upload()));
        var self=editAttachment(r,n->((com.fasterxml.jackson.databind.node.ObjectNode)n.get("crop")).put("baseAttachmentUuid",r.attachmentUuid()));assertThrows(V3AuthException.class,()->attachments().upload(teacher,self,upload()));
    }
    @Test void attachmentRejectsCrossPageBaseAndWrongResult() throws Exception {
        var r=attachment("answer_crop");var other=secondLearnerCapture();service.ingest(teacher,other,upload());
        String base=jdbc.queryForObject("SELECT attachment_uuid FROM mobile_scan_uploads WHERE scan_page_uuid=?",String.class,other.scanPageUuid());
        var cross=editAttachment(r,n->((com.fasterxml.jackson.databind.node.ObjectNode)n.get("crop")).put("baseAttachmentUuid",base));assertThrows(V3AuthException.class,()->attachments().upload(teacher,cross,upload()));
        var wrong=editAttachment(r,n->n.put("resultUuid",other.resultUuid()));assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->attachments().upload(teacher,wrong,upload())).getCode());
    }
    @Test void attachmentRejectsPurgedSourcesAndNeverResurrectsPurgedReceipt() {
        var r=attachment("teacher_evidence");var response=attachments().upload(teacher,r,upload());
        jdbc.update("UPDATE answer_attachments SET purge_status='purged',last_purge_attempt_at=CURRENT_TIMESTAMP,purged_at=CURRENT_TIMESTAMP,purge_reason='Synthetic test' WHERE answer_attachment_id=?",response.backendAttachmentId());
        assertEquals("ATTACHMENT_LINEAGE_INVALID",assertThrows(V3AuthException.class,()->attachments().upload(teacher,r,upload())).getCode());
        var crop=attachment("answer_crop");jdbc.update("UPDATE answer_attachments SET purge_status='purged',last_purge_attempt_at=CURRENT_TIMESTAMP,purged_at=CURRENT_TIMESTAMP,purge_reason='Synthetic test' WHERE attachment_uuid=?",crop.crop().baseAttachmentUuid());
        assertThrows(V3AuthException.class,()->attachments().upload(teacher,crop,upload()));
    }
    @Test void attachmentRechecksCurrentTeacherOnReplayAndRejectsRejectedPage() {
        var r=attachment("teacher_evidence");attachments().upload(teacher,r,upload());var next=attachment("teacher_evidence");
        jdbc.update("UPDATE scan_pages SET page_status='rejected' WHERE scan_page_uuid=?",r.scanPageUuid());
        assertEquals("ATTACHMENT_STATE_CONFLICT",assertThrows(V3AuthException.class,()->attachments().upload(teacher,next,upload())).getCode());
        jdbc.update("UPDATE users SET status_id=(SELECT status_id FROM statuses WHERE status_name='inactive' LIMIT 1) WHERE user_id=?",teacher.userId());
        assertEquals("TEACHER_REQUIRED",assertThrows(V3AuthException.class,()->attachments().upload(teacher,r,upload())).getCode());
    }

    private V3WrittenVerificationService writtenVerifier() { return writtenVerifier(new DataSourceTransactionManager(source),evaluationReader()); }
    private V3WrittenVerificationService writtenVerifier(DataSourceTransactionManager manager,V3EvaluationReferenceService references) {
        return new V3WrittenVerificationService(new V3VerificationRepository(jdbc),new com.capstone.assessment.v3.mobile.repository.V3WrittenVerificationRepository(jdbc),
                new V3DetectionRepository(jdbc),new V3ScanUploadLedgerRepository(jdbc),references,attachments(),validators.getValidator(),mapper,manager);
    }
    private record WrittenCase(V3WrittenVerificationBatch batch,WrittenRefs refs,String attachment) { }
    private WrittenCase writtenCase(boolean rubric,boolean imageOnly) throws Exception {
        service.ingest(teacher,request,upload());var refs=writtenReferences();
        // Synthetic written manifest rows exercise persistence only; no production printed template/geometry is changed.
        jdbc.update("UPDATE answer_sheet_regions g JOIN questions q ON q.question_id=g.question_id SET g.question_type_id=q.question_type_id,g.region_type='written_response',g.response_region_size='long' WHERE q.question_id IN (?,?,?)",refs.essay(),refs.manual(),refs.enumeration());
        long question=rubric?refs.essay():refs.manual();
        String q=jdbc.queryForObject("SELECT question_uuid FROM questions WHERE question_id=?",String.class,question);
        String region=jdbc.queryForObject("SELECT region_uuid FROM answer_sheet_regions WHERE question_id=?",String.class,question);
        String attachment=null;
        if(imageOnly) {
            var r=editAttachment(attachment("answer_crop"),n->n.put("regionUuid",region));attachments().upload(teacher,r,upload());attachment=r.attachmentUuid();
        }
        var ref=evaluationReader().get(teacher,request.assignmentUuid());
        V3WrittenVerificationBatch.Evaluation evaluation=rubric?
            new V3WrittenVerificationBatch.Rubric("answered",imageOnly?null:"Actual student response",attachment==null?List.of():List.of(attachment),refs.rubric(),
                ref.rubrics().get(0).criteria().stream().map(c->new V3WrittenVerificationBatch.CriterionScore(c.rubricCriterionId(),new java.math.BigDecimal(c.maximumPoints().intValue()==6?"4.00":"3.00"),"Teacher criterion comment")).toList()):
            new V3WrittenVerificationBatch.Manual("answered",imageOnly?null:"Actual student response",attachment==null?List.of():List.of(attachment),new java.math.BigDecimal("1.50"));
        var answer=new V3WrittenVerificationBatch.Answer(uuid(),uuid(),q,region,request.scanPageUuid(),evaluation,"Needs more explanation.",Instant.now());
        long revision=reader().result(teacher,request.resultUuid()).revision();
        var item=new V3WrittenVerificationBatch.Item(request.resultUuid(),revision,ref.testVersionNumber(),ref.evaluationReferenceHash(),
            List.of(new PageDecision(uuid(),request.scanPageUuid(),"accepted",null,null,Instant.now())),List.of(answer));
        return new WrittenCase(new V3WrittenVerificationBatch("3.1",uuid(),uuid(),request.assignmentUuid(),List.of(item)),refs,attachment);
    }
    private V3WrittenVerificationBatch changeWritten(V3WrittenVerificationBatch batch,java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> edit)throws Exception {
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(batch);edit.accept(node);return mapper.treeToValue(node,V3WrittenVerificationBatch.class);
    }
    private com.fasterxml.jackson.databind.node.ObjectNode evaluationNode(com.fasterxml.jackson.databind.node.ObjectNode n) {
        return (com.fasterxml.jackson.databind.node.ObjectNode)n.path("items").get(0).path("answers").get(0).path("evaluation");
    }
    private com.capstone.assessment.v3.mobile.dto.V3VerificationResponse.Outcome writtenOutcome(V3WrittenVerificationBatch r){return writtenVerifier().verify(teacher,r).items().get(0);}
    @Test void writtenManualImageOnlyPersistsEvidenceScoreAndAuditedReference()throws Exception {
        var c=writtenCase(false,true);var r=c.batch();var result=writtenVerifier().verify(teacher,r);assertEquals("success",result.syncStatus());
        long answer=result.items().get(0).idMappings().get(0).centralId();var row=jdbc.queryForMap("SELECT * FROM student_answers WHERE student_answer_id=?",answer);
        assertNull(row.get("response_text"));assertNull(row.get("selected_question_option_id"));assertNotNull(row.get("response_evidence_attachment_id"));assertEquals(new java.math.BigDecimal("1.50"),row.get("points_earned"));
        assertEquals("manual",row.get("capture_source"));assertEquals("Needs more explanation.",row.get("teacher_feedback"));
        assertEquals(answer,jdbc.queryForObject("SELECT student_answer_id FROM answer_attachments WHERE attachment_uuid=?",Long.class,c.attachment()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM answer_verifications WHERE student_answer_id=? AND mobile_sync_item_id IS NOT NULL AND evaluation_snapshot_json IS NOT NULL",Integer.class,answer));
        assertEquals("success",reader().sync(teacher,r.syncUuid()).syncStatus());assertEquals("pending",reader().analytics(teacher,request.resultUuid()).status());
        var samples=Path.of("target/mobile-written-contract-samples");java.nio.file.Files.createDirectories(samples);var wire=mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        wire.writeValue(samples.resolve("manual-request.json").toFile(),r);wire.writeValue(samples.resolve("manual-response.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(result));
        wire.writeValue(samples.resolve("sync.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(reader().sync(teacher,r.syncUuid())));
    }
    @Test void writtenRubricBackendSumsCriteriaAndPreservesHistoryAfterRubricEdit()throws Exception {
        var c=writtenCase(true,true);var outcome=writtenOutcome(c.batch());assertEquals("success",outcome.status());long answer=outcome.idMappings().get(0).centralId();
        assertEquals(new java.math.BigDecimal("7.00"),jdbc.queryForObject("SELECT points_earned FROM student_answers WHERE student_answer_id=?",java.math.BigDecimal.class,answer));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM answer_rubric_scores WHERE student_answer_id=? AND answer_verification_id IS NOT NULL AND criterion_feedback='Teacher criterion comment'",Integer.class,answer));
        String snapshot=jdbc.queryForObject("SELECT r.reference_json FROM mobile_written_references r JOIN answer_verifications v ON v.mobile_sync_item_id=r.sync_item_id WHERE v.student_answer_id=?",String.class,answer);
        jdbc.update("UPDATE rubric_criteria SET criterion_name='Changed after acceptance' WHERE rubric_id=?",c.refs().rubric());
        assertEquals("replayed",writtenOutcome(c.batch()).disposition());assertEquals(snapshot,jdbc.queryForObject("SELECT r.reference_json FROM mobile_written_references r JOIN answer_verifications v ON v.mobile_sync_item_id=r.sync_item_id WHERE v.student_answer_id=?",String.class,answer));
        assertEquals(2,reader().result(teacher,request.resultUuid()).rubricScoreMappings().size());
        var samples=Path.of("target/mobile-written-contract-samples");java.nio.file.Files.createDirectories(samples);mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValue(samples.resolve("rubric-request.json").toFile(),c.batch());
    }
    @Test void writtenRejectsStaleRubricReferenceWithoutSavingPageOrScore()throws Exception {
        var c=writtenCase(true,false);jdbc.update("UPDATE rubric_criteria SET criterion_name='Changed while offline' WHERE rubric_id=?",c.refs().rubric());
        var out=writtenOutcome(c.batch());assertEquals("EVALUATION_REFERENCE_STALE",out.error().code());assertFalse(out.error().retryable());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE test_result_id=?",Integer.class,resultId()));
        assertEquals("captured",jdbc.queryForObject("SELECT page_status FROM scan_pages WHERE scan_page_uuid=?",String.class,request.scanPageUuid()));
        assertEquals("failed",reader().sync(teacher,c.batch().syncUuid()).syncStatus());
    }
    @Test void writtenRejectsWrongTestVersionAndResultRevision()throws Exception {
        var c=writtenCase(false,false);var wrong=changeWritten(c.batch(),n->((com.fasterxml.jackson.databind.node.ObjectNode)n.path("items").get(0)).put("testVersionNumber",2));
        assertEquals("EVALUATION_REFERENCE_STALE",writtenOutcome(wrong).error().code());
        var stale=changeWritten(c.batch(),n->{n.put("operationUuid",uuid());n.put("syncUuid",uuid());((com.fasterxml.jackson.databind.node.ObjectNode)n.path("items").get(0)).put("expectedRevision",99);});
        assertEquals("REVISION_CONFLICT",writtenOutcome(stale).error().code());
    }
    @Test void writtenRejectsOutOfRangeManualScoreAndQuestionTextLimit()throws Exception {
        var c=writtenCase(false,false);var over=changeWritten(c.batch(),n->evaluationNode(n).put("points",3));assertEquals("MANUAL_SCORE_OUT_OF_RANGE",writtenOutcome(over).error().code());
        jdbc.update("UPDATE questions SET maximum_response_length=3 WHERE question_id=?",c.refs().manual());
        var length=changeWritten(c.batch(),n->{n.put("operationUuid",uuid());n.put("syncUuid",uuid());});assertEquals("RESPONSE_TEXT_TOO_LONG",writtenOutcome(length).error().code());
    }
    @Test void writtenRejectsIncompleteForeignDuplicateAndExcessiveRubricCriteria()throws Exception {
        var c=writtenCase(true,false);
        for(String mode:List.of("missing","foreign","excess")) {
            var invalid=changeWritten(c.batch(),n->{n.put("operationUuid",uuid());n.put("syncUuid",uuid());var scores=(com.fasterxml.jackson.databind.node.ArrayNode)evaluationNode(n).get("criterionScores");
                if(mode.equals("missing"))scores.remove(1);else ((com.fasterxml.jackson.databind.node.ObjectNode)scores.get(0)).put(mode.equals("foreign")?"rubricCriterionId":"pointsAwarded",999999);});
            assertEquals("failed",writtenOutcome(invalid).status());
        }
        var duplicate=changeWritten(c.batch(),n->{var scores=(com.fasterxml.jackson.databind.node.ArrayNode)evaluationNode(n).get("criterionScores");scores.set(1,scores.get(0).deepCopy());});
        assertEquals("VALIDATION_FAILED",assertThrows(V3AuthException.class,()->writtenOutcome(duplicate)).getCode());
    }
    @Test void writtenRejectsManualOverrideOfAssignedEssayRubric()throws Exception {
        var c=writtenCase(true,false);var invalid=changeWritten(c.batch(),n->{var e=evaluationNode(n);e.put("kind","manual");e.put("points",7);e.remove(List.of("rubricId","criterionScores"));});
        assertEquals("RUBRIC_REQUIRED",writtenOutcome(invalid).error().code());
    }
    @Test void writtenAcceptsDirectManualEssayWhenConfiguredWithoutRubric()throws Exception {
        var c=writtenCase(true,false);jdbc.update("UPDATE questions SET rubric_id=NULL WHERE question_id=?",c.refs().essay());jdbc.update("UPDATE answer_keys SET rubric_id=NULL,answer_key_type='manual',scoring_method='manual' WHERE question_id=?",c.refs().essay());
        var ref=evaluationReader().get(teacher,request.assignmentUuid());var valid=changeWritten(c.batch(),n->{((com.fasterxml.jackson.databind.node.ObjectNode)n.path("items").get(0)).put("evaluationReferenceHash",ref.evaluationReferenceHash());var e=evaluationNode(n);e.put("kind","manual");e.put("points",7);e.remove(List.of("rubricId","criterionScores"));});
        assertEquals("success",writtenOutcome(valid).status());
    }
    @Test void writtenBlankRequiresZeroAndRealAnswersNeedTextOrEvidence()throws Exception {
        var c=writtenCase(false,false);var blank=changeWritten(c.batch(),n->{var e=evaluationNode(n);e.put("answerStatus","blank");e.putNull("responseText");});
        assertEquals("BLANK_ANSWER_HAS_POINTS",writtenOutcome(blank).error().code());
        var zero=changeWritten(blank,n->{n.put("operationUuid",uuid());n.put("syncUuid",uuid());evaluationNode(n).put("points",0);});assertEquals("success",writtenOutcome(zero).status());
        var missing=changeWritten(c.batch(),n->evaluationNode(n).putNull("responseText"));assertThrows(V3AuthException.class,()->writtenOutcome(missing));
        var empty=changeWritten(c.batch(),n->evaluationNode(n).put("responseText","   "));assertThrows(V3AuthException.class,()->writtenOutcome(empty));
    }
    @Test void writtenRejectsPurgedAndForeignRegionEvidence()throws Exception {
        var c=writtenCase(false,true);jdbc.update("UPDATE answer_attachments SET answer_sheet_region_id=(SELECT MIN(answer_sheet_region_id) FROM answer_sheet_regions WHERE question_id=?) WHERE attachment_uuid=?",c.refs().essay(),c.attachment());
        assertEquals("ATTACHMENT_LINEAGE_INVALID",writtenOutcome(c.batch()).error().code());
        var another=changeWritten(c.batch(),n->{n.put("operationUuid",uuid());n.put("syncUuid",uuid());});
        jdbc.update("UPDATE answer_attachments SET purge_status='purged',last_purge_attempt_at=CURRENT_TIMESTAMP,purged_at=CURRENT_TIMESTAMP,purge_reason='Synthetic validation' WHERE attachment_uuid=?",c.attachment());
        assertEquals("ATTACHMENT_LINEAGE_INVALID",writtenOutcome(another).error().code());
    }
    @Test void writtenDuplicateRetryAndChangedPayloadCannotRescore()throws Exception {
        var c=writtenCase(false,true);var first=writtenOutcome(c.batch());assertEquals("success",first.status());assertEquals("replayed",writtenOutcome(c.batch()).disposition());
        var changed=changeWritten(c.batch(),n->evaluationNode(n).put("points",1));assertEquals("VERIFICATION_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->writtenOutcome(changed)).getCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM answer_verifications WHERE student_answer_id=?",Integer.class,first.idMappings().get(0).centralId()));
    }
    @Test void writtenConcurrentRetryCommitsOneScore()throws Exception {
        var c=writtenCase(true,true);var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=executor.submit(()->{start.await();return writtenOutcome(c.batch());});var b=executor.submit(()->{start.await();return writtenOutcome(c.batch());});start.countDown();
            var x=a.get(25,TimeUnit.SECONDS);var y=b.get(25,TimeUnit.SECONDS);assertEquals(x.idMappings(),y.idMappings());assertTrue(List.of(x.disposition(),y.disposition()).containsAll(List.of("created","replayed")));
        }finally{executor.shutdownNow();}
    }
    @Test void writtenRollbackIsAtomicAndRetryRecoversOriginalOperation()throws Exception {
        var c=writtenCase(true,true);var manager=new DataSourceTransactionManager(source){int commits;@Override protected void doCommit(DefaultTransactionStatus status){if(++commits==2)throw new TransactionSystemException("Injected rollback");super.doCommit(status);}};manager.setRollbackOnCommitFailure(true);
        var failed=writtenVerifier(manager,evaluationReader()).verify(teacher,c.batch()).items().get(0);assertTrue(failed.error().retryable());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE test_result_id=?",Integer.class,resultId()));assertNull(jdbc.queryForObject("SELECT student_answer_id FROM answer_attachments WHERE attachment_uuid=?",Long.class,c.attachment()));
        assertEquals("success",writtenOutcome(c.batch()).status());
    }
    @Test void writtenLostCommitAcknowledgementReturnsDurableReceipt()throws Exception {
        var c=writtenCase(false,true);var manager=new DataSourceTransactionManager(source){int commits;@Override protected void doCommit(DefaultTransactionStatus status){super.doCommit(status);if(++commits==2)throw new TransactionSystemException("Lost acknowledgement");}};
        var response=writtenVerifier(manager,evaluationReader()).verify(teacher,c.batch());assertEquals("success",response.syncStatus());assertEquals("replayed",response.items().get(0).disposition());
    }
    @Test void writtenRechecksOwnerBeforeHistoricalReplay()throws Exception {
        var c=writtenCase(false,true);assertEquals("success",writtenOutcome(c.batch()).status());jdbc.update("UPDATE users SET status_id=(SELECT status_id FROM statuses WHERE status_name='inactive' LIMIT 1) WHERE user_id=?",teacher.userId());
        assertEquals("VERIFICATION_FORBIDDEN",assertThrows(V3AuthException.class,()->writtenOutcome(c.batch())).getCode());
    }
    @Test void writtenReferenceLocksBlockConcurrentRubricEditUntilScoreCommit()throws Exception {
        var c=writtenCase(true,false);var executor=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var repository=new com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository(jdbc){
            @Override public java.util.List<com.capstone.assessment.v3.mobile.dto.V3EvaluationReference.Criterion> criteria(long rubric,boolean lock){
                var rows=super.criteria(rubric,lock);if(lock){executor.submit(()->{started.countDown();try{jdbc.update("UPDATE rubric_criteria SET criterion_name='Concurrent edit' WHERE rubric_id=?",rubric);}finally{finished.countDown();}});
                    try{assertTrue(started.await(2,TimeUnit.SECONDS));assertFalse(finished.await(200,TimeUnit.MILLISECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}
                return rows;
            }};
        try{var ref=new V3EvaluationReferenceService(repository,new DataSourceTransactionManager(source),true);assertEquals("success",writtenVerifier(new DataSourceTransactionManager(source),ref).verify(teacher,c.batch()).syncStatus());assertTrue(finished.await(5,TimeUnit.SECONDS));}
        finally{executor.shutdownNow();}
    }

    @Test void writtenPartialSuccessCommitsGoodResultAndPreservesRejectedItem()throws Exception {
        var second=secondLearnerCapture();service.ingest(teacher,second,upload());var c=writtenCase(false,false);
        var requestNode=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(c.batch());
        var item=requestNode.path("items").get(0).deepCopy();var other=(com.fasterxml.jackson.databind.node.ObjectNode)item;
        other.put("resultUuid",second.resultUuid());other.put("expectedRevision",1);
        var page=(com.fasterxml.jackson.databind.node.ObjectNode)other.path("pageDecisions").get(0);page.put("scanPageUuid",second.scanPageUuid());page.put("verificationUuid",uuid());
        var answer=(com.fasterxml.jackson.databind.node.ObjectNode)other.path("answers").get(0);answer.put("scanPageUuid",second.scanPageUuid());answer.put("answerUuid",uuid());answer.put("verificationUuid",uuid());((com.fasterxml.jackson.databind.node.ObjectNode)answer.get("evaluation")).put("points",3);
        ((com.fasterxml.jackson.databind.node.ArrayNode)requestNode.get("items")).add(other);
        var batch=mapper.treeToValue(requestNode,V3WrittenVerificationBatch.class);var response=writtenVerifier().verify(teacher,batch);
        assertEquals("partial_success",response.syncStatus());assertEquals("partial_success",reader().sync(teacher,batch.syncUuid()).syncStatus());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE test_result_id=?",Integer.class,resultId()));
        var replay=writtenVerifier().verify(teacher,batch);assertEquals("partial_success",replay.syncStatus());
        assertEquals("replayed",replay.items().stream().filter(i->i.resultUuid().equals(request.resultUuid())).findFirst().orElseThrow().disposition());
    }
    @Test void writtenImageOnlyConstraintRequiresRealEvidenceReferenceAndCannotBeOmr()throws Exception {
        var c=writtenCase(false,true);long id=writtenOutcome(c.batch()).idMappings().get(0).centralId();
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE student_answers SET response_evidence_attachment_id=NULL WHERE student_answer_id=?",id));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE student_answers SET capture_source='omr' WHERE student_answer_id=?",id));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE student_answers SET response_evidence_attachment_id=9007199254740990 WHERE student_answer_id=?",id));
    }

    private WrittenCase completeWrittenResult(boolean allWritten,boolean manualEssay,boolean blank)throws Exception {
        var detections=detectionFixture(false,10);var c=writtenCase(true,true);
        if(allWritten) {
            jdbc.update("UPDATE questions q JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id SET q.question_type_id=(SELECT question_type_id FROM question_types WHERE question_type_code='identification') WHERE d.assignment_uuid=? AND q.question_id NOT IN (?,?,?)",request.assignmentUuid(),c.refs().essay(),c.refs().manual(),c.refs().enumeration());
            jdbc.update("INSERT INTO answer_keys(question_id,answer_key_type,scoring_method) SELECT q.question_id,'manual','manual' FROM questions q JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id LEFT JOIN answer_keys k ON k.question_id=q.question_id WHERE d.assignment_uuid=? AND k.answer_key_id IS NULL",request.assignmentUuid());
            jdbc.update("UPDATE answer_sheet_regions g JOIN questions q ON q.question_id=g.question_id JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id SET g.question_type_id=q.question_type_id,g.region_type='written_response',g.response_region_size='long' WHERE d.assignment_uuid=?",request.assignmentUuid());
        } else {
            var objective=jdbc.queryForList("SELECT q.question_uuid FROM questions q JOIN question_types t ON t.question_type_id=q.question_type_id JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id WHERE d.assignment_uuid=? AND t.question_type_code='multiple_choice'",String.class,request.assignmentUuid());
            var batch=new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch("3.0",detections.syncUuid(),detections.operationUuid(),detections.detections().stream().filter(d->objective.contains(d.questionUuid())).toList());
            detectionService(new DataSourceTransactionManager(source)).upload(teacher,request.scanPageUuid(),batch);
            var answers=batch.detections().stream().map(d->new Answer(uuid(),uuid(),d.questionUuid(),d.regionUuid(),request.scanPageUuid(),new ObjectiveEvaluation("objective",d.detectionUuid()),"Reviewed",Instant.now())).toList();
            long revision=reader().result(teacher,request.resultUuid()).revision();
            var verified=new V3VerificationBatch("3.0",uuid(),uuid(),request.assignmentUuid(),List.of(new Item(request.resultUuid(),revision,List.of(new PageDecision(uuid(),request.scanPageUuid(),"accepted",null,null,Instant.now())),answers)));
            assertEquals("success",verificationService(new DataSourceTransactionManager(source)).verify(teacher,verified).syncStatus());
            jdbc.update("INSERT INTO answer_keys(question_id,answer_key_type,scoring_method,correct_question_option_id) SELECT q.question_id,'option','exact',o.question_option_id FROM questions q JOIN question_types t ON t.question_type_id=q.question_type_id JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id JOIN question_options o ON o.question_id=q.question_id AND o.option_key='A' WHERE d.assignment_uuid=? AND t.question_type_code='multiple_choice'",request.assignmentUuid());
        }
        if(manualEssay) {
            jdbc.update("UPDATE questions SET rubric_id=NULL WHERE question_id=?",c.refs().essay());
            jdbc.update("UPDATE answer_keys SET rubric_id=NULL,answer_key_type='manual',scoring_method='manual' WHERE question_id=?",c.refs().essay());
        }
        var original=c.batch().items().get(0).answers().get(0);var answers=new java.util.ArrayList<V3WrittenVerificationBatch.Answer>();
        V3WrittenVerificationBatch.Evaluation essay=manualEssay?new V3WrittenVerificationBatch.Manual("answered",null,List.of(c.attachment()),new java.math.BigDecimal("7.00")):original.evaluation();
        answers.add(new V3WrittenVerificationBatch.Answer(original.answerUuid(),original.verificationUuid(),original.questionUuid(),original.regionUuid(),original.scanPageUuid(),essay,original.comment(),original.clientDecidedAt()));
        var others=jdbc.queryForList("SELECT q.question_id,q.question_uuid,g.region_uuid FROM questions q JOIN answer_sheet_regions g ON g.question_id=q.question_id JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id WHERE d.assignment_uuid=? AND g.region_type='written_response' AND q.question_id<>? ORDER BY q.question_id",request.assignmentUuid(),c.refs().essay());
        for(var row:others) {
            boolean makeBlank=blank && ((Number)row.get("question_id")).longValue()==c.refs().enumeration();
            var value=new V3WrittenVerificationBatch.Manual(makeBlank?"blank":"answered",makeBlank?null:"Actual response",List.of(),new java.math.BigDecimal(makeBlank?"0.00":((Number)row.get("question_id")).longValue()==c.refs().manual()?"1.50":"1.00"));
            answers.add(new V3WrittenVerificationBatch.Answer(uuid(),uuid(),(String)row.get("question_uuid"),(String)row.get("region_uuid"),request.scanPageUuid(),value,"Teacher checked",Instant.now()));
        }
        var reference=evaluationReader().get(teacher,request.assignmentUuid());long revision=reader().result(teacher,request.resultUuid()).revision();
        var item=new V3WrittenVerificationBatch.Item(request.resultUuid(),revision,reference.testVersionNumber(),reference.evaluationReferenceHash(),allWritten?c.batch().items().get(0).pageDecisions():List.of(),List.copyOf(answers));
        var batch=new V3WrittenVerificationBatch("3.1",uuid(),uuid(),request.assignmentUuid(),List.of(item));
        assertEquals("success",writtenVerifier().verify(teacher,batch).syncStatus());return new WrittenCase(batch,c.refs(),c.attachment());
    }
    private void assertWrittenUnfinalized(long revision) {
        assertEquals("pending_verification",jdbc.queryForObject("SELECT result_status FROM test_results WHERE test_result_id=?",String.class,resultId()));
        assertEquals(revision,reader().result(teacher,request.resultUuid()).revision());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_finalizations WHERE test_result_id=?",Integer.class,resultId()));
    }
    @Test void mixedWrittenFinalizationComputesOfficialTotalsAndReadback()throws Exception {
        var c=completeWrittenResult(false,false,false);long revision=reader().result(teacher,request.resultUuid()).revision();
        var score=finalizeScore(resultId());assertEquals(new java.math.BigDecimal("23.50"),score.totalScore());assertEquals(new java.math.BigDecimal("28.00"),score.maxScore());assertEquals(new java.math.BigDecimal("83.93"),score.percentage());assertEquals(10,score.itemsEvaluated());
        assertEquals("finalized",score.resultStatus());assertEquals(revision+1,reader().result(teacher,request.resultUuid()).revision());
        var result=reader().result(teacher,request.resultUuid());var analytics=reader().analytics(teacher,request.resultUuid());assertEquals("ready",analytics.status());assertEquals(score.totalScore(),analytics.metrics().totalScore());assertEquals(score.maxScore(),result.officialScore().maxScore());
        assertEquals(new java.math.BigDecimal("7.00"),jdbc.queryForObject("SELECT points_earned FROM student_answers WHERE test_result_id=? AND question_id=?",java.math.BigDecimal.class,resultId(),c.refs().essay()));
        assertEquals("success",reader().sync(teacher,c.batch().syncUuid()).syncStatus());
        var samples=Path.of("target/mobile-written-finalization-samples");java.nio.file.Files.createDirectories(samples);var wire=mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        wire.writeValue(samples.resolve("finalized.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(score));wire.writeValue(samples.resolve("result.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(result));wire.writeValue(samples.resolve("analytics.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(analytics));
    }
    @Test void writtenOnlyAndDirectManualEssayFinalizeUsingTeacherPoints()throws Exception {
        completeWrittenResult(true,true,true);var score=finalizeScore(resultId());
        assertEquals(new java.math.BigDecimal("15.50"),score.totalScore());assertEquals(new java.math.BigDecimal("28.00"),score.maxScore());assertEquals(10,score.itemsEvaluated());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE test_result_id=? AND capture_source='omr'",Integer.class,resultId()));
        assertEquals("ready",reader().analytics(teacher,request.resultUuid()).status());
    }
    @Test void writtenFinalizationRejectsMissingWrittenAnswerWithoutObjectiveMutation()throws Exception {
        var c=completeWrittenResult(false,false,false);long revision=reader().result(teacher,request.resultUuid()).revision();
        long answer=jdbc.queryForObject("SELECT student_answer_id FROM student_answers WHERE test_result_id=? AND question_id=?",Long.class,resultId(),c.refs().manual());
        jdbc.update("DELETE FROM answer_verifications WHERE student_answer_id=?",answer);jdbc.update("DELETE FROM student_answers WHERE student_answer_id=?",answer);
        assertEquals("WRITTEN_VERIFICATION_INCOMPLETE",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());assertWrittenUnfinalized(revision);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE test_result_id=? AND capture_source='omr' AND points_earned<>0",Integer.class,resultId()));
    }
    @Test void writtenFinalizationRejectsRubricChangesAfterTeacherAcceptance()throws Exception {
        var c=completeWrittenResult(false,false,false);long revision=reader().result(teacher,request.resultUuid()).revision();
        jdbc.update("UPDATE rubric_criteria SET criterion_name='Changed scoring label' WHERE rubric_id=?",c.refs().rubric());
        assertEquals("WRITTEN_REFERENCE_STALE",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());assertWrittenUnfinalized(revision);
    }
    @Test void writtenFinalizationRejectsChangedAuditPointsAndFeedback()throws Exception {
        var c=completeWrittenResult(false,false,false);long revision=reader().result(teacher,request.resultUuid()).revision();
        jdbc.update("UPDATE student_answers SET points_earned=1.75 WHERE test_result_id=? AND question_id=?",resultId(),c.refs().manual());
        assertEquals("WRITTEN_AUDIT_MISMATCH",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());
        jdbc.update("UPDATE student_answers SET points_earned=1.50,teacher_feedback='Changed after verification' WHERE test_result_id=? AND question_id=?",resultId(),c.refs().manual());
        assertEquals("WRITTEN_AUDIT_MISMATCH",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());assertWrittenUnfinalized(revision);
    }
    @Test void writtenFinalizationRejectsModifiedCriterionScore()throws Exception {
        var c=completeWrittenResult(false,false,false);long revision=reader().result(teacher,request.resultUuid()).revision();
        jdbc.update("UPDATE answer_rubric_scores s JOIN student_answers a ON a.student_answer_id=s.student_answer_id SET s.points_awarded=2 WHERE a.test_result_id=? AND a.question_id=?",resultId(),c.refs().essay());
        assertEquals("WRITTEN_AUDIT_MISMATCH",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());assertWrittenUnfinalized(revision);
    }
    @Test void writtenFinalizationRejectsPurgedEvidenceAndDoesNotPublishAnything()throws Exception {
        var c=completeWrittenResult(false,false,false);long revision=reader().result(teacher,request.resultUuid()).revision();
        jdbc.update("UPDATE answer_attachments SET purge_status='purged',last_purge_attempt_at=CURRENT_TIMESTAMP,purged_at=CURRENT_TIMESTAMP,purge_reason='Synthetic purge' WHERE attachment_uuid=?",c.attachment());
        assertEquals("ATTACHMENT_LINEAGE_INVALID",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());assertWrittenUnfinalized(revision);
    }
    @Test void writtenFinalizationRejectsCorruptAndMissingCrops()throws Exception {
        var c=completeWrittenResult(false,false,false);String key=jdbc.queryForObject("SELECT storage_key FROM answer_attachments WHERE attachment_uuid=?",String.class,c.attachment());
        java.nio.file.Files.write(directory.resolve(key),new byte[]{0});assertEquals("SCAN_EVIDENCE_INTEGRITY_FAILED",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());
        java.nio.file.Files.delete(directory.resolve(key));assertEquals("SCAN_EVIDENCE_NOT_FOUND",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());
    }
    @Test void writtenFinalizationRejectsDetachedEvidenceAndForgedReferenceSnapshot()throws Exception {
        var c=completeWrittenResult(false,false,false);long answer=jdbc.queryForObject("SELECT student_answer_id FROM answer_attachments WHERE attachment_uuid=?",Long.class,c.attachment());
        jdbc.update("UPDATE answer_attachments SET student_answer_id=NULL WHERE attachment_uuid=?",c.attachment());assertEquals("WRITTEN_AUDIT_MISMATCH",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());
        jdbc.update("UPDATE answer_attachments SET student_answer_id=? WHERE attachment_uuid=?",answer,c.attachment());
        jdbc.update("UPDATE mobile_written_references r JOIN answer_verifications v ON v.mobile_sync_item_id=r.sync_item_id SET r.reference_json='{}' WHERE v.student_answer_id=?",answer);
        assertEquals("WRITTEN_AUDIT_MISMATCH",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());
    }
    @Test void writtenFinalizationRejectsMissingObjectiveKeyInMixedAssessment()throws Exception {
        completeWrittenResult(false,false,false);jdbc.update("DELETE k FROM answer_keys k JOIN questions q ON q.question_id=k.question_id JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id WHERE d.assignment_uuid=? AND k.answer_key_type='option'",request.assignmentUuid());
        assertEquals("ANSWER_KEY_MISSING",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());
    }
    @Test void writtenFinalizationReplayIsFrozenAfterRubricChangesAndPurge()throws Exception {
        var c=completeWrittenResult(false,false,false);var first=finalizeScore(resultId());
        jdbc.update("UPDATE rubric_criteria SET maximum_points=maximum_points+1 WHERE rubric_id=?",c.refs().rubric());
        jdbc.update("UPDATE answer_attachments SET purge_status='purged',last_purge_attempt_at=CURRENT_TIMESTAMP,purged_at=CURRENT_TIMESTAMP,purge_reason='Synthetic later purge' WHERE attachment_uuid=?",c.attachment());
        var replay=finalizeScore(resultId());assertFalse(replay.scoreChanged());assertEquals(first.totalScore(),replay.totalScore());assertEquals(first.scoredAt(),replay.scoredAt());
        assertEquals("ready",reader().analytics(teacher,request.resultUuid()).status());
        var samples=Path.of("target/mobile-written-finalization-samples");java.nio.file.Files.createDirectories(samples);mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValue(samples.resolve("replayed.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(replay));
    }
    @Test void writtenFinalizationConcurrentRetriesProduceOneOfficialReceipt()throws Exception {
        completeWrittenResult(false,false,false);var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);long result=resultId();
        try{var a=executor.submit(()->{start.await();return finalizeScore(result);});var b=executor.submit(()->{start.await();return finalizeScore(result);});start.countDown();
            var x=a.get(25,TimeUnit.SECONDS);var y=b.get(25,TimeUnit.SECONDS);assertEquals(x.totalScore(),y.totalScore());assertNotEquals(x.scoreChanged(),y.scoreChanged());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_finalizations WHERE test_result_id=?",Integer.class,result));
        }finally{executor.shutdownNow();}
    }
    @Test void writtenFinalizationRollbackAndLostAcknowledgementAreRetrySafe()throws Exception {
        completeWrittenResult(false,false,false);long revision=reader().result(teacher,request.resultUuid()).revision();
        var before=new DataSourceTransactionManager(source){@Override protected void doCommit(DefaultTransactionStatus status){throw new TransactionSystemException("Injected before commit");}};before.setRollbackOnCommitFailure(true);
        assertThrows(TransactionSystemException.class,()->new org.springframework.transaction.support.TransactionTemplate(before).execute(s->scorer(true).finalizeResult(teacher,resultId(),null)));assertWrittenUnfinalized(revision);
        var after=new DataSourceTransactionManager(source){@Override protected void doCommit(DefaultTransactionStatus status){super.doCommit(status);throw new TransactionSystemException("Lost acknowledgement");}};
        assertThrows(TransactionSystemException.class,()->new org.springframework.transaction.support.TransactionTemplate(after).execute(s->scorer(true).finalizeResult(teacher,resultId(),null)));
        assertFalse(finalizeScore(resultId()).scoreChanged());assertEquals(revision+1,reader().result(teacher,request.resultUuid()).revision());
    }
    @Test void writtenOfficialReplayRejectsWrongIdentityAndMalformedReceipt()throws Exception {
        completeWrittenResult(false,false,false);finalizeScore(resultId());String original=jdbc.queryForObject("SELECT response_json FROM mobile_result_finalizations WHERE test_result_id=?",String.class,resultId());
        var altered=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(original);altered.put("studentId",9007199254740990L);
        jdbc.update("UPDATE mobile_result_finalizations SET response_json=? WHERE test_result_id=?",mapper.writeValueAsString(altered),resultId());
        assertEquals("FINALIZATION_STATE_CONFLICT",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());
        jdbc.update("UPDATE mobile_result_finalizations SET response_json='{}' WHERE test_result_id=?",resultId());
        assertEquals("FINALIZATION_STATE_CONFLICT",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());
    }

    @Test void writtenFinalizationKeepsReferenceLocksUntilOfficialScoreCommits()throws Exception {
        completeWrittenResult(false,false,false);var executor=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var repository=new com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository(jdbc){
            @Override public java.util.List<com.capstone.assessment.v3.mobile.dto.V3EvaluationReference.Criterion> criteria(long rubric,boolean lock){
                var rows=super.criteria(rubric,lock);if(lock){executor.submit(()->{started.countDown();try{jdbc.update("UPDATE rubric_criteria SET criterion_name='Edited after finalization' WHERE rubric_id=?",rubric);}finally{finished.countDown();}});
                    try{assertTrue(started.await(2,TimeUnit.SECONDS));assertFalse(finished.await(200,TimeUnit.MILLISECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}
                return rows;
            }};
        try{var reference=new V3EvaluationReferenceService(repository,new DataSourceTransactionManager(source),true);
            var score=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(source)).execute(s->scorer(true,reference).finalizeResult(teacher,resultId(),null));
            assertEquals(new java.math.BigDecimal("23.50"),score.totalScore());assertTrue(finished.await(5,TimeUnit.SECONDS));assertFalse(finalizeScore(resultId()).scoreChanged());
        }finally{executor.shutdownNow();}
    }

    private V3ResultReopenService reopener(org.springframework.transaction.PlatformTransactionManager manager) {
        var finals=new com.capstone.assessment.v3.mobile.repository.V3MobileFinalizationRepository(jdbc);
        var owners=new V3ScanUploadLedgerRepository(jdbc);
        return new V3ResultReopenService(new com.capstone.assessment.v3.mobile.repository.V3ResultReopenRepository(jdbc),owners,
                new com.capstone.assessment.v3.scoring.repository.V3ScoringRepository(jdbc),finals,
                new V3MobileFinalizationService(finals,owners,storage,mapper,true,writtenFinalizer(evaluationReader())),mapper,validators.getValidator(),manager,true);
    }
    private com.capstone.assessment.v3.mobile.dto.V3ReopenRequest reopenRequest() {
        var r=reader().result(teacher,request.resultUuid());
        return new com.capstone.assessment.v3.mobile.dto.V3ReopenRequest("3.0",uuid(),uuid(),r.revision(),(long)r.scoreVersion(),"TEACHER_REVIEW_REQUEST","Review the accepted evaluation.");
    }
    private com.capstone.assessment.v3.mobile.dto.V3LifecycleAck reopen(com.capstone.assessment.v3.mobile.dto.V3ReopenRequest r) {
        return reopener(new DataSourceTransactionManager(source)).reopen(teacher,request.resultUuid(),r);
    }
    @Test void reopenPreservesOfficialReceiptAndAnswersAndReturnsStaleReadback()throws Exception {
        long id=verifiedResult(false);var official=finalizeScore(id);var r=reopenRequest();
        String old=jdbc.queryForObject("SELECT response_json FROM mobile_result_finalizations WHERE test_result_id=?",String.class,id);
        var answers=jdbc.queryForList("SELECT * FROM student_answers WHERE test_result_id=? ORDER BY student_answer_id",id);
        var ack=reopen(r);assertEquals("created",ack.disposition());assertEquals(r.expectedRevision()+1,ack.revision());assertEquals(official.scoreVersion(),ack.scoreVersion());
        assertEquals(old,jdbc.queryForObject("SELECT official_score_json FROM mobile_result_reopens WHERE test_result_id=?",String.class,id));
        assertEquals(old,jdbc.queryForObject("SELECT response_json FROM mobile_result_finalizations WHERE test_result_id=?",String.class,id));
        assertEquals(answers,jdbc.queryForList("SELECT * FROM student_answers WHERE test_result_id=? ORDER BY student_answer_id",id));
        var result=reader().result(teacher,request.resultUuid());var analytics=reader().analytics(teacher,request.resultUuid());var sync=reader().sync(teacher,r.syncUuid());
        assertEquals("pending_verification",result.resultStatus());assertNull(result.officialScore());assertTrue(result.pendingReasons().contains("RESULT_REOPENED"));
        assertEquals("stale",analytics.status());assertEquals("RESULT_REOPENED",analytics.reasonCode());assertNull(analytics.metrics());
        assertEquals("success",sync.syncStatus());assertEquals(1,sync.items().size());assertTrue(sync.items().get(0).pageOutcomes().isEmpty());
        assertEquals("FINALIZATION_STATE_CONFLICT",assertThrows(V3AuthException.class,()->finalizeScore(id)).getCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='V3_MOBILE_RESULT_REOPENED' AND entity_id=?",Integer.class,request.resultUuid()));
        var samples=Path.of("target/mobile-reopen-samples");java.nio.file.Files.createDirectories(samples);
        var writer=mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        writer.writeValue(samples.resolve("request.json").toFile(),r);
        writer.writeValue(samples.resolve("created.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(ack));
        writer.writeValue(samples.resolve("replayed.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(reopen(r)));
        writer.writeValue(samples.resolve("result.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(result));
        writer.writeValue(samples.resolve("analytics.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(analytics));
        writer.writeValue(samples.resolve("sync.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(sync));
    }
    @Test void reopenWrittenResultPreservesRubricAuditsAfterReferenceEdit()throws Exception {
        var c=completeWrittenResult(false,false,false);finalizeScore(resultId());var r=reopenRequest();
        var rows=jdbc.queryForList("SELECT s.* FROM answer_rubric_scores s JOIN student_answers a ON a.student_answer_id=s.student_answer_id WHERE a.test_result_id=? ORDER BY s.answer_rubric_score_id",resultId());
        jdbc.update("UPDATE rubric_criteria SET criterion_name='Changed rubric' WHERE rubric_id=?",c.refs().rubric());
        assertEquals("created",reopen(r).disposition());
        assertEquals(rows,jdbc.queryForList("SELECT s.* FROM answer_rubric_scores s JOIN student_answers a ON a.student_answer_id=s.student_answer_id WHERE a.test_result_id=? ORDER BY s.answer_rubric_score_id",resultId()));
        assertEquals("success",reader().sync(teacher,c.batch().syncUuid()).syncStatus());
    }
    @Test void reopenRetryIsHistoricalAndPayloadChangesConflict() {
        finalizeScore(verifiedResult(false));var r=reopenRequest();var first=reopen(r);var replay=reopen(r);
        assertEquals(first.replay(),replay);
        jdbc.update("UPDATE test_results SET result_status='superseded',mobile_revision=mobile_revision+1 WHERE result_uuid=?",request.resultUuid());
        assertEquals(first.replay(),reopen(r));
        var changed=new com.capstone.assessment.v3.mobile.dto.V3ReopenRequest("3.0",r.syncUuid(),r.operationUuid(),r.expectedRevision(),r.expectedScoreVersion(),r.reasonCode(),"Different comment");
        assertEquals("REOPEN_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->reopen(changed)).getCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_reopens WHERE test_result_id=?",Integer.class,resultId()));
    }
    @Test void reopenRejectsStaleRevisionAndScoreVersionWithoutAudit() {
        finalizeScore(verifiedResult(false));var r=reopenRequest();
        for(var stale:List.of(new com.capstone.assessment.v3.mobile.dto.V3ReopenRequest("3.0",r.syncUuid(),r.operationUuid(),r.expectedRevision()-1,r.expectedScoreVersion(),r.reasonCode(),null),
                new com.capstone.assessment.v3.mobile.dto.V3ReopenRequest("3.0",r.syncUuid(),r.operationUuid(),r.expectedRevision(),r.expectedScoreVersion()+1,r.reasonCode(),null)))
            assertEquals("REVISION_CONFLICT",assertThrows(V3AuthException.class,()->reopen(stale)).getCode());
        assertEquals("finalized",reader().result(teacher,request.resultUuid()).resultStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_reopens WHERE test_result_id=?",Integer.class,resultId()));
    }
    @Test void reopenRejectsUnfinalizedAndSupersededResults() {
        long id=verifiedResult(false);var r=reopenRequest();assertEquals("RESULT_NOT_FINALIZED",assertThrows(V3AuthException.class,()->reopen(r)).getCode());
        finalizeScore(id);var f=reopenRequest();jdbc.update("UPDATE test_results SET result_status='superseded' WHERE test_result_id=?",id);
        assertEquals("RESULT_NOT_FINALIZED",assertThrows(V3AuthException.class,()->reopen(f)).getCode());
    }
    @Test void reopenRejectsUnknownOrOtherSchoolAndInactiveTeacher() {
        finalizeScore(verifiedResult(false));var r=reopenRequest();var service=reopener(new DataSourceTransactionManager(source));
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->service.reopen(teacher,uuid(),r)).getCode());
        var other=new V3AuthenticatedUser(teacher.userId(),"OTHER","","teacher","active","");
        assertEquals("RESULT_ACCESS_DENIED",assertThrows(V3AuthException.class,()->service.reopen(other,request.resultUuid(),r)).getCode());
        reopen(r);jdbc.update("UPDATE users SET status_id=(SELECT status_id FROM statuses WHERE status_name='inactive') WHERE user_id=?",teacher.userId());
        assertEquals("RESULT_ACCESS_DENIED",assertThrows(V3AuthException.class,()->reopen(r)).getCode());
    }
    @Test void reopenReauthorizesAssignmentOwnershipOnRetry() {
        finalizeScore(verifiedResult(false));var r=reopenRequest();reopen(r);
        long replacement=insert("user_id","""
                INSERT INTO users(school_id,address_id,gender_id,role_id,status_id,first_name,last_name,email,contact_number,password_hash)
                SELECT school_id,address_id,gender_id,role_id,status_id,'Replacement','Teacher',?,?,'TEST_ONLY' FROM users WHERE user_id=?
                """,uuid()+"@example.invalid",uuid().substring(0,12),teacher.userId());
        jdbc.update("UPDATE class_assignments a JOIN test_assignments d ON d.class_assignment_id=a.class_assignment_id SET a.user_id=? WHERE d.assignment_uuid=?",replacement,request.assignmentUuid());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->reopen(r)).getCode());
        var other=new V3AuthenticatedUser(replacement,teacher.schoolId(),"","teacher","active","");
        assertEquals("REOPEN_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->reopener(new DataSourceTransactionManager(source)).reopen(other,request.resultUuid(),r)).getCode());
    }
    @Test void reopenRejectsUsedSyncAndSecondOperation() {
        finalizeScore(verifiedResult(false));var r=reopenRequest();
        var used=new com.capstone.assessment.v3.mobile.dto.V3ReopenRequest("3.0",request.syncUuid(),r.operationUuid(),r.expectedRevision(),r.expectedScoreVersion(),r.reasonCode(),null);
        assertEquals("SYNC_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->reopen(used)).getCode());
        reopen(r);var next=reopenRequest();assertEquals("RESULT_NOT_FINALIZED",assertThrows(V3AuthException.class,()->reopen(next)).getCode());
    }
    @Test void concurrentReopenRetriesCommitOneAuditAndReceipt()throws Exception {
        finalizeScore(verifiedResult(false));var r=reopenRequest();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=pool.submit(()->{start.await();return reopen(r);});var b=pool.submit(()->{start.await();return reopen(r);});start.countDown();
            var x=a.get(25,TimeUnit.SECONDS);var y=b.get(25,TimeUnit.SECONDS);assertNotEquals(x.disposition(),y.disposition());assertEquals(x.replay(),y.replay());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_reopens WHERE test_result_id=?",Integer.class,resultId()));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='V3_MOBILE_RESULT_REOPENED' AND entity_id=?",Integer.class,request.resultUuid()));
        }finally{pool.shutdownNow();}
    }
    @Test void reopenRollbackAndLostAcknowledgementAreRecoverable() {
        finalizeScore(verifiedResult(false));var r=reopenRequest();
        var before=new DataSourceTransactionManager(source){@Override protected void doCommit(DefaultTransactionStatus s){throw new TransactionSystemException("Before commit");}};before.setRollbackOnCommitFailure(true);
        assertEquals("REOPEN_PERSISTENCE_UNAVAILABLE",assertThrows(V3AuthException.class,()->reopener(before).reopen(teacher,request.resultUuid(),r)).getCode());
        assertEquals("finalized",reader().result(teacher,request.resultUuid()).resultStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM syncs WHERE sync_uuid=?",Integer.class,r.syncUuid()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='V3_MOBILE_RESULT_REOPENED' AND entity_id=?",Integer.class,request.resultUuid()));
        var after=new DataSourceTransactionManager(source){@Override protected void doCommit(DefaultTransactionStatus s){super.doCommit(s);throw new TransactionSystemException("Lost acknowledgement");}};
        assertEquals("REOPEN_PERSISTENCE_UNAVAILABLE",assertThrows(V3AuthException.class,()->reopener(after).reopen(teacher,request.resultUuid(),r)).getCode());
        assertEquals("replayed",reopen(r).disposition());assertEquals(r.expectedRevision()+1,reader().result(teacher,request.resultUuid()).revision());
    }
    @Test void reopenRejectsCorruptOfficialAndReopenReceipts()throws Exception {
        finalizeScore(verifiedResult(false));var r=reopenRequest();String json=jdbc.queryForObject("SELECT response_json FROM mobile_result_finalizations WHERE test_result_id=?",String.class,resultId());
        jdbc.update("UPDATE mobile_result_finalizations SET response_json='{}' WHERE test_result_id=?",resultId());
        assertEquals("FINALIZATION_STATE_CONFLICT",assertThrows(V3AuthException.class,()->reopen(r)).getCode());
        jdbc.update("UPDATE mobile_result_finalizations SET response_json=? WHERE test_result_id=?",json,resultId());reopen(r);
        jdbc.update("UPDATE mobile_result_reopens SET response_json='{}' WHERE operation_uuid=?",r.operationUuid());
        assertEquals("REOPEN_STATE_CONFLICT",assertThrows(V3AuthException.class,()->reopen(r)).getCode());
        assertEquals("READBACK_STATE_INCONSISTENT",assertThrows(V3AuthException.class,()->reader().sync(teacher,r.syncUuid())).getCode());
    }
    @Test void reopenSchemaRejectsInvalidRevisionReasonAndSnapshot() {
        finalizeScore(verifiedResult(false));var r=reopenRequest();reopen(r);
        for(String assignment:List.of("mobile_revision=previous_revision","reason_code=' '","official_score_json='not-json'"))
            assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE mobile_result_reopens SET "+assignment+" WHERE operation_uuid=?",r.operationUuid()));
    }
    @Test void concurrentDifferentReopenOperationsHaveOneWinner()throws Exception {
        finalizeScore(verifiedResult(false));var a=reopenRequest();var b=reopenRequest();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var futures=List.of(a,b).stream().map(r->pool.submit(()->{start.await();try{return reopen(r).disposition();}catch(V3AuthException e){return e.getCode();}})).toList();start.countDown();
            var outcomes=List.of(futures.get(0).get(25,TimeUnit.SECONDS),futures.get(1).get(25,TimeUnit.SECONDS));
            assertTrue(outcomes.contains("created"));assertTrue(outcomes.contains("RESULT_NOT_FINALIZED"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_reopens WHERE test_result_id=?",Integer.class,resultId()));
        }finally{pool.shutdownNow();}
    }
    @Test void reopenRequiresOfficialReceiptAndRejectsRevisionExhaustion() {
        finalizeScore(verifiedResult(false));var r=reopenRequest();
        jdbc.update("UPDATE test_results SET mobile_revision=9007199254740991 WHERE test_result_id=?",resultId());
        var exhausted=new com.capstone.assessment.v3.mobile.dto.V3ReopenRequest("3.0",r.syncUuid(),r.operationUuid(),9007199254740991L,r.expectedScoreVersion(),r.reasonCode(),null);
        assertEquals("RESULT_REVISION_EXHAUSTED",assertThrows(V3AuthException.class,()->reopen(exhausted)).getCode());
        jdbc.update("UPDATE test_results SET mobile_revision=? WHERE test_result_id=?",r.expectedRevision(),resultId());
        jdbc.update("DELETE FROM mobile_result_finalizations WHERE test_result_id=?",resultId());
        assertEquals("OFFICIAL_SCORE_UNAVAILABLE",assertThrows(V3AuthException.class,()->reopen(r)).getCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_reopens WHERE test_result_id=?",Integer.class,resultId()));
    }
    @Test void reopenHonorsAssessmentLifecycleForNewIntentButReplaysHistoricalReceipt() {
        finalizeScore(verifiedResult(false));var r=reopenRequest();
        jdbc.update("UPDATE test_assignments SET assignment_status='archived' WHERE assignment_uuid=?",request.assignmentUuid());
        assertEquals("ASSIGNMENT_ARCHIVED",assertThrows(V3AuthException.class,()->reopen(r)).getCode());
        jdbc.update("UPDATE test_assignments SET assignment_status='open' WHERE assignment_uuid=?",request.assignmentUuid());
        jdbc.update("UPDATE tests t JOIN test_assignments d ON d.test_id=t.test_id SET t.status='draft' WHERE d.assignment_uuid=?",request.assignmentUuid());
        assertEquals("ASSESSMENT_NOT_SCORABLE",assertThrows(V3AuthException.class,()->reopen(r)).getCode());
        jdbc.update("UPDATE tests t JOIN test_assignments d ON d.test_id=t.test_id SET t.status='active' WHERE d.assignment_uuid=?",request.assignmentUuid());
        reopen(r);jdbc.update("UPDATE test_assignments SET assignment_status='archived' WHERE assignment_uuid=?",request.assignmentUuid());
        assertEquals("replayed",reopen(r).disposition());
    }

    private V3ResultCorrectionService corrector(org.springframework.transaction.PlatformTransactionManager manager) {
        return corrector(manager,evaluationReader());
    }
    private V3ResultCorrectionService corrector(org.springframework.transaction.PlatformTransactionManager manager,V3EvaluationReferenceService reference) {
        return new V3ResultCorrectionService(new com.capstone.assessment.v3.mobile.repository.V3ResultCorrectionRepository(jdbc),new com.capstone.assessment.v3.mobile.repository.V3ResultReopenRepository(jdbc),
                new V3ScanUploadLedgerRepository(jdbc),new com.capstone.assessment.v3.scoring.repository.V3ScoringRepository(jdbc),scorer(true),
                new com.capstone.assessment.v3.mobile.repository.V3MobileFinalizationRepository(jdbc),new V3VerificationRepository(jdbc),new V3DetectionRepository(jdbc),reference,writtenVerifier(),attachments(),validators.getValidator(),mapper,manager,true);
    }
    private com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse correct(V3CorrectionRequest r){return corrector(new DataSourceTransactionManager(source)).correct(teacher,request.resultUuid(),r);}
    private V3CorrectionRequest correctionFixture(boolean mixed)throws Exception{return correctionFixture(mixed,false);}
    private V3CorrectionRequest correctionFixture(boolean mixed,boolean tf)throws Exception {
        WrittenCase written=mixed?completeWrittenResult(false,false,false):null;
        if(!mixed)verifiedResult(tf);finalizeScore(resultId());var reopening=reopenRequest();reopen(reopening);
        var answers=new java.util.ArrayList<V3CorrectionRequest.Answer>();
        for(var row:jdbc.queryForList("SELECT a.*,q.question_uuid,o.option_key,d.detection_uuid FROM student_answers a JOIN questions q ON q.question_id=a.question_id LEFT JOIN question_options o ON o.question_option_id=a.selected_question_option_id LEFT JOIN mobile_objective_verifications v ON v.student_answer_id=a.student_answer_id LEFT JOIN omr_detections d ON d.omr_detection_id=v.omr_detection_id WHERE a.test_result_id=? ORDER BY q.item_number",resultId())) {
            String answer=(String)row.get("answer_uuid"),question=(String)row.get("question_uuid");
            String region=jdbc.queryForObject("SELECT region_uuid FROM answer_sheet_regions WHERE question_id=? AND answer_sheet_page_id=(SELECT answer_sheet_page_id FROM scan_pages WHERE scan_page_uuid=?)",String.class,row.get("question_id"),request.scanPageUuid());
            V3CorrectionRequest.Evaluation evaluation;
            if(row.get("detection_uuid")!=null)evaluation=new V3CorrectionRequest.Objective((String)row.get("answer_status"),(String)row.get("detection_uuid"),(String)row.get("option_key"));
            else {var e=written.batch().items().get(0).answers().stream().filter(a->a.answerUuid().equals(answer)).findFirst().orElseThrow().evaluation();
                evaluation=e instanceof V3WrittenVerificationBatch.Manual m?new V3CorrectionRequest.Manual(m.answerStatus(),m.responseText(),m.attachmentUuids(),m.points()):
                        new V3CorrectionRequest.Rubric(e.answerStatus(),e.responseText(),e.attachmentUuids(),((V3WrittenVerificationBatch.Rubric)e).rubricId(),((V3WrittenVerificationBatch.Rubric)e).criterionScores());}
            answers.add(new V3CorrectionRequest.Answer(answer,uuid(),question,region,request.scanPageUuid(),evaluation,"Reviewed again",Instant.parse("2026-09-12T00:00:00Z")));
        }
        return correctionRequest(reopening.operationUuid(),answers);
    }
    private V3CorrectionRequest correctionRequest(String reopen,List<V3CorrectionRequest.Answer> answers){
        var result=reader().result(teacher,request.resultUuid());var ref=evaluationReader().get(teacher,request.assignmentUuid());
        return new V3CorrectionRequest("3.2",uuid(),uuid(),reopen,result.revision(),(long)result.scoreVersion(),ref.testVersionNumber(),ref.evaluationReferenceHash(),"TEACHER_CORRECTION","Review all answers",answers);
    }
    private V3CorrectionRequest withAnswers(V3CorrectionRequest r,List<V3CorrectionRequest.Answer> answers){return new V3CorrectionRequest(r.contractVersion(),r.syncUuid(),r.operationUuid(),r.reopenOperationUuid(),r.expectedRevision(),r.expectedScoreVersion(),r.testVersionNumber(),r.evaluationReferenceHash(),r.reasonCode(),r.comment(),answers);}
    private V3CorrectionRequest.Answer evaluation(V3CorrectionRequest.Answer a,V3CorrectionRequest.Evaluation e){return new V3CorrectionRequest.Answer(a.answerUuid(),a.verificationUuid(),a.questionUuid(),a.regionUuid(),a.scanPageUuid(),e,a.comment(),a.clientDecidedAt());}
    private void assertCorrectionPending(V3CorrectionRequest r){
        var result=reader().result(teacher,request.resultUuid());assertEquals("pending_verification",result.resultStatus());assertEquals(r.expectedRevision(),result.revision());assertEquals(r.expectedScoreVersion().intValue(),result.scoreVersion());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_corrections WHERE operation_uuid=?",Integer.class,r.operationUuid()));
    }
    @Test void correctionChangesObjectiveDecisionAndFinalizesNewVersionWithoutChangingDetection()throws Exception {
        var original=correctionFixture(false);var first=original.answers().get(0);var source=(V3CorrectionRequest.Objective)first.evaluation();assertEquals("blank",source.answerStatus());
        var rows=new java.util.ArrayList<>(original.answers());rows.set(0,evaluation(first,new V3CorrectionRequest.Objective("answered",source.detectionUuid(),"A")));var r=withAnswers(original,rows);
        var before=jdbc.queryForObject("SELECT total_score FROM test_results WHERE test_result_id=?",java.math.BigDecimal.class,resultId());
        var score=correct(r);assertEquals(2,score.scoreVersion());assertTrue(score.scoreChanged());assertTrue(score.totalScore().compareTo(before)>0);
        assertEquals("blank",jdbc.queryForObject("SELECT detection_status FROM omr_detections WHERE detection_uuid=?",String.class,source.detectionUuid()));
        assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_answer_corrections WHERE operation_uuid=?",Integer.class,r.operationUuid()));
        assertEquals(r.expectedRevision()+1,reader().result(teacher,request.resultUuid()).revision());assertEquals("ready",reader().analytics(teacher,request.resultUuid()).status());assertEquals("success",reader().sync(teacher,r.syncUuid()).syncStatus());
        assertEquals(score.totalScore(),finalizeScore(resultId()).totalScore());assertFalse(finalizeScore(resultId()).scoreChanged());
        var samples=Path.of("target/mobile-correction-samples");java.nio.file.Files.createDirectories(samples);var writer=mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        writer.writeValue(samples.resolve("request.json").toFile(),r);writer.writeValue(samples.resolve("created.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(score));
        writer.writeValue(samples.resolve("replayed.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(correct(r)));
        writer.writeValue(samples.resolve("result.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(reader().result(teacher,request.resultUuid())));
        writer.writeValue(samples.resolve("analytics.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(reader().analytics(teacher,request.resultUuid())));
        writer.writeValue(samples.resolve("sync.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(reader().sync(teacher,r.syncUuid())));
    }
    @Test void correctionWrittenRubricPreservesOldScoresAndAudits()throws Exception {
        var r=correctionFixture(true);var answers=new java.util.ArrayList<>(r.answers());
        for(int n=0;n<answers.size();n++)if(answers.get(n).evaluation() instanceof V3CorrectionRequest.Rubric rubric){
            var criteria=new java.util.ArrayList<>(rubric.criterionScores());var first=criteria.get(0);criteria.set(0,new V3WrittenVerificationBatch.CriterionScore(first.rubricCriterionId(),first.pointsAwarded().subtract(java.math.BigDecimal.ONE),"Corrected criterion"));
            answers.set(n,evaluation(answers.get(n),new V3CorrectionRequest.Rubric(rubric.answerStatus(),rubric.responseText(),rubric.attachmentUuids(),rubric.rubricId(),criteria)));}
        r=withAnswers(r,answers);var result=correct(r);assertEquals(new java.math.BigDecimal("22.50"),result.totalScore());assertEquals(2,result.scoreVersion());
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM answer_rubric_scores s JOIN student_answers a ON a.student_answer_id=s.student_answer_id WHERE a.test_result_id=?",Integer.class,resultId()));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM answer_verifications v JOIN student_answers a ON a.student_answer_id=v.student_answer_id WHERE a.test_result_id=?",Integer.class,resultId()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_reopens WHERE test_result_id=? AND score_version=1",Integer.class,resultId()));
        var samples=Path.of("target/mobile-correction-samples");java.nio.file.Files.createDirectories(samples);var writer=mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        writer.writeValue(samples.resolve("written-request.json").toFile(),r);writer.writeValue(samples.resolve("written-result.json").toFile(),com.capstone.assessment.common.response.ApiResponse.success(result));
    }
    @Test void correctionSupportsTrueFalseAndRejectsThirdOption()throws Exception {
        var r=correctionFixture(false,true);var rows=new java.util.ArrayList<>(r.answers());var first=rows.get(0);var d=(V3CorrectionRequest.Objective)first.evaluation();
        rows.set(0,evaluation(first,new V3CorrectionRequest.Objective("answered",d.detectionUuid(),"C")));var invalid=withAnswers(r,rows);
        assertEquals("VALIDATION_FAILED",assertThrows(V3AuthException.class,()->correct(invalid)).getCode());assertCorrectionPending(r);
        rows.set(0,evaluation(first,new V3CorrectionRequest.Objective("answered",d.detectionUuid(),"B")));assertEquals(2,correct(withAnswers(r,rows)).scoreVersion());
    }
    @Test void correctionRepeatedCyclesKeepHistoricalReceiptsAndAdvanceEqualScores()throws Exception {
        var r=correctionFixture(false);var first=correct(r);var reopen=reopenRequest();reopen(reopen);
        var rows=r.answers().stream().map(a->new V3CorrectionRequest.Answer(a.answerUuid(),uuid(),a.questionUuid(),a.regionUuid(),a.scanPageUuid(),a.evaluation(),"Another review",a.clientDecidedAt())).toList();
        var second=correct(correctionRequest(reopen.operationUuid(),rows));assertEquals(3,second.scoreVersion());assertEquals(first.totalScore(),second.totalScore());
        var replay=correct(r);assertEquals(2,replay.scoreVersion());assertFalse(replay.scoreChanged());assertEquals(first.scoredAt(),replay.scoredAt());
        assertEquals(3,reader().result(teacher,request.resultUuid()).scoreVersion());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_reopens WHERE test_result_id=?",Integer.class,resultId()));
    }
    @Test void correctionRejectsPartialCoverageAndChangedIdentity()throws Exception {
        var r=correctionFixture(false);var partial=withAnswers(r,r.answers().subList(0,9));assertEquals("CORRECTION_COVERAGE_INVALID",assertThrows(V3AuthException.class,()->correct(partial)).getCode());
        var a=r.answers().get(0);var rows=new java.util.ArrayList<>(r.answers());rows.set(0,new V3CorrectionRequest.Answer(uuid(),a.verificationUuid(),a.questionUuid(),a.regionUuid(),a.scanPageUuid(),a.evaluation(),null,a.clientDecidedAt()));
        var bad=withAnswers(r,rows);assertEquals("CORRECTION_COVERAGE_INVALID",assertThrows(V3AuthException.class,()->correct(bad)).getCode());assertCorrectionPending(r);
    }
    @Test void correctionRejectsStaleReferenceAndReopenRevision()throws Exception {
        var r=correctionFixture(false);
        var stale=new V3CorrectionRequest("3.2",r.syncUuid(),r.operationUuid(),r.reopenOperationUuid(),r.expectedRevision(),r.expectedScoreVersion(),r.testVersionNumber(),"0".repeat(64),r.reasonCode(),r.comment(),r.answers());
        assertEquals("EVALUATION_REFERENCE_STALE",assertThrows(V3AuthException.class,()->correct(stale)).getCode());
        var version=new V3CorrectionRequest("3.2",r.syncUuid(),r.operationUuid(),r.reopenOperationUuid(),r.expectedRevision()-1,r.expectedScoreVersion(),r.testVersionNumber(),r.evaluationReferenceHash(),r.reasonCode(),r.comment(),r.answers());
        assertEquals("REVISION_CONFLICT",assertThrows(V3AuthException.class,()->correct(version)).getCode());assertCorrectionPending(r);
    }
    @Test void correctionRejectsWrongOriginalDetectionAfterEarlierUpdates()throws Exception {
        var r=correctionFixture(false);var rows=new java.util.ArrayList<>(r.answers());var last=rows.get(9);var e=(V3CorrectionRequest.Objective)last.evaluation();
        rows.set(9,evaluation(last,new V3CorrectionRequest.Objective(e.answerStatus(),((V3CorrectionRequest.Objective)rows.get(0).evaluation()).detectionUuid(),e.selectedOption())));
        var bad=withAnswers(r,rows);assertEquals("DETECTION_NOT_FOUND",assertThrows(V3AuthException.class,()->correct(bad)).getCode());assertCorrectionPending(r);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE test_result_id=? AND score_version=2",Integer.class,resultId()));
    }
    @Test void correctionRejectsExcessiveRubricPointsWithoutPartialCommit()throws Exception {
        var r=correctionFixture(true);var rows=new java.util.ArrayList<>(r.answers());
        for(int n=0;n<rows.size();n++)if(rows.get(n).evaluation() instanceof V3CorrectionRequest.Rubric rb){var cs=new java.util.ArrayList<>(rb.criterionScores());var c=cs.get(0);cs.set(0,new V3WrittenVerificationBatch.CriterionScore(c.rubricCriterionId(),new java.math.BigDecimal("99"),null));rows.set(n,evaluation(rows.get(n),new V3CorrectionRequest.Rubric(rb.answerStatus(),rb.responseText(),rb.attachmentUuids(),rb.rubricId(),cs)));}
        var bad=withAnswers(r,rows);assertEquals("RUBRIC_SCORE_OUT_OF_RANGE",assertThrows(V3AuthException.class,()->correct(bad)).getCode());assertCorrectionPending(r);
    }
    @Test void correctionRequiresRetainedWrittenEvidence()throws Exception {
        var r=correctionFixture(true);String key=jdbc.queryForObject("SELECT o.storage_key FROM answer_attachments o JOIN student_answers a ON a.response_evidence_attachment_id=o.answer_attachment_id WHERE a.test_result_id=? LIMIT 1",String.class,resultId());
        java.nio.file.Files.delete(directory.resolve(key));assertEquals("SCAN_EVIDENCE_NOT_FOUND",assertThrows(V3AuthException.class,()->correct(r)).getCode());assertCorrectionPending(r);
    }
    @Test void correctionManualPointsAndTranscriptionAreVersioned()throws Exception {
        var r=correctionFixture(true);var rows=new java.util.ArrayList<>(r.answers());
        String changed=null;java.math.BigDecimal prior=null;
        for(int n=0;n<rows.size();n++)if(rows.get(n).evaluation() instanceof V3CorrectionRequest.Manual m && m.points().signum()>0){
            changed=rows.get(n).answerUuid();prior=m.points();
            rows.set(n,evaluation(rows.get(n),new V3CorrectionRequest.Manual("answered","Teacher corrected transcription",m.attachmentUuids(),m.points().subtract(java.math.BigDecimal.ONE))));break;
        }
        assertNotNull(changed);var score=correct(withAnswers(r,rows));assertEquals(new java.math.BigDecimal("22.50"),score.totalScore());
        var stored=jdbc.queryForMap("SELECT response_text,points_earned,score_version FROM student_answers WHERE answer_uuid=?",changed);
        assertEquals("Teacher corrected transcription",stored.get("response_text"));assertEquals(prior.subtract(java.math.BigDecimal.ONE),stored.get("points_earned"));assertEquals(2,((Number)stored.get("score_version")).intValue());
        String old=jdbc.queryForObject("SELECT c.before_json FROM mobile_answer_corrections c JOIN student_answers a ON a.student_answer_id=c.student_answer_id WHERE a.answer_uuid=?",String.class,changed);
        assertNotEquals("Teacher corrected transcription",mapper.readTree(old).get("answer").get("response_text").asText());
    }
    @Test void correctionCannotFinalizeWithoutOriginalCaptureBytes()throws Exception {
        var r=correctionFixture(false);String key=jdbc.queryForObject("SELECT storage_key FROM mobile_scan_uploads WHERE scan_page_uuid=?",String.class,request.scanPageUuid());
        java.nio.file.Files.delete(directory.resolve(key));
        assertEquals("SCAN_EVIDENCE_NOT_FOUND",assertThrows(V3AuthException.class,()->correct(r)).getCode());assertCorrectionPending(r);
    }
    @Test void correctionSameOperationRetriesAreConcurrentAndIdempotent()throws Exception {
        var r=correctionFixture(false);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=pool.submit(()->{start.await();return correct(r);});var b=pool.submit(()->{start.await();return correct(r);});start.countDown();
            var x=a.get(25,TimeUnit.SECONDS);var y=b.get(25,TimeUnit.SECONDS);assertNotEquals(x.scoreChanged(),y.scoreChanged());assertEquals(x.scoredAt(),y.scoredAt());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mobile_result_corrections WHERE test_result_id=?",Integer.class,resultId()));
        }finally{pool.shutdownNow();}
    }
    @Test void correctionRollbackAndLostAcknowledgementRecoverWithoutNewVersion()throws Exception {
        var r=correctionFixture(true);
        var before=new DataSourceTransactionManager(source){@Override protected void doCommit(DefaultTransactionStatus s){throw new TransactionSystemException("Before correction commit");}};before.setRollbackOnCommitFailure(true);
        assertEquals("CORRECTION_PERSISTENCE_UNAVAILABLE",assertThrows(V3AuthException.class,()->corrector(before).correct(teacher,request.resultUuid(),r)).getCode());assertCorrectionPending(r);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM answer_rubric_scores s JOIN student_answers a ON a.student_answer_id=s.student_answer_id WHERE a.test_result_id=?",Integer.class,resultId()));
        var after=new DataSourceTransactionManager(source){@Override protected void doCommit(DefaultTransactionStatus s){super.doCommit(s);throw new TransactionSystemException("Lost correction acknowledgement");}};
        assertEquals("CORRECTION_PERSISTENCE_UNAVAILABLE",assertThrows(V3AuthException.class,()->corrector(after).correct(teacher,request.resultUuid(),r)).getCode());assertFalse(correct(r).scoreChanged());assertEquals(2,reader().result(teacher,request.resultUuid()).scoreVersion());
    }
    @Test void correctionRejectsChangedRetryAndInactiveTeacher()throws Exception {
        var r=correctionFixture(false);correct(r);
        var changed=new V3CorrectionRequest("3.2",r.syncUuid(),r.operationUuid(),r.reopenOperationUuid(),r.expectedRevision(),r.expectedScoreVersion(),r.testVersionNumber(),r.evaluationReferenceHash(),r.reasonCode(),"Changed request",r.answers());
        assertEquals("CORRECTION_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->correct(changed)).getCode());
        jdbc.update("UPDATE users SET status_id=(SELECT status_id FROM statuses WHERE status_name='inactive') WHERE user_id=?",teacher.userId());
        assertEquals("RESULT_ACCESS_DENIED",assertThrows(V3AuthException.class,()->correct(r)).getCode());
    }
    @Test void correctionCannotConsumeForeignReopenOrCorruptOfficialSnapshot()throws Exception {
        var r=correctionFixture(false);var bad=new V3CorrectionRequest("3.2",r.syncUuid(),r.operationUuid(),uuid(),r.expectedRevision(),r.expectedScoreVersion(),r.testVersionNumber(),r.evaluationReferenceHash(),r.reasonCode(),null,r.answers());
        assertEquals("RESULT_NOT_REOPENED",assertThrows(V3AuthException.class,()->correct(bad)).getCode());
        jdbc.update("UPDATE mobile_result_reopens SET official_score_json='{}' WHERE operation_uuid=?",r.reopenOperationUuid());
        assertEquals("REOPEN_STATE_CONFLICT",assertThrows(V3AuthException.class,()->correct(r)).getCode());assertCorrectionPending(r);
    }
    @Test void correctionReferenceLocksRemainHeldThroughOfficialScoring()throws Exception {
        var r=correctionFixture(true);var pool=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var repo=new com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository(jdbc){@Override public List<com.capstone.assessment.v3.mobile.dto.V3EvaluationReference.Criterion> criteria(long rubric,boolean lock){
            var rows=super.criteria(rubric,lock);if(lock){pool.submit(()->{started.countDown();try{jdbc.update("UPDATE rubric_criteria SET criterion_name='Later edit' WHERE rubric_id=?",rubric);}finally{finished.countDown();}});
                try{assertTrue(started.await(2,TimeUnit.SECONDS));assertFalse(finished.await(150,TimeUnit.MILLISECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}return rows;}};
        try{var ref=new V3EvaluationReferenceService(repo,new DataSourceTransactionManager(source),true);assertEquals(2,corrector(new DataSourceTransactionManager(source),ref).correct(teacher,request.resultUuid(),r).scoreVersion());assertTrue(finished.await(5,TimeUnit.SECONDS));assertFalse(correct(r).scoreChanged());}finally{pool.shutdownNow();}
    }
    @Test void correctionSchemaProtectsScoreHistoryAndMalformedReplayIsRejected()throws Exception {
        var r=correctionFixture(false);correct(r);
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE mobile_result_corrections SET score_version=previous_score_version WHERE operation_uuid=?",r.operationUuid()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE mobile_answer_corrections SET before_json='invalid' WHERE operation_uuid=?",r.operationUuid()));
        jdbc.update("UPDATE mobile_result_corrections SET response_json='{}' WHERE operation_uuid=?",r.operationUuid());
        assertEquals("CORRECTION_STATE_CONFLICT",assertThrows(V3AuthException.class,()->correct(r)).getCode());
    }

    private V3ResultSupersedeService superseder(org.springframework.transaction.PlatformTransactionManager manager){
        return new V3ResultSupersedeService(new com.capstone.assessment.v3.mobile.repository.V3ResultSupersedeRepository(jdbc),
                new com.capstone.assessment.v3.mobile.repository.V3ResultReopenRepository(jdbc),new V3ScanUploadLedgerRepository(jdbc),
                new com.capstone.assessment.v3.scoring.repository.V3ScoringRepository(jdbc),new com.capstone.assessment.v3.mobile.repository.V3MobileFinalizationRepository(jdbc),
                new V3MobileFinalizationService(new com.capstone.assessment.v3.mobile.repository.V3MobileFinalizationRepository(jdbc),new V3ScanUploadLedgerRepository(jdbc),storage,mapper,true,writtenFinalizer(evaluationReader())),
                mapper,validators.getValidator(),manager,true);
    }
    private V3ResultSupersedeService superseder(){return superseder(new DataSourceTransactionManager(source));}
    private V3ScanPageUploadMetadata newCapture(boolean newResult){
        var r=request;return new V3ScanPageUploadMetadata("3.0",uuid(),newResult?uuid():r.resultUuid(),newResult?uuid():r.scanUuid(),uuid(),r.answerSheetUuid(),r.pageUuid(),r.assignmentUuid(),r.classListId(),1,newResult?1:r.captureNumber()+1,r.scannerVersion(),r.qrPayloadHash(),r.imageHash(),r.capturedAt());
    }
    private void requestRescan(){
        var input=new V3VerificationBatch("3.0",uuid(),uuid(),request.assignmentUuid(),List.of(new Item(request.resultUuid(),reader().result(teacher,request.resultUuid()).revision(),
                List.of(new PageDecision(uuid(),request.scanPageUuid(),"rescan_requested","BLUR","Retake the photo",Instant.now())),List.of())));
        assertEquals("success",verificationService(new DataSourceTransactionManager(source)).verify(teacher,input).items().get(0).status());
    }
    private void acceptCurrentCapture(){
        var detections=jdbc.queryForList("SELECT g.region_uuid,q.question_uuid FROM answer_sheet_regions g JOIN questions q ON q.question_id=g.question_id WHERE g.answer_sheet_page_id=(SELECT answer_sheet_page_id FROM scan_pages WHERE scan_page_uuid=?) ORDER BY q.item_number",request.scanPageUuid()).stream()
                .map(row->new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection(uuid(),(String)row.get("region_uuid"),(String)row.get("question_uuid"),"detected","A",new java.math.BigDecimal("0.98"))).toList();
        var batch=new com.capstone.assessment.v3.mobile.dto.V3DetectionBatch("3.0",uuid(),uuid(),detections);detectionService(new DataSourceTransactionManager(source)).upload(teacher,request.scanPageUuid(),batch);
        var answers=detections.stream().map(d->new Answer(uuid(),uuid(),d.questionUuid(),d.regionUuid(),request.scanPageUuid(),new ObjectiveEvaluation("objective",d.detectionUuid()),null,Instant.now())).toList();
        var input=new V3VerificationBatch("3.0",uuid(),uuid(),request.assignmentUuid(),List.of(new Item(request.resultUuid(),reader().result(teacher,request.resultUuid()).revision(),List.of(new PageDecision(uuid(),request.scanPageUuid(),"accepted",null,null,Instant.now())),answers)));
        assertEquals("success",verificationService(new DataSourceTransactionManager(source)).verify(teacher,input).items().get(0).status());
        jdbc.update("""
                INSERT INTO answer_keys(question_id,answer_key_type,correct_question_option_id,scoring_method)
                SELECT q.question_id,'option',o.question_option_id,'exact' FROM questions q JOIN test_parts p ON p.test_part_id=q.test_part_id
                JOIN test_assignments d ON d.test_id=p.test_id JOIN question_options o ON o.question_id=q.question_id AND o.option_key='A'
                WHERE d.assignment_uuid=? AND NOT EXISTS(SELECT 1 FROM answer_keys k WHERE k.question_id=q.question_id)
                """,request.assignmentUuid());
    }
    private String replacementFixture(){
        String old=request.resultUuid();request=newCapture(true);service.ingest(teacher,request,upload());acceptCurrentCapture();finalizeScore(resultId());return old;
    }
    private com.capstone.assessment.v3.mobile.dto.V3SupersedeRequest supersedeRequest(String old){
        var r=reader().result(teacher,old);return new com.capstone.assessment.v3.mobile.dto.V3SupersedeRequest("3.0",uuid(),uuid(),r.revision(),(long)r.scoreVersion(),"VERIFIED_RESCAN","New capture verified",request.resultUuid());
    }
    private void sample(String name,Object value)throws Exception{
        var folder=Path.of("target/mobile-supersede-samples");java.nio.file.Files.createDirectories(folder);
        mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValue(folder.resolve(name+".json").toFile(),value);
    }
    @Test void rescanRetainsOriginalEvidenceAndFinalizesOnlyTheReplacementPage()throws Exception{
        detectionFixture(false,10);var original=request;requestRescan();long revision=reader().result(teacher,request.resultUuid()).revision();request=newCapture(false);
        var uploaded=service.ingest(teacher,request,upload());assertEquals("created",uploaded.uploadStatus());assertEquals(revision+1,reader().result(teacher,request.resultUuid()).revision());
        assertEquals("superseded",jdbc.queryForObject("SELECT page_status FROM scan_pages WHERE scan_page_uuid=?",String.class,original.scanPageUuid()));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM answer_attachments a JOIN scan_pages p ON p.scan_page_id=a.scan_page_id JOIN scan_sessions s ON s.scan_session_id=p.scan_session_id WHERE s.scan_uuid=? AND a.attachment_type='original_page'",Integer.class,request.scanUuid()));
        acceptCurrentCapture();var official=finalizeScore(resultId());assertEquals(new java.math.BigDecimal("20.00"),official.totalScore());assertEquals("ready",reader().analytics(teacher,request.resultUuid()).status());
        assertEquals("captured",service.ingest(teacher,original,upload()).pageStatus());assertEquals("replayed",service.ingest(teacher,request,upload()).uploadStatus());
        assertEquals("success",reader().sync(teacher,original.syncUuid()).syncStatus());assertEquals("success",reader().sync(teacher,request.syncUuid()).syncStatus());
        sample("rescan-request",request);sample("rescan-response",com.capstone.assessment.common.response.ApiResponse.success(uploaded));sample("rescan-result",com.capstone.assessment.common.response.ApiResponse.success(reader().result(teacher,request.resultUuid())));
    }
    @Test void rescanRequiresTeacherDecisionAndExactNextCapture(){
        detectionFixture(false,10);var original=request;var next=newCapture(false);
        assertEquals("RESCAN_PREDECESSOR_CONFLICT",assertThrows(V3AuthException.class,()->service.ingest(teacher,next,upload())).getCode());requestRescan();
        var skipped=new V3ScanPageUploadMetadata("3.0",uuid(),next.resultUuid(),next.scanUuid(),uuid(),next.answerSheetUuid(),next.pageUuid(),next.assignmentUuid(),next.classListId(),1,3,next.scannerVersion(),next.qrPayloadHash(),next.imageHash(),next.capturedAt());
        assertEquals("RESCAN_PREDECESSOR_CONFLICT",assertThrows(V3AuthException.class,()->service.ingest(teacher,skipped,upload())).getCode());
        assertEquals("rescan_requested",jdbc.queryForObject("SELECT page_status FROM scan_pages WHERE scan_page_uuid=?",String.class,original.scanPageUuid()));
    }
    @Test void rescanRollbackAndConcurrentRetryPreserveOneSuccessor()throws Exception{
        detectionFixture(false,10);requestRescan();var old=request;request=newCapture(false);
        var manager=new DataSourceTransactionManager(source){int commits;@Override protected void doCommit(DefaultTransactionStatus s){if(++commits==3)throw new TransactionSystemException("Rescan before commit");super.doCommit(s);}};manager.setRollbackOnCommitFailure(true);
        assertThrows(V3AuthException.class,()->service(manager).ingest(teacher,request,upload()));
        assertEquals("rescan_requested",jdbc.queryForObject("SELECT page_status FROM scan_pages WHERE scan_page_uuid=?",String.class,old.scanPageUuid()));
        var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        try{var a=pool.submit(()->{go.await();return service.ingest(teacher,request,upload());});var b=pool.submit(()->{go.await();return service.ingest(teacher,request,upload());});go.countDown();var x=a.get(25,TimeUnit.SECONDS);var y=b.get(25,TimeUnit.SECONDS);assertEquals(x.backendScanPageId(),y.backendScanPageId());assertNotEquals(x.uploadStatus(),y.uploadStatus());}finally{pool.shutdownNow();}
    }
    @Test void rescanRejectsAcceptedOrOfficialCapture(){
        verifiedResult(false);var next=newCapture(false);assertThrows(V3AuthException.class,()->service.ingest(teacher,next,upload()));
        finalizeScore(resultId());assertEquals("RESULT_LOCKED",assertThrows(V3AuthException.class,()->service.ingest(teacher,next,upload())).getCode());
    }
    @Test void rescanRepeatedRequestsKeepOneCurrentPageAndRejectBrokenLineage(){
        detectionFixture(false,10);
        for(int n=2;n<=3;n++){requestRescan();request=newCapture(false);service.ingest(teacher,request,upload());}
        acceptCurrentCapture();jdbc.update("UPDATE scan_pages SET supersedes_scan_page_id=NULL WHERE scan_page_uuid=?",request.scanPageUuid());
        assertEquals("SCAN_VERIFICATION_INCOMPLETE",assertThrows(V3AuthException.class,()->finalizeScore(resultId())).getCode());
    }
    @Test void supersedePreservesScoresAndExposesDurableLinkAndStaleAnalytics()throws Exception{
        var score=finalizeScore(verifiedResult(false));String old=replacementFixture();var r=supersedeRequest(old);
        var ack=superseder().supersede(teacher,old,r);assertEquals("superseded",ack.resultStatus());assertEquals(request.resultUuid(),ack.replacementResultUuid());assertEquals(r.expectedRevision()+1,ack.revision());
        var current=reader().result(teacher,old);assertNull(current.officialScore());assertEquals("RESULT_SUPERSEDED",reader().analytics(teacher,old).reasonCode());assertEquals("ready",reader().analytics(teacher,request.resultUuid()).status());
        assertEquals(ack,superseder().supersession(teacher,old));assertEquals(ack.replay(),superseder().supersede(teacher,old,r));assertEquals("success",reader().sync(teacher,r.syncUuid()).syncStatus());
        assertEquals(score.totalScore(),jdbc.queryForObject("SELECT total_score FROM test_results WHERE result_uuid=?",java.math.BigDecimal.class,old));
        assertEquals(20,jdbc.queryForObject("SELECT COUNT(*) FROM student_answers WHERE test_result_id IN (SELECT test_result_id FROM test_results WHERE result_uuid IN (?,?))",Integer.class,old,request.resultUuid()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM scan_sessions WHERE scan_uuid=? AND supersedes_scan_session_id IS NOT NULL",Integer.class,request.scanUuid()));
        sample("request",r);sample("created",com.capstone.assessment.common.response.ApiResponse.success(ack));sample("replayed",com.capstone.assessment.common.response.ApiResponse.success(ack.replay()));sample("link",com.capstone.assessment.common.response.ApiResponse.success(superseder().supersession(teacher,old)));
        sample("result",com.capstone.assessment.common.response.ApiResponse.success(current));sample("analytics",com.capstone.assessment.common.response.ApiResponse.success(reader().analytics(teacher,old)));sample("replacement",com.capstone.assessment.common.response.ApiResponse.success(reader().result(teacher,request.resultUuid())));sample("sync",com.capstone.assessment.common.response.ApiResponse.success(reader().sync(teacher,r.syncUuid())));
    }
    @Test void supersedeSupportsAuditedReopenedSource(){
        finalizeScore(verifiedResult(false));reopen(reopenRequest());String old=replacementFixture();assertEquals("superseded",superseder().supersede(teacher,old,supersedeRequest(old)).resultStatus());
    }
    @Test void supersedeRejectsStaleRevisionSelfLinkAndReverseAttempt(){
        finalizeScore(verifiedResult(false));String old=replacementFixture();var r=supersedeRequest(old);
        var stale=new com.capstone.assessment.v3.mobile.dto.V3SupersedeRequest("3.0",r.syncUuid(),r.operationUuid(),r.expectedRevision()-1,r.expectedScoreVersion(),r.reasonCode(),null,r.replacementResultUuid());
        assertEquals("REVISION_CONFLICT",assertThrows(V3AuthException.class,()->superseder().supersede(teacher,old,stale)).getCode());
        assertEquals("VALIDATION_FAILED",assertThrows(V3AuthException.class,()->superseder().supersede(teacher,request.resultUuid(),r)).getCode());
        var reverse=new com.capstone.assessment.v3.mobile.dto.V3SupersedeRequest("3.0",uuid(),uuid(),4L,1L,"REVIEW",null,old);
        assertEquals("REPLACEMENT_CONTEXT_MISMATCH",assertThrows(V3AuthException.class,()->superseder().supersede(teacher,request.resultUuid(),reverse)).getCode());
    }
    @Test void supersedeRequiresFinalizedLatestReplacement(){
        finalizeScore(verifiedResult(false));String old=request.resultUuid();request=newCapture(true);service.ingest(teacher,request,upload());var r=supersedeRequest(old);
        assertEquals("REPLACEMENT_NOT_FINALIZED",assertThrows(V3AuthException.class,()->superseder().supersede(teacher,old,r)).getCode());acceptCurrentCapture();finalizeScore(resultId());
        var latest=newCapture(true);service.ingest(teacher,latest,upload());assertEquals("REPLACEMENT_NOT_CURRENT",assertThrows(V3AuthException.class,()->superseder().supersede(teacher,old,r)).getCode());
    }
    @Test void supersedeConcurrentRetriesAndChangedContent()throws Exception{
        finalizeScore(verifiedResult(false));String old=replacementFixture();var r=supersedeRequest(old);var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        try{var a=pool.submit(()->{go.await();return superseder().supersede(teacher,old,r);});var b=pool.submit(()->{go.await();return superseder().supersede(teacher,old,r);});go.countDown();var x=a.get(25,TimeUnit.SECONDS);var y=b.get(25,TimeUnit.SECONDS);assertEquals(x.acknowledgedAt(),y.acknowledgedAt());assertNotEquals(x.disposition(),y.disposition());}finally{pool.shutdownNow();}
        var changed=new com.capstone.assessment.v3.mobile.dto.V3SupersedeRequest("3.0",r.syncUuid(),r.operationUuid(),r.expectedRevision(),r.expectedScoreVersion(),r.reasonCode(),"Different reason detail",r.replacementResultUuid());
        assertEquals("SUPERSEDE_IDENTITY_CONFLICT",assertThrows(V3AuthException.class,()->superseder().supersede(teacher,old,changed)).getCode());
    }
    @Test void supersedeRollbackAndLostAcknowledgementAreRecoverable(){
        finalizeScore(verifiedResult(false));String old=replacementFixture();var r=supersedeRequest(old);
        var before=new DataSourceTransactionManager(source){@Override protected void doCommit(DefaultTransactionStatus s){throw new TransactionSystemException("Before link commit");}};before.setRollbackOnCommitFailure(true);
        assertEquals("SUPERSEDE_PERSISTENCE_UNAVAILABLE",assertThrows(V3AuthException.class,()->superseder(before).supersede(teacher,old,r)).getCode());assertEquals("finalized",reader().result(teacher,old).resultStatus());
        var after=new DataSourceTransactionManager(source){@Override protected void doCommit(DefaultTransactionStatus s){super.doCommit(s);throw new TransactionSystemException("Lost link acknowledgement");}};
        assertEquals("SUPERSEDE_PERSISTENCE_UNAVAILABLE",assertThrows(V3AuthException.class,()->superseder(after).supersede(teacher,old,r)).getCode());assertEquals("replayed",superseder().supersede(teacher,old,r).disposition());
    }
    @Test void supersedeChainReplaysHistoricalLinkAndNeverCycles(){
        finalizeScore(verifiedResult(false));String first=replacementFixture();var a=supersedeRequest(first);superseder().supersede(teacher,first,a);
        String second=replacementFixture();var b=supersedeRequest(second);superseder().supersede(teacher,second,b);
        assertEquals(second,superseder().supersede(teacher,first,a).replacementResultUuid());assertEquals(request.resultUuid(),superseder().supersession(teacher,second).replacementResultUuid());
        assertEquals("superseded",reader().result(teacher,first).resultStatus());assertEquals("superseded",reader().result(teacher,second).resultStatus());
    }
    @Test void supersedeRechecksOwnerAndRejectsCorruptReceipt(){
        finalizeScore(verifiedResult(false));String old=replacementFixture();var r=supersedeRequest(old);superseder().supersede(teacher,old,r);
        jdbc.update("UPDATE mobile_result_supersessions SET response_json='{}' WHERE operation_uuid=?",r.operationUuid());
        assertEquals("SUPERSEDE_STATE_CONFLICT",assertThrows(V3AuthException.class,()->superseder().supersession(teacher,old)).getCode());
        jdbc.update("UPDATE users SET status_id=(SELECT status_id FROM statuses WHERE status_name='inactive') WHERE user_id=?",teacher.userId());
        assertEquals("RESULT_ACCESS_DENIED",assertThrows(V3AuthException.class,()->superseder().supersede(teacher,old,r)).getCode());
    }

    @Test void supersedeReportsSelectOnlyOneLatestAttemptBeforeAndAfterLink(){
        var initial=finalizeScore(verifiedResult(false));String old=replacementFixture();var r=supersedeRequest(old);
        long assignment=jdbc.queryForObject("SELECT class_assignment_id FROM test_assignments WHERE test_assignment_id=?",Long.class,initial.testAssignmentId());
        var reports=new com.capstone.assessment.v3.report.repository.V3ReportRepository(jdbc);var scope=reports.findAssessmentScope(initial.testId(),assignment).orElseThrow();
        var before=reports.listLatestAssessmentResults(scope).stream().filter(x->x.classListId()==request.classListId()).toList();
        assertEquals(1,before.size());assertEquals(resultId(),before.get(0).testResultId());
        superseder().supersede(teacher,old,r);
        var after=reports.listLatestAssessmentResults(scope).stream().filter(x->x.classListId()==request.classListId()).toList();assertEquals(before,after);
    }
    @Test void supersedeRejectsMissingReplacementOriginalAndCrossSchoolReference()throws Exception{
        finalizeScore(verifiedResult(false));String old=replacementFixture();var r=supersedeRequest(old);
        String key=jdbc.queryForObject("SELECT storage_key FROM mobile_scan_uploads WHERE scan_page_uuid=?",String.class,request.scanPageUuid());java.nio.file.Files.delete(directory.resolve(key));
        assertEquals("SCAN_EVIDENCE_NOT_FOUND",assertThrows(V3AuthException.class,()->superseder().supersede(teacher,old,r)).getCode());assertEquals("finalized",reader().result(teacher,old).resultStatus());
        seed();assertEquals("RESOURCE_NOT_FOUND",assertThrows(V3AuthException.class,()->superseder().supersede(teacher,old,r)).getCode());
    }
    @Test void supersedeSchemaRejectsSelfLinksBadRevisionAndMalformedHistory(){
        finalizeScore(verifiedResult(false));String old=replacementFixture();var r=supersedeRequest(old);superseder().supersede(teacher,old,r);
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE mobile_result_supersessions SET replacement_result_id=test_result_id WHERE operation_uuid=?",r.operationUuid()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE mobile_result_supersessions SET mobile_revision=previous_revision WHERE operation_uuid=?",r.operationUuid()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE mobile_result_supersessions SET previous_score_json='invalid' WHERE operation_uuid=?",r.operationUuid()));
    }

    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="v3.release.http", matches="true")
    @Test void releaseProfileLiveHttpWorkflow() throws Exception {
        // Real embedded HTTP server + real security chain, on the dedicated synthetic database only.
        String database=System.getProperty("v3.scan.mariadb.database");
        var evidence=Path.of(System.getProperty("v3.release.evidence", "target"));
        directory=java.nio.file.Files.createDirectories(evidence.resolve("http-evidence"));
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var httpEvidence=mapper.createObjectNode();
        var stages=httpEvidence.putArray("stages");
        var client=java.net.http.HttpClient.newHttpClient();
        String password=uuid();
        jdbc.update("UPDATE users SET password_hash=?,email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),teacher.userId());
        String email=jdbc.queryForObject("SELECT email FROM users WHERE user_id=?",String.class,teacher.userId());
        var arguments=new java.util.ArrayList<String>(List.of(
                "--spring.profiles.active=v3,v3-mobile-release", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=jdbc:mysql://127.0.0.1:33317/"+database+"?useSSL=false&serverTimezone=UTC",
                "--spring.datasource.username=root", "--spring.datasource.password=scan_validation_only",
                "--app.v3.baseline.expected-database="+database,
                "--app.v3.mobile.release.http-enabled=true", "--app.v3.mobile.release.mode=development",
                "--app.v3.mobile.release.public-base-url=http://127.0.0.1:18082",
                "--app.v3.mobile.release.adb-reverse-enabled=true",
                "--app.v3.scan-evidence.storage-directory="+directory.toAbsolutePath(),
                "--app.v3.auth.cleanup-enabled=false", "--spring.main.banner-mode=off"));
        for(String feature:List.of("scan-recovery","finalization","readback","evaluation-reference","reopen","correction","supersede"))
            arguments.add("--app.v3.mobile."+feature+"-enabled=true");
        try(var app=new org.springframework.boot.SpringApplication(com.capstone.assessment.AssessmentApplication.class)
                .run(arguments.toArray(String[]::new))) {
            int port=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)app).getWebServer().getPort();
            String base="http://127.0.0.1:"+port;
            app.getBean(com.capstone.assessment.v3.mobile.config.V3MobileReleaseProperties.class)
                    .getRelease().setPublicBaseUrl(base);
            httpEvidence.put("transport","real HTTP on loopback; physical phone not tested");
            var ready=httpJson(client,base,"GET","/api/v3/system/mobile-release-readiness",null,null,200,stages);
            assertTrue(ready.path("data").path("backendReady").asBoolean());
            assertFalse(ready.path("data").path("fullyConnected").asBoolean());
            httpEvidence.set("readiness",ready);
            httpJson(client,base,"GET","/api/v3/auth/me",null,null,401,stages);
            var login=httpJson(client,base,"POST","/api/v3/auth/login",null,
                    java.util.Map.of("email",email,"password",password,"deviceIdentifier","prompt18-synthetic"),200,stages);
            String token=login.path("data").path("accessToken").asText();
            assertFalse(token.isBlank());
            httpJson(client,base,"GET","/api/v3/auth/me",token,null,200,stages);
            var refs=httpJson(client,base,"GET","/api/v3/mobile/reference-data",token,null,200,stages);
            assertTrue(refs.path("data").path("syncPolicy").path("scanPageUploadAvailable").asBoolean());
            var download=httpJson(client,base,"GET","/api/v3/mobile/download",token,null,200,stages);
            assertTrue(download.path("data").has("classAssignmentSchedules"));
            assertTrue(download.path("data").path("classAssignments").get(0).has("classStatus"));
            httpJson(client,base,"GET","/api/v3/mobile/test-assignments/"+request.assignmentUuid()+"/answer-sheets/"+request.answerSheetUuid()+"/manifest",token,null,200,stages);
            var cors=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v3/mobile/scan-pages"))
                    .header("Origin","http://localhost:5173").header("Access-Control-Request-Method","POST")
                    .header("Access-Control-Request-Headers","authorization,content-type")
                    .method("OPTIONS",java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,cors.statusCode());assertEquals("http://localhost:5173",cors.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
            stages.addObject().put("stage","frontend_cors_preflight").put("status",cors.statusCode());
            String boundary="smart-"+uuid();
            var body=new ByteArrayOutputStream();
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n"+mapper.writeValueAsString(request)+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"image\"; filename=\"scan.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(image);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            for(int expected:List.of(201,200)) {
                var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v3/mobile/scan-pages"))
                        .header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary)
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(expected,response.statusCode(),response.body());
                stages.addObject().put("stage","scan_upload"+(expected==200?"_replay":"")).put("status",response.statusCode());
            }
            // The helper seeds objective options and returns detections; the upload it repeats is an idempotent replay.
            var detections=detectionFixture(false,10);
            String detectionPath="/api/v3/mobile/scan-pages/"+request.scanPageUuid()+"/detections";
            httpJson(client,base,"POST",detectionPath,token,detections,201,stages);
            httpJson(client,base,"POST",detectionPath,token,detections,200,stages);
            jdbc.update("""
                    INSERT INTO answer_keys(question_id,answer_key_type,correct_question_option_id,scoring_method)
                    SELECT q.question_id,'option',o.question_option_id,'exact' FROM questions q
                    JOIN test_parts p ON p.test_part_id=q.test_part_id JOIN test_assignments d ON d.test_id=p.test_id
                    JOIN question_options o ON o.question_id=q.question_id AND o.option_key='A' WHERE d.assignment_uuid=?
                    """,request.assignmentUuid());
            var answers=detections.detections().stream().map(d->new Answer(uuid(),uuid(),d.questionUuid(),d.regionUuid(),request.scanPageUuid(),
                    new ObjectiveEvaluation("objective",d.detectionUuid()),"HTTP teacher review",Instant.now())).toList();
            var verify=new V3VerificationBatch("3.0",uuid(),uuid(),request.assignmentUuid(),List.of(new Item(request.resultUuid(),2L,
                    List.of(new PageDecision(uuid(),request.scanPageUuid(),"accepted",null,null,Instant.now())),answers)));
            var verified=httpJson(client,base,"POST","/api/v3/mobile/verification-batches",token,verify,200,stages);
            assertEquals("success",verified.path("data").path("items").get(0).path("status").asText());
            long result=resultId();
            var official=httpJson(client,base,"POST","/api/v3/scoring/results/"+result+"/finalize",token,null,200,stages);
            assertEquals(20,official.path("data").path("totalScore").asInt());
            httpJson(client,base,"POST","/api/v3/scoring/results/"+result+"/finalize",token,null,200,stages);
            httpJson(client,base,"GET","/api/v3/mobile/results/"+request.resultUuid(),token,null,200,stages);
            var analytics=httpJson(client,base,"GET","/api/v3/mobile/results/"+request.resultUuid()+"/analytics",token,null,200,stages);
            httpEvidence.set("analytics",analytics);
            httpJson(client,base,"GET","/api/v3/mobile/syncs/"+request.syncUuid(),token,null,200,stages);
            httpJson(client,base,"GET","/api/v3/reports/reference-data",token,null,200,stages);
            var reportScope=jdbc.queryForMap("SELECT test_id,class_assignment_id FROM test_assignments WHERE assignment_uuid=?",request.assignmentUuid());
            var report=httpJson(client,base,"GET","/api/v3/reports/assessment-results?testId="+reportScope.get("test_id")+"&classAssignmentId="+reportScope.get("class_assignment_id"),token,null,200,stages);
            assertEquals(result,report.path("data").path("rows").get(0).path("testResultId").asLong());
            assertEquals(20,report.path("data").path("rows").get(0).path("earnedPoints").asInt());
            httpEvidence.set("frontendReport",report);
            httpJson(client,base,"POST","/api/v3/auth/logout",token,null,200,stages);
            httpJson(client,base,"GET","/api/v3/auth/me",token,null,401,stages);
            httpEvidence.put("passed",true);
            mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("live-http-evidence.json").toFile(),httpEvidence);
            // Synthetic account only; local file is excluded from the shareable handoff and never logged.
            mapper.writeValue(evidence.resolve("local-test-account.json").toFile(),java.util.Map.of("email",email,"password",password));
        }
    }

    private com.fasterxml.jackson.databind.JsonNode httpJson(java.net.http.HttpClient client,String base,String method,String path,
            String token,Object body,int expected,com.fasterxml.jackson.databind.node.ArrayNode stages) throws Exception {
        var builder=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path)).timeout(java.time.Duration.ofSeconds(30));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        builder.header("Content-Type","application/json");
        builder.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        var response=client.send(builder.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(expected,response.statusCode(),method+" "+path+": "+response.body());
        stages.addObject().put("method",method).put("path",path).put("status",response.statusCode());
        return mapper.readTree(response.body());
    }

    private void seed() throws Exception {
        String school = "VAL" + UUID.randomUUID().toString().substring(0, 8);
        long address = insert("address_id", "INSERT INTO addresses(country_code) VALUES ('PH')");
        jdbc.update("INSERT INTO school_profiles(school_id,address_id,school_name) VALUES (?,?,'Synthetic recovery test')", school, address);
        long user = insert("user_id", """
                INSERT INTO users(school_id,address_id,gender_id,role_id,status_id,first_name,last_name,email,contact_number,password_hash)
                VALUES (?,?,1,2,3,'Synthetic','Teacher',?,?,'TEST_ONLY_NOT_A_LOGIN_HASH')
                """, school, address, school + "@example.invalid", school);
        teacher = new V3AuthenticatedUser(user, school, "", "teacher", "active", "test-session");
        long curriculum = insert("curriculum_id", "INSERT INTO curriculums(curriculum_name,version) VALUES (?, 'test')", school);
        long year = insert("academic_year_id", "INSERT INTO academic_years(school_id,curriculum_id,year_name,start_date,end_date,status) VALUES (?,?,'2026-2027','2026-01-01','2027-12-31','active')", school, curriculum);
        long grade = jdbc.queryForObject("SELECT MIN(grade_level_id) FROM grade_levels", Long.class);
        long subject = jdbc.queryForObject("SELECT MIN(subject_id) FROM subjects", Long.class);
        long section = insert("section_id", "INSERT INTO sections(school_id,grade_level_id,section_name) VALUES (?,?,'Synthetic')", school, grade);
        long cohort = insert("class_id", "INSERT INTO classes(academic_year_id,section_id) VALUES (?,?)", year, section);
        long assignment = insert("class_assignment_id", "INSERT INTO class_assignments(class_id,user_id,subject_id) VALUES (?,?,?)", cohort, user, subject);
        String lrn = String.format("%012d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000000000L));
        long student = insert("student_id", "INSERT INTO students(school_id,address_id,gender_id,student_lrn,first_name,last_name) VALUES (?,?,1,?,'Synthetic','Learner')", school, address, lrn);
        long membership = insert("class_list_id", "INSERT INTO class_lists(membership_uuid,class_id,student_id,academic_year_id) VALUES (?,?,?,?)", uuid(), cohort, student, year);
        long term = insert("term_period_id", "INSERT INTO term_periods(academic_year_id,term_name,term_order,start_at,end_at) VALUES (?,'Test term',1,'2026-01-01','2027-01-01')", year);
        long test = insert("test_id", "INSERT INTO tests(test_uuid,school_id,created_by_user_id,term_period_id,test_name,test_type,total_items,status) VALUES (?,?,?,?,'Synthetic capture','quiz',10,'active')", uuid(), school, user, term);
        String assignmentUuid = uuid();
        long delivery = insert("test_assignment_id", "INSERT INTO test_assignments(assignment_uuid,test_id,class_assignment_id,assigned_by_user_id,assignment_status) VALUES (?,?,?,?,'open')", assignmentUuid, test, assignment, user);
        long type = jdbc.queryForObject("SELECT question_type_id FROM question_types WHERE question_type_code='multiple_choice'", Long.class);
        long part = insert("test_part_id", "INSERT INTO test_parts(test_id,part_order,part_name,question_type_id,number_of_items,points_per_item) VALUES (?,1,'MC',?,10,2)", test, type);
        long template = jdbc.queryForObject("SELECT omr_template_id FROM omr_templates WHERE template_code='OMR-A4-10-MC-CTX-V2'", Long.class);
        long paper = jdbc.queryForObject("SELECT paper_size_id FROM paper_sizes WHERE paper_size_code='A4'", Long.class);
        String sheetUuid = uuid();
        long sheet = insert("answer_sheet_version_id", "INSERT INTO answer_sheet_versions(answer_sheet_uuid,test_assignment_id,paper_size_id,test_version_number,total_questions,total_pages,manifest_hash,generation_status,generated_by_user_id) VALUES (?,?,?,1,10,1,?,'ready',?)", sheetUuid, delivery, paper, "a".repeat(64), user);
        String pageUuid = uuid();
        String qr = "{\"v\":2,\"tv\":\"OMR-A4-10-MC-CTX-V2\",\"t\":" + test + ",\"q\":\"MC\",\"n\":10}";
        String qrHash = hash(qr.getBytes(StandardCharsets.UTF_8));
        long page = insert("answer_sheet_page_id", "INSERT INTO answer_sheet_pages(page_uuid,answer_sheet_version_id,omr_template_id,page_number,total_pages,qr_payload,qr_payload_hash,page_geometry_hash) VALUES (?,?,?,1,1,?,?,?)", pageUuid, sheet, template, qr, qrHash, "b".repeat(64));
        List<Long> regions = jdbc.queryForList("SELECT omr_template_region_id FROM omr_template_regions WHERE omr_template_id=? AND region_type='objective_bubbles' ORDER BY region_order", Long.class, template);
        for (int i = 1; i <= 10; i++) {
            long question = insert("question_id", "INSERT INTO questions(question_uuid,test_part_id,question_type_id,item_number,question_text,maximum_points) VALUES (?,?,?,?,'Synthetic item',2)", uuid(), part, type, i);
            jdbc.update("""
                    INSERT INTO answer_sheet_regions(region_uuid,answer_sheet_version_id,answer_sheet_page_id,
                        omr_template_region_id,question_id,test_part_id,question_type_id,global_item_number,part_item_number,
                        region_type,geometry_snapshot,geometry_hash)
                    SELECT ?,?,?,?,?,?,?,?,?,'objective_bubbles',geometry_definition,geometry_hash
                    FROM omr_template_regions WHERE omr_template_region_id=?
                    """, uuid(), sheet, page, regions.get(i - 1), question, part, type, i, i, regions.get(i - 1));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        image = bytes.toByteArray();
        request = new V3ScanPageUploadMetadata("3.0", uuid(), uuid(), uuid(), uuid(), sheetUuid, pageUuid,
                assignmentUuid, membership, 1, 1, "3.0.0", qrHash, hash(image), Instant.now().minusSeconds(60));
    }

    private long insert(String key, String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(sql, new String[]{key});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]); return statement; }, keys);
        return keys.getKey().longValue();
    }
    private V3ScanPageIngestionService service(DataSourceTransactionManager manager) {
        return new V3ScanPageIngestionService(new V3ScanPageRepository(jdbc), storage, validators.getValidator(), manager,
                new V3ScanUploadLedgerRepository(jdbc), mapper);
    }
    private MockMultipartFile upload() { return new MockMultipartFile("image", "scan.jpg", "image/jpeg", image); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
