package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.capstone.assessment.v3.mobile.repository.V3ScanPageRepository;
import com.capstone.assessment.v3.mobile.repository.V3ScanUploadLedgerRepository;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class V3ScanPageIngestionPersistenceTest {
    private static final V3AuthenticatedUser TEACHER = new V3AuthenticatedUser(42, "S1", "teacher@test", "teacher", "active", "session");
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private DriverManagerDataSource source;
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private ValidatorFactory validators;
    private V3OriginalScanImageStorage storage;
    private V3ScanPageIngestionService service;
    private V3ScanPageUploadMetadata request;
    private byte[] image;

    @BeforeEach
    void setup() throws Exception {
        source = new DriverManagerDataSource("jdbc:h2:mem:scan_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        keepAlive = source.getConnection();
        new ResourceDatabasePopulator(new ClassPathResource("contracts/v3/mobile/scan-ingestion-test-schema.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        validators = Validation.buildDefaultValidatorFactory();
        storage = new V3OriginalScanImageStorage(directory.resolve("evidence").toString());
        service = newService(new DataSourceTransactionManager(source));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "JPEG", bytes);
        image = bytes.toByteArray();
        String qr = "{\"v\":2,\"tv\":\"OMR-A4-10-MC-CTX-V2\",\"t\":200,\"q\":\"MC\",\"n\":10}";
        request = new V3ScanPageUploadMetadata("3.0", uuid(), uuid(), uuid(), uuid(), uuid(), uuid(), uuid(),
                41001, 1, 1, "3.0.0", hash(qr.getBytes(StandardCharsets.UTF_8)), hash(image), Instant.now().minusSeconds(60));
        jdbc.update("INSERT INTO statuses VALUES (1, 'active')");
        jdbc.update("INSERT INTO roles VALUES (1, 'teacher')");
        jdbc.update("INSERT INTO users(user_id,school_id,status_id) VALUES (42, 'S1', 1)");
        jdbc.update("INSERT INTO sections VALUES (2, 'S1')");
        jdbc.update("INSERT INTO classes VALUES (3, 2, 'active')");
        jdbc.update("INSERT INTO class_assignments VALUES (13, 3, 42, 'active')");
        jdbc.update("INSERT INTO students VALUES (77, 'S1', 'active')");
        jdbc.update("INSERT INTO class_lists VALUES (41001, 3, 77, 'enrolled')");
        jdbc.update("INSERT INTO tests VALUES (200, 'S1', 1, 10, 'active')");
        jdbc.update("INSERT INTO test_assignments VALUES (100, ?, 200, 13, 'open', NULL, NULL, FALSE)", request.assignmentUuid());
        jdbc.update("INSERT INTO question_types VALUES (1, 'multiple_choice')");
        jdbc.update("INSERT INTO test_parts VALUES (10, 200)");
        jdbc.update("INSERT INTO paper_sizes VALUES (1, 'A4')");
        jdbc.update("INSERT INTO omr_templates VALUES (500, 'OMR-A4-10-MC-CTX-V2', '2', '3.0.0', 'active')");
        jdbc.update("INSERT INTO answer_sheet_versions VALUES (300, ?, 100, 1, 1, 10, 1, 'ready')", request.answerSheetUuid());
        jdbc.update("INSERT INTO answer_sheet_pages VALUES (400, ?, 300, 500, 1, 1, ?, ?, 'ready')", request.pageUuid(), qr, request.qrPayloadHash());
        for (int i = 1; i <= 10; i++) {
            jdbc.update("INSERT INTO questions VALUES (?, 10, 1, 2.0)", i);
            jdbc.update("INSERT INTO answer_sheet_regions VALUES (?, 400, ?)", i, i);
        }
    }

    @AfterEach void close() throws Exception { if (keepAlive != null) keepAlive.close(); if (validators != null) validators.close(); }

    @Test void commitsResolvedIdsAndOriginalEvidenceWithoutScoringOrVerification() throws Exception {
        var response = service.ingest(TEACHER, request, upload());
        assertEquals("created", response.uploadStatus());
        assertEquals("captured", response.pageStatus());
        assertEquals(request.imageHash(), response.contentHash());
        assertEquals(400L, jdbc.queryForObject("SELECT answer_sheet_page_id FROM scan_pages WHERE scan_page_id = ?", Long.class, response.backendScanPageId()));
        assertEquals(100L, jdbc.queryForObject("SELECT test_assignment_id FROM test_results", Long.class));
        assertEquals(41001L, jdbc.queryForObject("SELECT class_list_id FROM test_results", Long.class));
        assertEquals("draft", jdbc.queryForObject("SELECT result_status FROM test_results", String.class));
        assertEquals(20, jdbc.queryForObject("SELECT max_score FROM test_results", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT total_score FROM test_results", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT items_evaluated FROM test_results", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT attempt_number FROM test_results", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT captured_page_count FROM scan_sessions", Integer.class));
        assertEquals("original_page", jdbc.queryForObject("SELECT attachment_type FROM answer_attachments", String.class));
        assertEquals("success", jdbc.queryForObject("SELECT sync_status FROM syncs", String.class));
        String key = jdbc.queryForObject("SELECT storage_key FROM answer_attachments", String.class);
        assertArrayEquals(image, Files.readAllBytes(directory.resolve("evidence").resolve(key)));
        assertFalse(key.contains("device"));
        assertEquals(1, count("test_result_scans"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE users SET school_id = 'OTHER'", "UPDATE students SET school_id = 'OTHER'",
            "UPDATE sections SET school_id = 'OTHER'", "UPDATE tests SET school_id = 'OTHER'",
            "UPDATE statuses SET status_name = 'inactive'", "UPDATE classes SET status = 'inactive'",
            "UPDATE class_assignments SET status = 'inactive'", "UPDATE class_lists SET enrollment_status = 'withdrawn'",
            "UPDATE students SET status = 'inactive'", "UPDATE answer_sheet_versions SET generation_status = 'retired'",
            "UPDATE answer_sheet_pages SET page_status = 'retired'"})
    void deniesUnavailableOrCrossSchoolContextBeforeStoringFiles(String mutation) {
        jdbc.update(mutation);
        assertCode("SCAN_CONTEXT_NOT_FOUND", request);
        assertNoRows();
        assertFalse(Files.exists(directory.resolve("evidence")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"assignmentUuid", "answerSheetUuid", "pageUuid"})
    void rejectsUnresolvedUuidInsteadOfTrustingClientIdentity(String field) throws Exception {
        assertCode("SCAN_CONTEXT_NOT_FOUND", change(field, uuid()));
        assertNoRows();
    }

    @Test void rejectsOtherTeacherAndOtherClassMembership() throws Exception {
        var otherTeacher = new V3AuthenticatedUser(43, "S1", "other@test", "teacher", "active", "session");
        assertEquals("SCAN_CONTEXT_NOT_FOUND", assertThrows(V3AuthException.class, () -> service.ingest(otherTeacher, request, upload())).getCode());
        assertCode("SCAN_CONTEXT_NOT_FOUND", change("classListId", 12345));
        assertNoRows();
    }

    @Test void rejectsNonTeacherBeforeDatabaseOrStorageWork() {
        assertEquals("TEACHER_REQUIRED", assertThrows(V3AuthException.class, () -> service.ingest(null, request, upload())).getCode());
        var principal = new V3AuthenticatedUser(42, "S1", "teacher@test", "principal", "active", "session");
        assertEquals("TEACHER_REQUIRED", assertThrows(V3AuthException.class, () -> service.ingest(principal, request, upload())).getCode());
        assertNoRows();
    }

    @Test void rejectsStaleSnapshotAndIncorrectPage() throws Exception {
        assertCode("ASSESSMENT_SNAPSHOT_MISMATCH", change("pageNumber", 2));
        jdbc.update("UPDATE tests SET version_number = 2");
        assertCode("ASSESSMENT_SNAPSHOT_MISMATCH", request);
        assertNoRows();
    }

    @Test void rejectsMissingQuestionMappings() {
        jdbc.update("UPDATE answer_sheet_regions SET question_id = 1");
        assertCode("ASSESSMENT_SNAPSHOT_MISMATCH", request);
        assertNoRows();
    }

    @Test void rejectsWrongClientQrAndCorruptStoredQr() throws Exception {
        assertCode("QR_PAYLOAD_MISMATCH", change("qrPayloadHash", "0".repeat(64)));
        jdbc.update("UPDATE answer_sheet_pages SET qr_payload = 'changed'");
        assertCode("QR_PAYLOAD_MISMATCH", request);
        assertNoRows();
    }

    @Test void rejectsOldScannerAndUnsupportedTemplate() throws Exception {
        assertCode("SCANNER_TEMPLATE_UNSUPPORTED", change("scannerVersion", "2.9.99"));
        jdbc.update("UPDATE omr_templates SET template_code = 'UNVALIDATED'");
        assertCode("SCANNER_TEMPLATE_UNSUPPORTED", request);
        assertNoRows();
    }

    @Test void rejectsClosedDeliveryAndFutureTimestamp() throws Exception {
        assertCode("CAPTURE_NOT_ALLOWED", change("capturedAt", Instant.now().plusSeconds(3600).toString()));
        jdbc.update("UPDATE test_assignments SET assignment_status = 'closed'");
        assertCode("CAPTURE_NOT_ALLOWED", request);
        assertNoRows();
    }

    @Test void rejectsHashMismatchAndRollsBackAllEarlierRecords() throws Exception {
        assertCode("IMAGE_HASH_MISMATCH", change("imageHash", "0".repeat(64)));
        assertNoRows();
        assertEquals(0, evidenceCount());
    }

    @Test void existingPageReplaysItsDurableReceiptWithoutReinsertion() {
        var first = service.ingest(TEACHER, request, upload());
        var replay = service.ingest(TEACHER, request, upload());
        assertEquals("replayed", replay.uploadStatus());
        assertEquals(first.backendScanPageId(), replay.backendScanPageId());
        assertEquals(1, count("test_results"));
        assertEquals(1, count("scan_pages"));
        assertEquals(1, count("answer_attachments"));
    }

    @Test void secondPageIdentityCannotOverwriteCurrentCapture() throws Exception {
        service.ingest(TEACHER, request, upload());
        assertCode("SCAN_IDENTITY_CONFLICT", change("scanPageUuid", uuid()));
        assertEquals(1, count("scan_pages"));
        assertEquals(1, evidenceCount());
    }

    @Test void finalizedResultAndRescansRemainLocked() throws Exception {
        service.ingest(TEACHER, request, upload());
        jdbc.update("UPDATE test_results SET result_status = 'finalized'");
        assertCode("RESULT_LOCKED", change("scanPageUuid", uuid()));
        var rescanJson = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(request);
        rescanJson.put("scanPageUuid", uuid());
        rescanJson.put("captureNumber", 2);
        assertCode("RESULT_LOCKED", mapper.treeToValue(rescanJson, V3ScanPageUploadMetadata.class));
        assertEquals(1, count("scan_pages"));
    }

    @Test void syncContextConflictRollsBackNewDraftAndSession() throws Exception {
        jdbc.update("INSERT INTO syncs(sync_uuid,user_id,test_assignment_id,direction,sync_status,payload_hash) VALUES (?,42,100,'download','pending',?)",
                request.syncUuid(), "0".repeat(64));
        assertCode("SYNC_IDENTITY_CONFLICT", request);
        assertEquals(0, count("test_results"));
        assertEquals(0, count("scan_sessions"));
        assertEquals(0, count("scan_pages"));
    }

    @Test void cannotReuseAnotherLearnersResultUuid() {
        jdbc.update("INSERT INTO students VALUES (88, 'S1', 'active')");
        jdbc.update("INSERT INTO class_lists VALUES (41002, 3, 88, 'enrolled')");
        jdbc.update("INSERT INTO test_results(result_uuid,test_assignment_id,class_list_id,attempt_number,total_score,max_score,items_evaluated,result_status) VALUES (?,100,41002,1,0,20,0,'draft')",
                request.resultUuid());
        assertCode("SCAN_IDENTITY_CONFLICT", request);
        assertEquals(1, count("test_results"));
        assertEquals(0, count("scan_pages"));
    }

    @Test void cannotAttachExistingScanToAnotherResult() throws Exception {
        service.ingest(TEACHER, request, upload());
        request = change("scanPageUuid", uuid());
        request = change("syncUuid", uuid());
        assertCode("SCAN_IDENTITY_CONFLICT", change("resultUuid", uuid()));
        assertEquals(1, count("test_results"));
        assertEquals(1, count("scan_sessions"));
    }

    @Test void allocatesAttemptFromStoredHistory() {
        jdbc.update("INSERT INTO test_results(result_uuid,test_assignment_id,class_list_id,attempt_number,total_score,max_score,items_evaluated,result_status) VALUES (?,100,41001,3,0,20,0,'finalized')",
                uuid());
        service.ingest(TEACHER, request, upload());
        assertEquals(4, jdbc.queryForObject("SELECT attempt_number FROM test_results WHERE result_uuid = ?",
                Integer.class, request.resultUuid()));
    }

    @Test void storageFailureRollsBackDatabaseRecords() throws Exception {
        Files.writeString(directory.resolve("evidence"), "not a directory");
        assertCode("SCAN_EVIDENCE_STORAGE_FAILED", request);
        assertNoRows();
    }

    @Test void commitFailureRetainsTrackedEvidenceAndRecoveryReusesIt() throws Exception {
        var failingManager = new DataSourceTransactionManager(source) {
            private int commits;
            @Override protected void doCommit(DefaultTransactionStatus status) {
                if (++commits == 3) throw new TransactionSystemException("Injected scan commit failure after durable intent");
                super.doCommit(status);
            }
        };
        failingManager.setRollbackOnCommitFailure(true);
        service = newService(failingManager);
        assertCode("SCAN_PERSISTENCE_FAILED", request);
        assertNoCaptureRows();
        assertEquals("pending", jdbc.queryForObject("SELECT upload_state FROM mobile_scan_uploads", String.class));
        assertEquals(1, evidenceCount());
        String attachment = jdbc.queryForObject("SELECT attachment_uuid FROM mobile_scan_uploads", String.class);
        service = newService(new DataSourceTransactionManager(source));
        var recovered = service.recover(request.scanPageUuid());
        assertEquals("created", recovered.uploadStatus());
        assertEquals(attachment, jdbc.queryForObject("SELECT attachment_uuid FROM answer_attachments", String.class));
        assertEquals("committed", jdbc.queryForObject("SELECT upload_state FROM mobile_scan_uploads", String.class));
        assertEquals(1, count("scan_pages"));
        assertEquals(1, evidenceCount());
    }

    @Test void exactReplaySurvivesFinalizationRetiredSheetAndClosedDelivery() {
        var first = service.ingest(TEACHER, request, upload());
        jdbc.update("UPDATE test_results SET result_status = 'finalized'");
        jdbc.update("UPDATE scan_pages SET page_status = 'accepted'");
        jdbc.update("UPDATE answer_sheet_versions SET generation_status = 'retired'");
        jdbc.update("UPDATE test_assignments SET assignment_status = 'closed'");
        var replay = service.ingest(TEACHER, request, upload());
        assertEquals("replayed", replay.uploadStatus());
        assertEquals("captured", replay.pageStatus());
        assertEquals(first.backendScanPageId(), replay.backendScanPageId());
    }

    @Test void lostIntentCommitAcknowledgementRetainsRecoverableStaging() throws Exception {
        var manager = new DataSourceTransactionManager(source) {
            int commits;
            @Override protected void doCommit(DefaultTransactionStatus status) {
                super.doCommit(status);
                if (++commits == 2) throw new TransactionSystemException("Lost intent commit acknowledgement");
            }
        };
        service = newService(manager);
        assertCode("SCAN_PERSISTENCE_FAILED", request);
        assertNoCaptureRows();
        assertEquals(1, count("mobile_scan_uploads"));
        assertEquals(0, evidenceCount());
        restartStorage();
        assertEquals("created", service.recover(request.scanPageUuid()).uploadStatus());
        assertEquals(1, count("scan_pages"));
    }

    @Test void replayChecksActualBytesEvenWhenClaimedHashIsUnchanged() {
        service.ingest(TEACHER, request, upload());
        byte[] changed = image.clone();
        changed[30] ^= 1;
        var incorrect = new MockMultipartFile("image", "scan.jpg", "image/jpeg", changed);
        assertEquals("IMAGE_HASH_MISMATCH", assertThrows(V3AuthException.class,
                () -> service.ingest(TEACHER, request, incorrect)).getCode());
        assertEquals(1, count("answer_attachments"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"syncUuid", "resultUuid", "scanUuid", "answerSheetUuid", "pageUuid", "assignmentUuid"})
    void changedUuidBindingsConflictEvenAfterSuccessfulUpload(String field) throws Exception {
        service.ingest(TEACHER, request, upload());
        assertCode("SCAN_IDENTITY_CONFLICT", change(field, uuid()));
        assertEquals(1, count("scan_pages"));
        assertEquals(1, count("mobile_scan_uploads"));
    }

    @Test void changedTimeHashAndScannerConflictBeforeNewStorage() throws Exception {
        service.ingest(TEACHER, request, upload());
        assertCode("SCAN_IDENTITY_CONFLICT", change("capturedAt", request.capturedAt().plusSeconds(1).toString()));
        assertCode("SCAN_IDENTITY_CONFLICT", change("imageHash", "0".repeat(64)));
        assertCode("SCAN_IDENTITY_CONFLICT", change("scannerVersion", "3.0.1"));
        assertEquals(1, evidenceCount());
    }

    @Test void concurrentRetriesAcrossServiceInstancesProduceOneReceipt() throws Exception {
        var otherService = newService(new DataSourceTransactionManager(source));
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return service.ingest(TEACHER, request, upload()); });
            var second = executor.submit(() -> { start.await(); return otherService.ingest(TEACHER, request, upload()); });
            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.backendScanPageId(), b.backendScanPageId());
            assertNotEquals(a.uploadStatus(), b.uploadStatus());
            assertEquals(1, count("scan_pages"));
            assertEquals(1, count("answer_attachments"));
            assertEquals(1, count("mobile_scan_uploads"));
            assertEquals(1, evidenceCount());
        } finally { executor.shutdownNow(); }
    }

    @Test void interruptedPublicationCanRecoverFromRetainedStagingAfterRestart() throws Exception {
        failPublication();
        assertCode("SCAN_EVIDENCE_STORAGE_FAILED", request);
        assertNoCaptureRows();
        assertEquals(1, count("mobile_scan_uploads"));
        restartStorage();
        var recovered = service.recover(request.scanPageUuid());
        assertEquals("created", recovered.uploadStatus());
        assertEquals(1, evidenceCount());
    }

    @Test void missingPendingBytesRequireRetryAndReuseReservedAttachment() throws Exception {
        failPublication();
        assertCode("SCAN_EVIDENCE_STORAGE_FAILED", request);
        String attachment = jdbc.queryForObject("SELECT attachment_uuid FROM mobile_scan_uploads", String.class);
        Files.delete(directory.resolve("evidence/.staging").resolve(attachment + ".part"));
        restartStorage();
        assertEquals("SCAN_EVIDENCE_NOT_FOUND", assertThrows(V3AuthException.class, () -> service.recover(request.scanPageUuid())).getCode());
        assertEquals("created", service.ingest(TEACHER, request, upload()).uploadStatus());
        assertEquals(attachment, jdbc.queryForObject("SELECT attachment_uuid FROM answer_attachments", String.class));
    }

    @Test void retryCanRestoreMissingCommittedBytesWithoutChangingIdentity() throws Exception {
        var first = service.ingest(TEACHER, request, upload());
        String key = jdbc.queryForObject("SELECT storage_key FROM answer_attachments", String.class);
        Files.delete(directory.resolve("evidence").resolve(key));
        var replay = service.ingest(TEACHER, request, upload());
        assertEquals("replayed", replay.uploadStatus());
        assertEquals(first.backendScanPageId(), replay.backendScanPageId());
        assertArrayEquals(image, Files.readAllBytes(directory.resolve("evidence").resolve(key)));
        assertEquals(1, count("answer_attachments"));
    }

    @Test void recoveryNeverOverwritesCorruptPublishedEvidence() throws Exception {
        service.ingest(TEACHER, request, upload());
        String key = jdbc.queryForObject("SELECT storage_key FROM answer_attachments", String.class);
        Path published = directory.resolve("evidence").resolve(key);
        byte[] corrupt = {1, 2, 3};
        Files.write(published, corrupt);
        assertCode("SCAN_EVIDENCE_INTEGRITY_FAILED", request);
        assertArrayEquals(corrupt, Files.readAllBytes(published));
    }

    @Test void pendingRecoveryChecksCurrentTeacherRole() {
        failPublication();
        assertCode("SCAN_EVIDENCE_STORAGE_FAILED", request);
        restartStorage();
        jdbc.update("UPDATE roles SET role_name = 'principal'");
        assertEquals("SCAN_CONTEXT_NOT_FOUND", assertThrows(V3AuthException.class, () -> service.recover(request.scanPageUuid())).getCode());
        assertNoCaptureRows();
    }

    @Test void firstPendingIntentReservesItsBatchBeforeAnyPageCommit() throws Exception {
        failPublication();
        assertCode("SCAN_EVIDENCE_STORAGE_FAILED", request);
        assertEquals(1, count("syncs"));
        assertEquals("in_progress", jdbc.queryForObject("SELECT sync_status FROM syncs", String.class));
        assertNoCaptureRows();
        restartStorage();
        request = change("scanPageUuid", uuid());
        assertCode("SYNC_IDENTITY_CONFLICT", change("resultUuid", uuid()));
        assertEquals(1, count("mobile_scan_uploads"));
    }

    @Test void recoveryBatchReportsFailuresThenCompletesOnLaterAttempt() {
        failPublication();
        assertCode("SCAN_EVIDENCE_STORAGE_FAILED", request);
        jdbc.update("UPDATE mobile_scan_uploads SET last_attempt_at = NULL");
        var failed = service.recoverPending(10);
        assertEquals("retry_required", failed.get(0).status());
        assertTrue(service.recoverPending(10).isEmpty(), "Database-clock cooldown prevents immediate worker retry loops");
        restartStorage();
        jdbc.update("UPDATE mobile_scan_uploads SET last_attempt_at = NULL");
        assertEquals("recovered", service.recoverPending(10).get(0).status());
        assertTrue(service.recoverPending(10).isEmpty());
    }

    private void failPublication() {
        storage = new V3OriginalScanImageStorage(directory.resolve("evidence").toString()) {
            @Override public OriginalImage recoverPublish(OriginalImage expected) {
                throw new V3AuthException("SCAN_EVIDENCE_STORAGE_FAILED", "Injected publication failure", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
        service = newService(new DataSourceTransactionManager(source));
    }

    private void restartStorage() {
        storage = new V3OriginalScanImageStorage(directory.resolve("evidence").toString());
        service = newService(new DataSourceTransactionManager(source));
    }

    private V3ScanPageIngestionService newService(DataSourceTransactionManager manager) {
        return new V3ScanPageIngestionService(new V3ScanPageRepository(jdbc), storage, validators.getValidator(), manager,
                new V3ScanUploadLedgerRepository(jdbc), mapper);
    }
    private V3ScanPageUploadMetadata change(String field, Object value) throws Exception {
        var json = mapper.valueToTree(request);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).set(field, mapper.valueToTree(value));
        return mapper.treeToValue(json, V3ScanPageUploadMetadata.class);
    }
    private void assertCode(String code, V3ScanPageUploadMetadata input) {
        assertEquals(code, assertThrows(V3AuthException.class, () -> service.ingest(TEACHER, input, upload())).getCode());
    }
    private void assertNoRows() {
        for (String table : new String[]{"test_results", "scan_sessions", "test_result_scans", "scan_pages", "answer_attachments", "syncs", "sync_items"})
            assertEquals(0, count(table), table);
    }
    private void assertNoCaptureRows() {
        for (String table : new String[]{"test_results", "scan_sessions", "test_result_scans", "scan_pages", "answer_attachments", "sync_items"})
            assertEquals(0, count(table), table);
        assertEquals(1, count("syncs"), "The durable pending batch remains reserved");
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private long evidenceCount() throws Exception {
        try (var files = Files.list(directory.resolve("evidence"))) { return files.filter(Files::isRegularFile).count(); }
    }
    private MockMultipartFile upload() { return new MockMultipartFile("image", "device/private/scan.jpg", "image/jpeg", image); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
