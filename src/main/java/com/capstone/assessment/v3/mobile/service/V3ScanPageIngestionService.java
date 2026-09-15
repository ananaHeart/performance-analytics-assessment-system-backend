package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.answersheet.service.V3DynamicLayout;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadResponse;
import com.capstone.assessment.v3.mobile.repository.V3ScanPageRepository;
import com.capstone.assessment.v3.mobile.repository.V3ScanPageRepository.CaptureContext;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage.OriginalImage;
import com.capstone.assessment.v3.mobile.repository.V3ScanUploadLedgerRepository;
import com.capstone.assessment.v3.mobile.repository.V3ScanUploadLedgerRepository.Upload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;

/** Durable first-capture and teacher-requested pre-finalization rescan ingestion. HTTP remains denied pending ledger deployment and release validation. */
@Service
@Profile("v3")
public class V3ScanPageIngestionService {
    private final V3ScanPageRepository repository;
    private final V3OriginalScanImageStorage storage;
    private final Validator validator;
    private final TransactionTemplate transaction;
    private final V3ScanUploadLedgerRepository ledger;
    private final ObjectMapper mapper;

    public V3ScanPageIngestionService(V3ScanPageRepository repository, V3OriginalScanImageStorage storage,
                                      Validator validator, PlatformTransactionManager manager,
                                      V3ScanUploadLedgerRepository ledger, ObjectMapper mapper) {
        this.repository = repository;
        this.storage = storage;
        this.validator = validator;
        this.ledger = ledger;
        this.mapper = mapper.copy();
        transaction = new TransactionTemplate(manager);
        // An acknowledgement must never precede an enclosing transaction's eventual commit.
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public V3ScanPageUploadResponse ingest(V3AuthenticatedUser user, V3ScanPageUploadMetadata request,
                                           MultipartFile image) {
        if (user == null || !"teacher".equals(user.role()) || !"active".equals(user.status())) {
            throw error("TEACHER_REQUIRED", "An active assigned teacher is required.", HttpStatus.FORBIDDEN);
        }
        if (request == null || !validator.validate(request).isEmpty()
                || request.scannerVersion().length() > 50 || request.pageNumber() > 65535
                || request.captureNumber() > 65535) {
            throw error("VALIDATION_FAILED", "Scan metadata is missing or invalid.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (request.captureNumber() > 5) {
            throw conflict("RESCAN_LIMIT", "At most five captures per page are supported in this bounded Mobile workflow.");
        }
        try {
            // Preflight prevents unauthorized/changed retries from writing even temporary evidence.
            transaction.executeWithoutResult(status -> inspect(user, request));
            try (var incoming = storage.stage(image, request.imageHash())) {
                transaction.executeWithoutResult(status -> {
                    Upload existing = inspect(user, request);
                    if (existing == null) {
                        reserveGroup(user, request);
                        incoming.retainForRecovery();
                        ledger.insert(user, request, fingerprint(user, request), serialize(request), incoming.image());
                    } else {
                        storage.restorePending(existing.image(), incoming);
                    }
                });
                return finish(user, request);
            }
        } catch (DuplicateKeyException e) {
            throw conflict("SCAN_IDENTITY_CONFLICT", "A scan identity or capture already exists; no replacement was accepted.");
        } catch (DataAccessException | TransactionException e) {
            var failure = error("SCAN_PERSISTENCE_FAILED", "The scan could not be committed. No upload was acknowledged.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
            failure.initCause(e);
            throw failure;
        }
    }

    /** Recovery uses persisted metadata and current DB ownership, never a cached session or client-supplied user. */
    public V3ScanPageUploadResponse recover(String pageUuid) {
        Upload upload = ledger.find(pageUuid, false).orElseThrow(() -> conflict("UPLOAD_NOT_FOUND", "No durable upload intent exists."));
        V3ScanPageUploadMetadata request;
        try { request = mapper.readValue(upload.requestJson(), V3ScanPageUploadMetadata.class); }
        catch (JsonProcessingException e) { throw conflict("UPLOAD_RECEIPT_INVALID", "The durable request cannot be decoded."); }
        var user = new V3AuthenticatedUser(upload.teacherId(), upload.schoolId(), "", "teacher", "active", "");
        return finish(user, request);
    }

    public List<RecoveryOutcome> recoverPending(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Recovery batch size must be 1..100.");
        List<RecoveryOutcome> outcomes = new ArrayList<>();
        for (String uuid : ledger.pending(limit)) {
            try { recover(uuid); outcomes.add(new RecoveryOutcome(uuid, "recovered", null)); }
            catch (V3AuthException e) { outcomes.add(new RecoveryOutcome(uuid, "retry_required", e.getCode())); }
            catch (DataAccessException | TransactionException e) { outcomes.add(new RecoveryOutcome(uuid, "retry_required", "SCAN_PERSISTENCE_FAILED")); }
        }
        return List.copyOf(outcomes);
    }

    private V3ScanPageUploadResponse finish(V3AuthenticatedUser user, V3ScanPageUploadMetadata request) {
        try {
            Completion completed = transaction.execute(status -> {
                Upload upload = inspect(user, request);
                if (upload == null) throw conflict("UPLOAD_NOT_FOUND", "No durable upload intent exists.");
                if ("committed".equals(upload.state())) {
                    storage.recoverPublish(upload.image());
                    return new Completion(upload.pageId(), "replayed", upload.pageStatus());
                }
                long pageId = persist(user, request, upload.image());
                ledger.committed(request.scanPageUuid(), pageId);
                return new Completion(pageId, "created", "captured");
            });
            if (completed == null) throw new IllegalStateException("Missing committed upload receipt.");
            return new V3ScanPageUploadResponse(request.syncUuid(), request.resultUuid(), request.scanUuid(),
                    request.scanPageUuid(), completed.pageId(), completed.status(), completed.pageStatus(), request.imageHash(), Instant.now());
        } catch (RuntimeException failure) {
            // Separate transaction: preserve diagnostics even when scan insertion rolls back. Never overwrite a committed receipt.
            String code = failure instanceof V3AuthException auth ? auth.getCode()
                    : failure instanceof DuplicateKeyException ? "SCAN_IDENTITY_CONFLICT" : "SCAN_PERSISTENCE_FAILED";
            try { transaction.executeWithoutResult(status -> {
                ledger.lockOwner(user.userId(), user.schoolId());
                Upload row = ledger.find(request.scanPageUuid(), true).orElse(null);
                if (row != null && row.teacherId() == user.userId() && row.requestHash().equals(fingerprint(user, request))) {
                    ledger.failed(request.scanPageUuid(), code);
                }
            }); } catch (RuntimeException diagnosticFailure) { failure.addSuppressed(diagnosticFailure); }
            throw failure;
        }
    }

    private Upload inspect(V3AuthenticatedUser user, V3ScanPageUploadMetadata request) {
        ledger.lockOwner(user.userId(), user.schoolId()).orElseThrow(() ->
                error("SCAN_CONTEXT_NOT_FOUND", "The assigned teacher is unavailable.", HttpStatus.NOT_FOUND));
        Upload existing = ledger.find(request.scanPageUuid(), true).orElse(null);
        if (existing != null && (existing.teacherId() != user.userId() || !existing.schoolId().equals(user.schoolId())
                || !existing.requestHash().equals(fingerprint(user, request)))) {
            throw conflict("SCAN_IDENTITY_CONFLICT", "This page UUID is already bound to different upload content.");
        }
        boolean replay = existing != null && "committed".equals(existing.state());
        CaptureContext context = repository.lockContext(user, request, replay).orElseThrow(() ->
                error("SCAN_CONTEXT_NOT_FOUND", "No owned assignment, learner and sheet page match this upload.", HttpStatus.NOT_FOUND));
        if (!replay) {
            validateContext(request, context);
            var content = repository.contentSnapshot(context);
            if (content.count() != context.totalQuestions() || content.multipleChoiceCount() != content.count()
                    || repository.mappedQuestions(context) != (V3DynamicLayout.CODE.equals(context.templateCode())
                        ? V3DynamicLayout.pageQuestions(content.count(), context.pageNumber()) : content.count())
                    || (V3DynamicLayout.CODE.equals(context.templateCode()) && !repository.completeDynamicMapping(context))) {
                throw conflict("ASSESSMENT_SNAPSHOT_MISMATCH", "The stored sheet does not match the current assessment questions.");
            }
        }
        return existing;
    }

    private String serialize(V3ScanPageUploadMetadata request) {
        try { return mapper.writeValueAsString(request); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not encode the durable scan request.", e); }
    }

    private void reserveGroup(V3AuthenticatedUser user, V3ScanPageUploadMetadata request) {
        CaptureContext context = repository.lockContext(user, request).orElseThrow();
        var existing = repository.lockSync(request.syncUuid());
        if (existing.isPresent()) {
            var row = existing.get();
            if (row.teacherId() != user.userId() || row.assignmentId() != context.assignmentId()
                    || !"upload".equals(row.direction()) || !groupHash(user, request).equals(row.groupHash())) {
                throw conflict("SYNC_IDENTITY_CONFLICT", "The sync UUID is reserved for another upload group.");
            }
        } else repository.insertSyncIntent(user, request, context, groupHash(user, request));
    }

    private static String groupHash(V3AuthenticatedUser user, V3ScanPageUploadMetadata request) {
        return hash(String.join("\n", "v3-scan-stage-group-1", Long.toString(user.userId()),
                request.assignmentUuid(), Long.toString(request.classListId()), request.resultUuid(), request.scanUuid(), request.answerSheetUuid()));
    }

    private static String fingerprint(V3AuthenticatedUser user, V3ScanPageUploadMetadata r) {
        // Explicit length-prefixed, versioned canonical fields; JSON ordering and timestamp spelling cannot change identity.
        var canonical = new StringBuilder("scan-page-receipt-v1");
        for (String value : List.of(Long.toString(user.userId()), user.schoolId(), r.contractVersion(), r.syncUuid(),
                r.resultUuid(), r.scanUuid(), r.scanPageUuid(), r.answerSheetUuid(), r.pageUuid(), r.assignmentUuid(),
                Long.toString(r.classListId()), Integer.toString(r.pageNumber()), Integer.toString(r.captureNumber()),
                r.scannerVersion(), r.qrPayloadHash(), r.imageHash(), r.capturedAt().toString())) {
            canonical.append('|').append(value.length()).append(':').append(value);
        }
        return hash(canonical.toString());
    }

    public record RecoveryOutcome(String scanPageUuid, String status, String errorCode) { }
    private record Completion(long pageId, String status, String pageStatus) { }

    private long persist(V3AuthenticatedUser user, V3ScanPageUploadMetadata request, OriginalImage image) {
        CaptureContext context = repository.lockContext(user, request).orElseThrow(() ->
                error("SCAN_CONTEXT_NOT_FOUND", "No eligible owned assignment, learner and sheet page match this scan.",
                        HttpStatus.NOT_FOUND));
        validateContext(request, context);
        var content = repository.contentSnapshot(context);
        if (content.count() != context.totalQuestions() || content.multipleChoiceCount() != content.count()
                || repository.mappedQuestions(context) != (V3DynamicLayout.CODE.equals(context.templateCode())
                        ? V3DynamicLayout.pageQuestions(content.count(), context.pageNumber()) : content.count())
                    || (V3DynamicLayout.CODE.equals(context.templateCode()) && !repository.completeDynamicMapping(context))) {
            throw conflict("ASSESSMENT_SNAPSHOT_MISMATCH", "The stored sheet does not match the current assessment questions.");
        }
        if (repository.scanPageExists(request.scanPageUuid())) {
            throw conflict("SCAN_IDENTITY_CONFLICT", "A page exists without a matching committed receipt; review is required.");
        }
        var result = repository.lockResult(request.resultUuid());
        result.ifPresent(row -> {
            if (row.assignmentId() != context.assignmentId() || row.classListId() != request.classListId()) {
                throw conflict("SCAN_IDENTITY_CONFLICT", "The result identity cannot be reused for this capture.");
            }
            if (!"draft".equals(row.status()) && !"pending_verification".equals(row.status())) {
                throw conflict("RESULT_LOCKED", "Finalized or superseded results cannot receive captures.");
            }
        });
        if(request.captureNumber()>1 && result.isEmpty())throw conflict("RESCAN_PREDECESSOR_REQUIRED","Rescan requires an existing result and selected session.");
        long resultId = result.map(V3ScanPageRepository.ResultRow::id)
                .orElseGet(() -> repository.insertResult(request, context, content.maximumScore()));
        var scan = repository.lockScan(request.scanUuid());
        scan.ifPresent(row -> {
            if (row.assignmentId() != context.assignmentId() || row.classListId() != request.classListId()
                    || row.sheetId() != context.sheetId() || row.teacherId() != user.userId()
                    || row.resultId() != resultId || !"selected".equals(row.linkStatus())) {
                throw conflict("SCAN_IDENTITY_CONFLICT", "The scan identity cannot be reused for this capture.");
            }
            if (!"captured".equals(row.status()) && !"needs_verification".equals(row.status())
                    && !(request.captureNumber()>1 && "rescan_requested".equals(row.status()))) {
                throw conflict("SCAN_LOCKED", "This scan session cannot receive new pages.");
            }
        });
        if (scan.isEmpty() && repository.hasSelectedScan(resultId)) {
            throw conflict("SCAN_IDENTITY_CONFLICT", "This result already has a selected scan session.");
        }
        if(request.captureNumber()>1 && scan.isEmpty())throw conflict("RESCAN_PREDECESSOR_REQUIRED","Retain the existing selected scan UUID for a page rescan.");
        long scanId = scan.map(V3ScanPageRepository.ScanRow::id)
                .orElseGet(() -> repository.insertScan(user, request, context, resultId));
        String groupHash = groupHash(user, request);
        var sync = repository.lockSync(request.syncUuid());
        sync.ifPresent(row -> {
            if (row.teacherId() != user.userId() || row.assignmentId() != context.assignmentId()
                    || !"upload".equals(row.direction()) || !groupHash.equals(row.groupHash())) {
                throw conflict("SYNC_IDENTITY_CONFLICT", "The sync identity belongs to different capture context.");
            }
        });
        long syncId = sync.map(V3ScanPageRepository.SyncRow::id)
                .orElseGet(() -> repository.insertSync(user, request, context, resultId, groupHash));
        repository.ensureSyncItem(syncId, request.resultUuid(), resultId);
        Long predecessor=request.captureNumber()>1?repository.prepareRescan(user,request,resultId,scanId,context):null;
        long pageId = repository.insertPage(scanId, request, context);
        if(predecessor!=null)repository.linkRescan(user,request,resultId,scanId,pageId,predecessor);
        repository.insertOriginal(scanId, pageId, image, request.capturedAt());
        if(predecessor==null)repository.recordCapturedPage(scanId);
        storage.recoverPublish(image);
        // Success acknowledges this page, not result completion; finalization checks all manifest pages.
        repository.completeScanSync(syncId, request.resultUuid());
        return pageId;
    }

    private void validateContext(V3ScanPageUploadMetadata request, CaptureContext context) {
        Instant now = Instant.now();
        if (request.capturedAt().isAfter(now.plusSeconds(300))
                || context.openAt() != null && request.capturedAt().isBefore(context.openAt())
                || "archived".equals(context.assignmentStatus()) || "planned".equals(context.assignmentStatus())
                || !context.allowLateCapture() && ("closed".equals(context.assignmentStatus())
                    || context.closeAt() != null && request.capturedAt().isAfter(context.closeAt()))) {
            throw conflict("CAPTURE_NOT_ALLOWED", "The capture falls outside the assignment's permitted capture policy.");
        }
        if (context.sheetTestVersion() != context.testVersion()
                || context.totalQuestions() != context.testTotalItems() || (V3DynamicLayout.CODE.equals(context.templateCode()) ? context.totalQuestions() < 5 || context.totalQuestions() > V3DynamicLayout.MAX_QUESTIONS : context.totalQuestions() != 10)
                || (V3DynamicLayout.CODE.equals(context.templateCode()) ? context.totalPages() != V3DynamicLayout.pages(context.totalQuestions()) : context.totalPages() != 1) || context.pageTotalPages() != context.totalPages()
                || context.pageNumber() != request.pageNumber() || context.pageNumber() < 1 || context.pageNumber() > context.totalPages()) {
            throw conflict("ASSESSMENT_SNAPSHOT_MISMATCH", "The sheet, page and assessment snapshots do not match.");
        }
        if (!("OMR-A4-10-MC-CTX-V2".equals(context.templateCode()) || V3DynamicLayout.CODE.equals(context.templateCode())) || !"active".equals(context.templateStatus())
                || !"A4".equals(context.paperSize()) || !scannerAtLeast(request.scannerVersion(), context.minimumScannerVersion())) {
            throw conflict("SCANNER_TEMPLATE_UNSUPPORTED", "This template or scanner version is not eligible for this capture slice.");
        }
        if (V3DynamicLayout.CODE.equals(context.templateCode())) {
            try {
                var qr = mapper.readTree(context.qrPayload());
                String pageGeometry = java.util.HexFormat.of().formatHex(java.util.Base64.getUrlDecoder().decode(qr.path("gh").asText()));
                if (!V3DynamicLayout.VERSION.equals(context.templateVersion()) || !V3DynamicLayout.SCANNER_VERSION.equals(context.minimumScannerVersion())
                        || !context.qrPayload().equals(V3DynamicLayout.qr(request.answerSheetUuid(), request.pageUuid(), request.assignmentUuid(),
                                request.pageNumber(), context.totalPages(), pageGeometry)))
                    throw conflict("QR_PAYLOAD_MISMATCH", "Dynamic QR identities do not match the immutable sheet/page.");
            } catch (java.io.IOException | IllegalArgumentException e) { throw conflict("QR_PAYLOAD_MISMATCH", "Invalid dynamic QR payload."); }
        }
        if (!hash(context.qrPayload()).equals(context.qrHash()) || !request.qrPayloadHash().equals(context.qrHash())) {
            throw conflict("QR_PAYLOAD_MISMATCH", "The QR hash does not match the stored immutable page payload.");
        }
    }

    private static boolean scannerAtLeast(String actual, String minimum) {
        if (actual == null || minimum == null || !actual.matches("[0-9]{1,6}(\\.[0-9]{1,6}){0,2}")
                || !minimum.matches("[0-9]{1,6}(\\.[0-9]{1,6}){0,2}")) return false;
        String[] left = actual.split("\\.");
        String[] right = minimum.split("\\.");
        for (int i = 0; i < 3; i++) {
            int difference = Integer.parseInt(i < left.length ? left[i] : "0")
                    - Integer.parseInt(i < right.length ? right[i] : "0");
            if (difference != 0) return difference > 0;
        }
        return true;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static V3AuthException conflict(String code, String message) { return error(code, message, HttpStatus.CONFLICT); }
    private static V3AuthException error(String code, String message, HttpStatus status) {
        return new V3AuthException(code, message, status);
    }
}
