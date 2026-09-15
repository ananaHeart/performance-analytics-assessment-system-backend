package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.repository.*;
import com.capstone.assessment.v3.scoring.repository.V3ScoringRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service
@Profile("v3")
public class V3ResultReopenService {
    private final V3ResultReopenRepository repository;
    private final V3ScanUploadLedgerRepository owners;
    private final V3ScoringRepository scoring;
    private final V3MobileFinalizationRepository finalizations;
    private final V3MobileFinalizationService finalizer;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    public V3ResultReopenService(V3ResultReopenRepository repository,V3ScanUploadLedgerRepository owners,V3ScoringRepository scoring,
            V3MobileFinalizationRepository finalizations,V3MobileFinalizationService finalizer,ObjectMapper mapper,Validator validator,
            PlatformTransactionManager manager,@Value("${app.v3.mobile.reopen-enabled:false}") boolean enabled){
        this.repository=repository;this.owners=owners;this.scoring=scoring;this.finalizations=finalizations;this.finalizer=finalizer;
        this.mapper=mapper.copy();this.validator=validator;this.enabled=enabled;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public V3LifecycleAck reopen(V3AuthenticatedUser user,String uuid,V3ReopenRequest request){
        if(user==null)throw error("AUTHENTICATION_REQUIRED","Authentication is required.",HttpStatus.UNAUTHORIZED);
        if(!"teacher".equals(user.role()) || !"active".equals(user.status()) || user.schoolId()==null || user.schoolId().isBlank())throw forbidden();
        if(uuid==null || !uuid.matches(V3ReopenRequest.UUID) || request==null || !validator.validate(request).isEmpty())
            throw error("VALIDATION_FAILED","Use the versioned reopen request with a reason, canonical UUIDs and positive expected versions.",HttpStatus.UNPROCESSABLE_ENTITY);
        if(!enabled)throw error("MOBILE_REOPEN_UNAVAILABLE","Reopen awaits reviewed migration deployment and coordinated release.",HttpStatus.SERVICE_UNAVAILABLE);
        String requestJson=json(request),hash=hash(json(List.of("mobile-result-reopen-v1",user.userId(),user.schoolId(),uuid,request)));
        try{return transaction.execute(status->{
            if(owners.lockOwner(user.userId(),user.schoolId()).isEmpty())throw forbidden();
            long result=repository.ownedResult(user,uuid).orElseThrow(()->error("RESOURCE_NOT_FOUND","The owned result was not found.",HttpStatus.NOT_FOUND));
            var saved=repository.saved(request.operationUuid());
            if(saved.isPresent()){
                var old=saved.get();
                if(old.result()!=result || old.teacher()!=user.userId() || !old.hash().equals(hash) || !old.syncUuid().equals(request.syncUuid()))throw conflict("REOPEN_IDENTITY_CONFLICT","The operation is already bound to different content.");
                V3LifecycleAck ack;
                try{ack=mapper.readValue(old.json(),V3LifecycleAck.class);}catch(JsonProcessingException e){throw receiptConflict();}
                if(ack==null || !uuid.equals(ack.resultUuid()) || !"pending_verification".equals(ack.resultStatus()) || ack.replacementResultUuid()!=null
                        || !"created".equals(ack.disposition()) || ack.acknowledgedAt()==null || ack.revision()!=old.revision() || ack.scoreVersion()!=old.version()
                        || ack.revision()!=request.expectedRevision()+1 || ack.scoreVersion()!=request.expectedScoreVersion())throw receiptConflict();
                return ack.replay(); // Historical acknowledgement; current state comes from result readback.
            }
            if(repository.syncExists(request.syncUuid()))throw conflict("SYNC_IDENTITY_CONFLICT","Use a distinct sync UUID for each operation.");
            var context=scoring.findResultContextForUpdate(result).orElseThrow();
            if(!"finalized".equals(context.resultStatus()))throw conflict("RESULT_NOT_FINALIZED","Only an official finalized result can be reopened.");
            if(!Set.of("active","completed").contains(context.testStatus()))throw conflict("ASSESSMENT_NOT_SCORABLE","The assessment must be active or completed before a new reopen operation.");
            if("archived".equals(context.testAssignmentStatus()))throw conflict("ASSIGNMENT_ARCHIVED","An archived test assignment cannot be reopened.");
            long revision=finalizations.revision(result);
            if(revision!=request.expectedRevision() || context.scoreVersion()!=request.expectedScoreVersion())throw conflict("REVISION_CONFLICT","Reconcile the current result revision and score version before a new reopen request.");
            if(revision>=9007199254740991L)throw conflict("RESULT_REVISION_EXHAUSTED","The result revision cannot advance safely.");
            if(!finalizations.hasPaperCapture(result) || finalizations.receipt(result).isEmpty())throw conflict("OFFICIAL_SCORE_UNAVAILABLE","A committed Mobile finalization receipt is required.");
            var prepared=finalizer.prepare(context).orElseThrow();
            if(prepared.replay()==null)throw receiptConflict();
            String official=finalizations.receipt(result).orElseThrow().json();
            var ack=new V3LifecycleAck(uuid,"pending_verification",revision+1,context.scoreVersion(),null,"created",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            repository.commit(user,result,context.testAssignmentId(),request,ack,hash,requestJson,official,json(ack));return ack;
        });}catch(DuplicateKeyException e){throw conflict("REOPEN_IDENTITY_CONFLICT","An operation, sync or result score version is already reserved.");}
        catch(DataAccessException | TransactionException e){throw error("REOPEN_PERSISTENCE_UNAVAILABLE","Retry the identical reopen request; commit acknowledgement may have been lost.",HttpStatus.SERVICE_UNAVAILABLE);}
    }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Cannot serialize reopen facts.",e);}}
    private String hash(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private V3AuthException forbidden(){return error("RESULT_ACCESS_DENIED","An active assigned teacher is required.",HttpStatus.FORBIDDEN);}
    private V3AuthException receiptConflict(){return conflict("REOPEN_STATE_CONFLICT","The saved reopen receipt requires backend reconciliation.");}
    private V3AuthException conflict(String code,String message){return error(code,message,HttpStatus.CONFLICT);}
    private V3AuthException error(String code,String message,HttpStatus status){return new V3AuthException(code,message,status);}
}
