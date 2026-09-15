package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.repository.*;
import com.capstone.assessment.v3.scoring.repository.V3ScoringRepository;
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
public class V3ResultSupersedeService {
    private final V3ResultSupersedeRepository repository;
    private final V3ResultReopenRepository ownership;
    private final V3ScanUploadLedgerRepository owners;
    private final V3ScoringRepository scoring;
    private final V3MobileFinalizationRepository finals;
    private final V3MobileFinalizationService finalizer;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    public V3ResultSupersedeService(V3ResultSupersedeRepository repository,V3ResultReopenRepository ownership,V3ScanUploadLedgerRepository owners,
            V3ScoringRepository scoring,V3MobileFinalizationRepository finals,V3MobileFinalizationService finalizer,ObjectMapper mapper,Validator validator,
            PlatformTransactionManager manager,@Value("${app.v3.mobile.supersede-enabled:false}") boolean enabled){
        this.repository=repository;this.ownership=ownership;this.owners=owners;this.scoring=scoring;this.finals=finals;this.finalizer=finalizer;
        this.mapper=mapper.copy();this.validator=validator;this.enabled=enabled;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public V3LifecycleAck supersede(V3AuthenticatedUser user,String uuid,V3SupersedeRequest request){
        authorize(user,uuid);
        if(request==null || !validator.validate(request).isEmpty() || uuid.equals(request.replacementResultUuid()))throw invalid();
        gate();String requestJson=json(request),hash=hash(json(List.of("mobile-result-supersede-v1",user.userId(),user.schoolId(),uuid,request)));
        try{return transaction.execute(status->{
            lockOwner(user);
            // All Mobile writers lock the teacher first; lock both owned result contexts in stable UUID order too.
            var ids=new HashMap<String,Long>();
            for(String key:List.of(uuid,request.replacementResultUuid()).stream().sorted().toList())ids.put(key,owned(user,key));
            long result=ids.get(uuid),replacement=ids.get(request.replacementResultUuid());
            var saved=repository.saved(request.operationUuid());
            if(saved.isPresent()){
                var old=saved.get();if(old.result()!=result || old.replacement()!=replacement || old.teacher()!=user.userId() || !old.hash().equals(hash) || !old.syncUuid().equals(request.syncUuid()))
                    throw conflict("SUPERSEDE_IDENTITY_CONFLICT","Operation is bound to different content.");
                var response=ack(old);
                if(response.revision()!=request.expectedRevision()+1 || response.scoreVersion()!=request.expectedScoreVersion())throw corrupt();
                return response.replay();
            }
            if(ownership.syncExists(request.syncUuid()))throw conflict("SYNC_IDENTITY_CONFLICT","Use a distinct sync UUID for each operation.");
            var old=scoring.findResultContextForUpdate(result).orElseThrow();var next=scoring.findResultContextForUpdate(replacement).orElseThrow();
            if(!Set.of("finalized","pending_verification").contains(old.resultStatus()) || old.scoredAt()==null)
                throw conflict("SOURCE_NOT_OFFICIAL","Supersede requires a finalized or audited reopened official result.");
            if(!"finalized".equals(next.resultStatus()))throw conflict("REPLACEMENT_NOT_FINALIZED","Finish teacher verification and backend finalization of the replacement first.");
            if(old.testAssignmentId()!=next.testAssignmentId() || old.classListId()!=next.classListId() || old.studentId()!=next.studentId()
                    || old.testId()!=next.testId() || next.attemptNumber()<=old.attemptNumber())throw conflict("REPLACEMENT_CONTEXT_MISMATCH","Replacement must be a newer server-assigned attempt for the same delivery and student.");
            if(!Set.of("active","completed").contains(old.testStatus()) || "archived".equals(old.testAssignmentStatus()))throw conflict("ASSESSMENT_NOT_SCORABLE","Assessment and delivery must permit this new lifecycle operation.");
            long revision=finals.revision(result);
            if(revision!=request.expectedRevision() || old.scoreVersion()!=request.expectedScoreVersion())throw conflict("REVISION_CONFLICT","Refresh the source revision and score version before a new operation.");
            if(revision>=9007199254740991L)throw conflict("RESULT_REVISION_EXHAUSTED","Result revision cannot advance.");
            if(!repository.latest(next.testAssignmentId(),next.classListId(),next.attemptNumber()) || repository.replacementUsed(replacement))
                throw conflict("REPLACEMENT_NOT_CURRENT","Use the latest non-superseded attempt, not a replacement already consumed by another source.");
            var previous=finals.receipt(result).orElseThrow(()->conflict("OFFICIAL_SCORE_UNAVAILABLE","Previous official receipt is required."));
            var replacementScore=finals.receipt(replacement).orElseThrow(()->conflict("OFFICIAL_SCORE_UNAVAILABLE","Replacement official receipt is required."));
            if(previous.scoreVersion()!=old.scoreVersion() || replacementScore.scoreVersion()!=next.scoreVersion()
                    || replacementScore.revision()!=finals.revision(replacement))throw corrupt();
            if("pending_verification".equals(old.resultStatus())){
                if(!repository.reopened(result,old.scoreVersion(),revision,previous.json()))throw conflict("AUDITED_REOPEN_REQUIRED","Pending source must have an audited reopen of this official score.");
            }else if(previous.revision()!=revision)throw corrupt();
            finalizer.officialSnapshot(old,previous);finalizer.officialSnapshot(next,replacementScore);
            if(!repository.distinctCaptures(result,replacement))throw conflict("REPLACEMENT_CAPTURE_REQUIRED","Both results need distinct selected capture sessions.");
            finalizer.validateOriginal(next);
            var ack=new V3LifecycleAck(uuid,"superseded",revision+1,old.scoreVersion(),request.replacementResultUuid(),"created",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            repository.commit(user,result,replacement,old.testAssignmentId(),request,ack,hash,requestJson,previous.json(),replacementScore.json(),json(ack));
            return ack;
        });}catch(DuplicateKeyException e){throw conflict("SUPERSEDE_IDENTITY_CONFLICT","Source, replacement, operation or sync is already bound.");}
        catch(DataAccessException | TransactionException e){throw error("SUPERSEDE_PERSISTENCE_UNAVAILABLE","Retry the identical request; commit acknowledgement may have been lost.",HttpStatus.SERVICE_UNAVAILABLE);}
    }
    /** Follow the immediate durable link after restart; repeat for each successor to resolve a longer chain. */
    public V3LifecycleAck supersession(V3AuthenticatedUser user,String uuid){
        authorize(user,uuid);gate();return transaction.execute(s->{
            lockOwner(user);owned(user,uuid);var saved=repository.bySource(uuid).orElseThrow(()->error("SUPERSESSION_NOT_FOUND","No committed replacement link exists for this result.",HttpStatus.NOT_FOUND));
            owned(user,saved.replacementUuid());return ack(saved);
        });
    }
    private V3LifecycleAck ack(V3ResultSupersedeRepository.Saved saved){
        try{var a=mapper.readValue(saved.json(),V3LifecycleAck.class);
            if(a==null || !saved.resultUuid().equals(a.resultUuid()) || !saved.replacementUuid().equals(a.replacementResultUuid())
                    || !"superseded".equals(a.resultStatus()) || !"created".equals(a.disposition()) || a.acknowledgedAt()==null || a.revision()!=saved.revision() || a.scoreVersion()!=saved.version())throw corrupt();return a;
        }catch(java.io.IOException e){throw corrupt();}
    }
    private void authorize(V3AuthenticatedUser u,String uuid){
        if(u==null)throw error("AUTHENTICATION_REQUIRED","Authentication is required.",HttpStatus.UNAUTHORIZED);
        if(!"teacher".equals(u.role()) || !"active".equals(u.status()) || u.schoolId()==null || u.schoolId().isBlank())throw forbidden();
        if(uuid==null || !uuid.matches(V3ReopenRequest.UUID))throw invalid();
    }
    private void gate(){if(!enabled)throw error("MOBILE_SUPERSEDE_UNAVAILABLE","Supersession awaits reviewed deployment and coordinated release.",HttpStatus.SERVICE_UNAVAILABLE);}
    private void lockOwner(V3AuthenticatedUser u){if(owners.lockOwner(u.userId(),u.schoolId()).isEmpty())throw forbidden();}
    private long owned(V3AuthenticatedUser u,String uuid){return ownership.ownedResult(u,uuid).orElseThrow(()->error("RESOURCE_NOT_FOUND","Owned result was not found.",HttpStatus.NOT_FOUND));}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private String hash(String v){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private V3AuthException invalid(){return error("VALIDATION_FAILED","Use the 3.0 supersede contract with distinct canonical result identities and a reason.",HttpStatus.UNPROCESSABLE_ENTITY);}
    private V3AuthException forbidden(){return error("RESULT_ACCESS_DENIED","An active assigned teacher is required.",HttpStatus.FORBIDDEN);}
    private V3AuthException corrupt(){return conflict("SUPERSEDE_STATE_CONFLICT","The official or supersession receipt requires reconciliation.");}
    private V3AuthException conflict(String c,String m){return error(c,m,HttpStatus.CONFLICT);}
    private V3AuthException error(String c,String m,HttpStatus status){return new V3AuthException(c,m,status);}
}
