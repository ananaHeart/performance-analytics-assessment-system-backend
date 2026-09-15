package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3DetectionBatch;
import com.capstone.assessment.v3.mobile.dto.V3DetectionBatch.Detection;
import com.capstone.assessment.v3.mobile.dto.V3DetectionUploadResponse;
import com.capstone.assessment.v3.mobile.dto.V3DetectionUploadResponse.IdMapping;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.capstone.assessment.v3.mobile.repository.V3DetectionRepository;
import com.capstone.assessment.v3.mobile.repository.V3ScanUploadLedgerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Immutable raw observations only. This service never creates answers, verifies pages or computes scores. */
@Service
@Profile("v3")
public class V3DetectionUploadService {
    private final V3DetectionRepository repository;
    private final V3ScanUploadLedgerRepository owners;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public V3DetectionUploadService(V3DetectionRepository repository, V3ScanUploadLedgerRepository owners,
            Validator validator, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.repository=repository; this.owners=owners; this.validator=validator; this.mapper=mapper.copy();
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public V3DetectionUploadResponse upload(V3AuthenticatedUser user, String pageUuid, V3DetectionBatch batch) {
        if(user==null || !"teacher".equals(user.role()) || !"active".equals(user.status()))
            throw error("TEACHER_REQUIRED","An active assigned teacher is required.",HttpStatus.FORBIDDEN);
        validate(pageUuid,batch);
        // A batch is a set: ordering and equivalent decimal spellings do not change its identity.
        var sorted=batch.detections().stream().sorted(Comparator.comparing(Detection::detectionUuid))
                .map(d -> new Detection(d.detectionUuid(),d.regionUuid(),d.questionUuid(),d.detectionStatus(),
                        d.detectedOption(),d.confidence().stripTrailingZeros())).toList();
        var canonical=new V3DetectionBatch(batch.contractVersion(),batch.syncUuid(),batch.operationUuid(),sorted);
        String hash=hash(json(List.of("v3-detections-1",user.userId(),user.schoolId(),pageUuid,canonical)));
        try {
            return transaction.execute(status -> {
                owners.lockOwner(user.userId(),user.schoolId()).orElseThrow(() -> missing("The assigned teacher is unavailable."));
                var receipt=repository.receipt(batch.operationUuid());
                var page=repository.lockPage(user,pageUuid,receipt.isPresent()).orElseThrow(() ->
                        missing("No owned committed scan page matches this request."));
                if(receipt.isPresent()) {
                    var old=receipt.get();
                    if(old.pageId()!=page.pageId() || !old.hash().equals(hash))
                        throw conflict("DETECTION_IDENTITY_CONFLICT","The operation UUID is bound to different content.");
                    try { return mapper.readValue(old.response(),V3DetectionUploadResponse.class).replay(); }
                    catch(JsonProcessingException e) { throw new IllegalStateException("Invalid stored detection receipt.",e); }
                }
                if(!Set.of("draft","pending_verification").contains(page.resultStatus())
                        || !Set.of("captured","needs_verification").contains(page.scanStatus())
                        || !"captured".equals(page.pageStatus()) || !"selected".equals(page.linkStatus()))
                    throw conflict("DETECTION_TARGET_LOCKED","Only an unverified selected capture can receive new detections.");
                if(page.sheetVersion()!=page.testVersion())
                    throw conflict("ASSESSMENT_SNAPSHOT_MISMATCH","The question version differs from the captured sheet.");
                if(page.revision()>=9007199254740991L)
                    throw conflict("REVISION_LIMIT","The result revision cannot be advanced.");
                if(repository.syncExists(batch.syncUuid()))
                    throw conflict("SYNC_IDENTITY_CONFLICT","Use a new sync UUID for a new detection operation.");
                List<IdMapping> mappings=new ArrayList<>();
                for(Detection detection:sorted) {
                    var region=repository.region(page,detection).orElseThrow(() ->
                            conflict("DETECTION_REGION_MISMATCH","The region and question must belong to the captured page."));
                    validateRegion(region,detection);
                    if(repository.occupied(detection.detectionUuid(),page.pageId(),region.id()))
                        throw conflict("DETECTION_IDENTITY_CONFLICT","A detection UUID or page region is already recorded; retry its original operation.");
                    // No per-bubble darkness values are supplied by this DTO. Preserve the exact reported summary instead of inventing them.
                    String raw=json(Map.of("source","mobile_summary_v3","observation",detection));
                    long id=repository.insert(page,region,detection,raw);
                    mappings.add(new IdMapping("omr_detection",detection.detectionUuid(),id));
                }
                long syncId=repository.insertSync(user,page,batch.syncUuid(),hash);
                var response=new V3DetectionUploadResponse(batch.syncUuid(),batch.operationUuid(),"created",
                        page.revision()+1,List.copyOf(mappings),Instant.now());
                repository.finish(page,syncId,batch.operationUuid(),hash,json(response));
                return response;
            });
        } catch(DuplicateKeyException e) {
            throw conflict("DETECTION_IDENTITY_CONFLICT","An operation, sync, detection or region identity already exists.");
        } catch(DataAccessException | TransactionException e) {
            var failure=error("DETECTION_PERSISTENCE_FAILED","No detection upload was acknowledged. Retry the same operation and content.",HttpStatus.INTERNAL_SERVER_ERROR);
            failure.initCause(e); throw failure;
        }
    }

    private void validate(String pageUuid,V3DetectionBatch batch) {
        if(pageUuid==null || !pageUuid.matches(V3ScanPageUploadMetadata.UUID_PATTERN)
                || batch==null || !validator.validate(batch).isEmpty())
            throw invalid("The detection batch is invalid; supply 1..200 observations and canonical identities.");
        Set<String> ids=new HashSet<>(),regions=new HashSet<>();
        for(Detection d:batch.detections()) {
            if(!ids.add(d.detectionUuid()) || !regions.add(d.regionUuid()))
                throw invalid("Each detection UUID and region must occur once in the batch.");
            if("detected".equals(d.detectionStatus()) != (d.detectedOption()!=null))
                throw invalid("Detected observations require an option; blank, multiple marks and uncertain observations require null.");
        }
    }

    private void validateRegion(V3DetectionRepository.Region region,Detection detection) {
        if(!"objective_bubbles".equals(region.regionType()) || !Set.of("multiple_choice","true_false").contains(region.type()))
            throw conflict("OBJECTIVE_REGION_REQUIRED","Only MC and TF objective regions accept OMR detections.");
        Set<String> keys=new HashSet<>();
        try {
            var geometry=mapper.readTree(region.geometry());
            var options=geometry.get("option_keys");
            if(options==null || !options.isArray()) throw invalid("The stored region has no option keys.");
            for(var option:options) {
                if(!option.isTextual() || !option.textValue().matches("[A-D]") || !keys.add(option.textValue()))
                    throw invalid("The stored region option keys are invalid.");
            }
        } catch(JsonProcessingException e) { throw invalid("The stored region geometry cannot be decoded."); }
        var active=new HashSet<>(repository.options(region.questionId()));
        if(keys.size()<2 || !active.equals(keys) || "true_false".equals(region.type()) && !keys.equals(Set.of("A","B")))
            throw conflict("DETECTION_OPTION_MISMATCH","Stored question options must match the immutable region; TF uses A and B.");
        if(detection.detectedOption()!=null && !keys.contains(detection.detectedOption()))
            throw conflict("DETECTION_OPTION_MISMATCH","The selected option is not present in the stored region.");
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Could not encode detection evidence.",e); }
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static V3AuthException invalid(String message) { return error("VALIDATION_FAILED",message,HttpStatus.UNPROCESSABLE_ENTITY); }
    private static V3AuthException missing(String message) { return error("SCAN_CONTEXT_NOT_FOUND",message,HttpStatus.NOT_FOUND); }
    private static V3AuthException conflict(String code,String message) { return error(code,message,HttpStatus.CONFLICT); }
    private static V3AuthException error(String code,String message,HttpStatus status) { return new V3AuthException(code,message,status); }
}
