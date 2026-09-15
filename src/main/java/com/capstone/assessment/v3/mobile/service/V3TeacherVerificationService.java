package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.dto.V3VerificationBatch.*;
import com.capstone.assessment.v3.mobile.dto.V3VerificationResponse.Outcome;
import com.capstone.assessment.v3.mobile.dto.V3DetectionUploadResponse.IdMapping;
import com.capstone.assessment.v3.mobile.repository.*;
import com.capstone.assessment.v3.mobile.repository.V3DetectionRepository.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
@Profile("v3")
public class V3TeacherVerificationService {
    private final V3VerificationRepository repository;
    private final V3DetectionRepository pages;
    private final V3ScanUploadLedgerRepository owners;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public V3TeacherVerificationService(V3VerificationRepository repository,V3DetectionRepository pages,
            V3ScanUploadLedgerRepository owners,Validator validator,ObjectMapper mapper,PlatformTransactionManager manager) {
        this.repository=repository;this.pages=pages;this.owners=owners;this.validator=validator;this.mapper=mapper.copy();
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public V3VerificationResponse verify(V3AuthenticatedUser user,V3VerificationBatch input) {
        if(user==null || !"teacher".equals(user.role()) || !"active".equals(user.status())) throw forbidden();
        validate(input);
        var canonical=new V3VerificationBatch(input.contractVersion(),input.syncUuid(),input.operationUuid(),input.assignmentUuid(),
                input.items().stream().sorted(Comparator.comparing(Item::resultUuid)).map(i->new Item(i.resultUuid(),i.expectedRevision(),
                        i.pageDecisions().stream().sorted(Comparator.comparing(PageDecision::verificationUuid)).toList(),
                        i.answers().stream().sorted(Comparator.comparing(Answer::answerUuid)).toList())).toList());
        String hash=hash(json(List.of("mobile-verification-objective-v1",user.userId(),user.schoolId(),canonical)));
        // Register the immutable whole batch before processing any result. Revalidate ownership for every retry.
        try {
            transaction.executeWithoutResult(status->{
                long assignment=authorize(user,canonical,canonical.items());
                var existing=repository.batch(canonical.operationUuid());
                if(existing.isPresent()) { match(existing.get().hash(),hash);return; }
                if(repository.syncExists(canonical.syncUuid())) throw conflict("SYNC_IDENTITY_CONFLICT","Use a new sync UUID for each new operation.");
                repository.register(user,assignment,canonical,hash,json(canonical));
            });
        } catch(DuplicateKeyException e) { throw conflict("VERIFICATION_IDENTITY_CONFLICT","A batch or sync identity is already reserved."); }
        catch(DataAccessException | TransactionException e) { throw persistence(); }

        List<Outcome> outcomes=new ArrayList<>();
        for(Item item:canonical.items()) {
            try {
                outcomes.add(transaction.execute(status->{
                    authorize(user,canonical,List.of(item));
                    var batch=repository.batch(canonical.operationUuid()).orElseThrow();match(batch.hash(),hash);
                    var receipt=repository.item(batch.syncId(),item.resultUuid());
                    var saved=decode(receipt.response());
                    if(saved!=null && ("success".equals(saved.status()) || !saved.error().retryable())) return saved.replay();
                    var result=repository.result(item.resultUuid()).orElseThrow(()->dependency("Commit the result before verification."));
                    if(!Set.of("draft","pending_verification").contains(result.status()))
                        throw conflict("RESULT_LOCKED","Finalized or superseded results require the later correction workflow.");
                    if(result.revision()!=item.expectedRevision()) throw conflict("REVISION_CONFLICT","Reconcile the result and submit a new operation with its current revision.");
                    if(result.revision()>=9007199254740991L) throw conflict("REVISION_LIMIT","The result revision cannot advance.");
                    List<IdMapping> pageMappings=new ArrayList<>(),answerMappings=new ArrayList<>();Set<Long> scans=new HashSet<>();
                    for(PageDecision decision:item.pageDecisions()) {
                        Page page=page(user,decision.scanPageUuid(),result.id());
                        if(!Set.of("captured","needs_verification").contains(page.pageStatus()))
                            throw conflict("PAGE_DECISION_LOCKED","An existing page decision requires the later audited correction/rescan workflow.");
                        uniqueDecision(decision.verificationUuid());
                        long id=repository.pageDecision(user,canonical.operationUuid(),page,decision);scans.add(page.scanId());
                        pageMappings.add(new IdMapping("scan_verification",decision.verificationUuid(),id));
                    }
                    for(Answer answer:item.answers()) {
                        Page page=page(user,answer.scanPageUuid(),result.id());
                        if(!"accepted".equals(page.pageStatus())) throw conflict("PAGE_NOT_ACCEPTED","Accept the page before accepting its objective answers.");
                        var reference=new V3DetectionBatch.Detection(answer.evaluation().detectionUuid(),answer.regionUuid(),answer.questionUuid(),null,null,null);
                        var region=pages.region(page,reference).orElseThrow(()->conflict("VERIFICATION_REGION_MISMATCH","The question and region must belong to this result's page."));
                        if(!"objective_bubbles".equals(region.regionType()) || !Set.of("multiple_choice","true_false").contains(region.type()))
                            throw conflict("OBJECTIVE_REGION_REQUIRED","This slice accepts only MC/TF detection-backed answers.");
                        var detection=repository.detection(answer.evaluation().detectionUuid(),page.pageId(),region.id(),region.questionId())
                                .orElseThrow(()->dependency("Commit the referenced detection for this page/region/question before verification."));
                        if(!Set.of("detected","blank").contains(detection.status()))
                            throw conflict("OBJECTIVE_RESCAN_REQUIRED","Uncertain or multiple marks cannot become a chosen answer; request a rescan or reject the page.");
                        if("blank".equals(detection.status()) && (detection.option()!=null || detection.optionId()!=null)
                                || "detected".equals(detection.status()) && (detection.optionId()==null || detection.option()==null
                                || !Set.of("A","B","C","D").contains(detection.option())
                                || "true_false".equals(region.type()) && !Set.of("A","B").contains(detection.option())))
                            throw conflict("DETECTION_OPTION_MISMATCH","The stored detection must resolve to a valid current question option.");
                        uniqueDecision(answer.verificationUuid());
                        if(repository.answerExists(answer.answerUuid(),result.id(),region.questionId()))
                            throw conflict("ANSWER_ALREADY_VERIFIED","An existing answer requires the later audited correction workflow.");
                        long id=repository.answer(user,canonical.operationUuid(),page,region.questionId(),answer,detection);scans.add(page.scanId());
                        answerMappings.add(new IdMapping("student_answer",answer.answerUuid(),id));
                    }
                    repository.finishResult(result.id(),user,scans);
                    // The existing contract caps mappings at 200. Answer batches return answer IDs;
                    // page-only batches return page audit IDs. All page audit UUIDs remain in the durable request.
                    var outcome=new Outcome(item.resultUuid(),"success","created",result.revision()+1,
                            List.copyOf(answerMappings.isEmpty()?pageMappings:answerMappings),null);
                    repository.outcome(batch.syncId(),receipt.id(),result.id(),outcome,json(outcome));return outcome;
                }));
            } catch(V3AuthException e) {
                if(e.getStatus()==HttpStatus.FORBIDDEN) throw e;
                outcomes.add(recordFailure(user,canonical,hash,item,e.getCode(),e.getMessage(),"DEPENDENCY_NOT_READY".equals(e.getCode())));
            } catch(DuplicateKeyException e) {
                outcomes.add(recordFailure(user,canonical,hash,item,"VERIFICATION_IDENTITY_CONFLICT","A verification or answer identity is already recorded.",false));
            } catch(DataAccessException | TransactionException e) {
                outcomes.add(recordFailure(user,canonical,hash,item,"VERIFICATION_PERSISTENCE_FAILED","Retry the same immutable operation and content.",true));
            }
        }
        long successes=outcomes.stream().filter(i->"success".equals(i.status())).count();
        return new V3VerificationResponse(canonical.syncUuid(),canonical.operationUuid(),
                successes==outcomes.size()?"success":successes==0?"failed":"partial_success",List.copyOf(outcomes));
    }

    private Outcome recordFailure(V3AuthenticatedUser user,V3VerificationBatch input,String hash,Item item,String code,String message,boolean retryable) {
        try {
            return transaction.execute(status->{
                authorize(user,input,List.of(item));
                var batch=repository.batch(input.operationUuid()).orElseThrow();match(batch.hash(),hash);
                var receipt=repository.item(batch.syncId(),item.resultUuid());var saved=decode(receipt.response());
                // An actual commit may have succeeded before the connection lost its acknowledgement.
                if(saved!=null && ("success".equals(saved.status()) || !saved.error().retryable())) return saved.replay();
                var result=repository.result(item.resultUuid());
                var outcome=new Outcome(item.resultUuid(),"failed","rejected",null,List.of(),new V3VerificationResponse.Error(code,message,retryable));
                repository.outcome(batch.syncId(),receipt.id(),result.map(V3VerificationRepository.Result::id).orElse(null),outcome,json(outcome));return outcome;
            });
        } catch(DataAccessException | TransactionException e) { throw persistence(); }
    }

    private long authorize(V3AuthenticatedUser user,V3VerificationBatch batch,List<Item> checkedItems) {
        owners.lockOwner(user.userId(),user.schoolId()).orElseThrow(V3TeacherVerificationService::forbidden);
        long assignment=repository.assignment(user,batch.assignmentUuid()).orElseThrow(V3TeacherVerificationService::forbidden);
        for(Item item:checkedItems) {
            var result=repository.result(item.resultUuid());
            if(result.isPresent() && (result.get().assignmentId()!=assignment || !repository.resultInSchool(result.get().id(),user.schoolId()))) throw forbidden();
            Set<String> referenced=new HashSet<>();item.pageDecisions().forEach(p->referenced.add(p.scanPageUuid()));item.answers().forEach(a->referenced.add(a.scanPageUuid()));
            for(String uuid:referenced) if(repository.pageExists(uuid)) {
                var page=pages.lockPage(user,uuid,true).orElseThrow(V3TeacherVerificationService::forbidden);
                if(!page.resultUuid().equals(item.resultUuid()) || page.assignmentId()!=assignment) throw forbidden();
            }
        }
        return assignment;
    }
    private Page page(V3AuthenticatedUser user,String uuid,long resultId) {
        var page=pages.lockPage(user,uuid,false).orElseThrow(()->dependency("An eligible committed scan page is required."));
        if(page.resultId()!=resultId) throw forbidden();
        if(!"selected".equals(page.linkStatus()) || !Set.of("captured","needs_verification","accepted").contains(page.scanStatus()))
            throw conflict("SCAN_LOCKED","The selected scan cannot receive initial verification decisions.");
        if(page.sheetVersion()!=page.testVersion()) throw conflict("ASSESSMENT_SNAPSHOT_MISMATCH","The assessment differs from its sheet snapshot.");
        return page;
    }
    private void uniqueDecision(String uuid) {
        if(repository.verificationExists(uuid)) throw conflict("VERIFICATION_IDENTITY_CONFLICT","The verification UUID already identifies a recorded decision.");
    }
    private void validate(V3VerificationBatch batch) {
        if(batch==null || !validator.validate(batch).isEmpty()) throw invalid("The objective verification batch is invalid.");
        Set<String> results=new HashSet<>(),decisions=new HashSet<>(),answers=new HashSet<>();
        for(Item item:batch.items()) {
            if(!results.add(item.resultUuid()) || item.pageDecisions().isEmpty() && item.answers().isEmpty()) throw invalid("Each result must appear once and contain at least one decision or answer.");
            Set<String> pageIds=new HashSet<>(),questions=new HashSet<>();
            for(PageDecision d:item.pageDecisions()) {
                if(!pageIds.add(d.scanPageUuid()) || !decisions.add(d.verificationUuid())) throw invalid("Duplicate page or verification identity.");
                if(!"accepted".equals(d.action()) && (d.reasonCode()==null || d.reasonCode().isBlank())) throw invalid("Rejected/rescan decisions require a reason code.");
            }
            for(Answer a:item.answers()) if(!questions.add(a.questionUuid()) || !decisions.add(a.verificationUuid()) || !answers.add(a.answerUuid()))
                throw invalid("Duplicate answer, question or verification identity.");
        }
    }
    private Outcome decode(String json) {
        if(json==null)return null;
        try{return mapper.readValue(json,Outcome.class);}catch(JsonProcessingException e){throw new IllegalStateException("Invalid stored verification receipt.",e);}
    }
    private String json(Object value) { try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Could not encode verification audit.",e);} }
    private static String hash(String value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
    private static void match(String previous,String current) {if(!previous.equals(current))throw conflict("VERIFICATION_IDENTITY_CONFLICT","The operation UUID is bound to a different immutable batch.");}
    private static V3AuthException invalid(String message){return new V3AuthException("VALIDATION_FAILED",message,HttpStatus.UNPROCESSABLE_ENTITY);}
    private static V3AuthException forbidden(){return new V3AuthException("VERIFICATION_FORBIDDEN","The teacher must own the assignment, results and referenced pages.",HttpStatus.FORBIDDEN);}
    private static V3AuthException conflict(String code,String message){return new V3AuthException(code,message,HttpStatus.CONFLICT);}
    private static V3AuthException dependency(String message){return conflict("DEPENDENCY_NOT_READY",message);}
    private static V3AuthException persistence(){return new V3AuthException("VERIFICATION_PERSISTENCE_FAILED","The batch could not be acknowledged; retry its original identities and content.",HttpStatus.INTERNAL_SERVER_ERROR);}
}
