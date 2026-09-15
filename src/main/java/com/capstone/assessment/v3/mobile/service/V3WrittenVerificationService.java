package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.dto.V3WrittenVerificationBatch.*;
import com.capstone.assessment.v3.mobile.dto.V3VerificationResponse.Outcome;
import com.capstone.assessment.v3.mobile.dto.V3DetectionUploadResponse.IdMapping;
import com.capstone.assessment.v3.mobile.repository.*;
import com.capstone.assessment.v3.mobile.repository.V3DetectionRepository.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;

/** Initial teacher-written evaluations only. Per-result transactions reuse the durable verification batch ledger. */
@Service
@Profile("v3")
public class V3WrittenVerificationService {
    private final V3VerificationRepository batches;
    private final V3WrittenVerificationRepository written;
    private final V3DetectionRepository pages;
    private final V3ScanUploadLedgerRepository owners;
    private final V3EvaluationReferenceService references;
    private final V3AttachmentUploadService evidence;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    public V3WrittenVerificationService(V3VerificationRepository batches,V3WrittenVerificationRepository written,
            V3DetectionRepository pages,V3ScanUploadLedgerRepository owners,V3EvaluationReferenceService references,
            V3AttachmentUploadService evidence,Validator validator,ObjectMapper mapper,PlatformTransactionManager manager) {
        this.batches=batches;this.written=written;this.pages=pages;this.owners=owners;this.references=references;
        this.evidence=evidence;this.validator=validator;this.mapper=mapper.copy();transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public V3VerificationResponse verify(V3AuthenticatedUser user,V3WrittenVerificationBatch input) {
        if(user==null || !"teacher".equals(user.role()) || !"active".equals(user.status()) || user.schoolId()==null || user.schoolId().isBlank()) throw forbidden();
        validate(input);
        // Array order has no semantic meaning for these identities; bind the normalized request.
        var request=new V3WrittenVerificationBatch(input.contractVersion(),input.syncUuid(),input.operationUuid(),input.assignmentUuid(),
            input.items().stream().sorted(Comparator.comparing(Item::resultUuid)).map(i->new Item(i.resultUuid(),i.expectedRevision(),i.testVersionNumber(),i.evaluationReferenceHash(),
                i.pageDecisions().stream().sorted(Comparator.comparing(V3VerificationBatch.PageDecision::verificationUuid)).toList(),
                i.answers().stream().sorted(Comparator.comparing(Answer::answerUuid)).map(a->new Answer(a.answerUuid(),a.verificationUuid(),a.questionUuid(),a.regionUuid(),a.scanPageUuid(),canonical(a.evaluation()),a.comment(),a.clientDecidedAt())).toList())).toList());
        String json=json(request),hash=hash(json(List.of("written-verification-3.1",user.userId(),user.schoolId(),request)));
        try {transaction.executeWithoutResult(s->{
            long assignment=authorize(user,request,request.items());var old=batches.batch(request.operationUuid());
            if(old.isPresent()){match(old.get().hash(),hash);return;}
            if(batches.syncExists(request.syncUuid()))throw conflict("SYNC_IDENTITY_CONFLICT","Use a fresh sync UUID for each operation.");
            batches.register(user,assignment,request.syncUuid(),request.operationUuid(),request.items().stream().map(Item::resultUuid).toList(),hash,json);
        });}catch(DuplicateKeyException e){throw conflict("VERIFICATION_IDENTITY_CONFLICT","Batch identity is already reserved.");}
        catch(DataAccessException | TransactionException e){throw persistence();}
        var outcomes=new ArrayList<Outcome>();
        for(var item:request.items()) {
            try {outcomes.add(transaction.execute(s->{
                authorize(user,request,List.of(item));var batch=batches.batch(request.operationUuid()).orElseThrow();match(batch.hash(),hash);
                var receipt=batches.item(batch.syncId(),item.resultUuid());var old=decode(receipt.response());
                if(terminal(old))return old.replay();
                var result=batches.result(item.resultUuid()).orElseThrow(()->conflict("DEPENDENCY_NOT_READY","Commit the result before written verification."));
                if(!Set.of("draft","pending_verification").contains(result.status()))throw conflict("RESULT_LOCKED","Corrections require the later audited reopen workflow.");
                if(result.revision()!=item.expectedRevision())throw conflict("REVISION_CONFLICT","Read the current result and submit a new operation.");
                if(result.revision()>=9007199254740991L)throw conflict("REVISION_LIMIT","Result revision cannot advance.");
                var reference=references.lockedForVerification(user,request.assignmentUuid());
                if(reference.testVersionNumber()!=item.testVersionNumber() || !reference.evaluationReferenceHash().equals(item.evaluationReferenceHash()))
                    throw conflict("EVALUATION_REFERENCE_STALE","Refresh the scoring reference and have the teacher review scores before a new operation.");
                written.reference(receipt.id(),reference.testVersionNumber(),reference.evaluationReferenceHash(),json(reference));
                var scans=new HashSet<Long>();var mappings=new ArrayList<IdMapping>();
                for(var decision:item.pageDecisions()) {
                    var page=page(user,decision.scanPageUuid(),result.id());
                    if(!Set.of("captured","needs_verification").contains(page.pageStatus()))throw conflict("PAGE_DECISION_LOCKED","Existing page decisions cannot be changed here.");
                    unique(decision.verificationUuid());batches.pageDecision(user,request.operationUuid(),page,decision);scans.add(page.scanId());
                }
                for(var answer:item.answers()) {
                    var page=page(user,answer.scanPageUuid(),result.id());
                    if(!"accepted".equals(page.pageStatus()))throw conflict("PAGE_NOT_ACCEPTED","Accept the scanned page before its written answers.");
                    var region=pages.region(page,new V3DetectionBatch.Detection(null,answer.regionUuid(),answer.questionUuid(),null,null,null))
                        .orElseThrow(()->conflict("VERIFICATION_REGION_MISMATCH","Question and region must belong to the immutable result page."));
                    if(!"written_response".equals(region.regionType()) || !Set.of("essay","identification","enumeration").contains(region.type()))
                        throw conflict("WRITTEN_REGION_REQUIRED","Only written-response question regions are accepted by contract 3.1.");
                    unique(answer.verificationUuid());
                    if(batches.answerExists(answer.answerUuid(),result.id(),region.questionId()))throw conflict("ANSWER_ALREADY_VERIFIED","Existing answers require the later correction workflow.");
                    var question=reference.questions().stream().filter(q->q.questionUuid().equals(answer.questionUuid())).findFirst().orElseThrow(()->invalid("Question reference is missing."));
                    var value=answer.evaluation();Integer maxLength=written.maximumResponseLength(region.questionId());
                    if(value.responseText()!=null && maxLength!=null && value.responseText().codePointCount(0,value.responseText().length())>maxLength)
                        throw conflict("RESPONSE_TEXT_TOO_LONG","Response exceeds the question maximum response length.");
                    BigDecimal points=points(value,question,reference,region.type());
                    var attachmentIds=new ArrayList<Long>();
                    for(String uuid:value.attachmentUuids())attachmentIds.add(evidence.evidenceForScoring(page,uuid,region.id()));
                    long id=written.answer(user,page,region.questionId(),receipt.id(),answer,points,attachmentIds,json(answer));
                    if(id>9007199254740991L)throw conflict("IDENTITY_LIMIT","Answer ID exceeds the Mobile range.");
                    mappings.add(new IdMapping("student_answer",answer.answerUuid(),id));scans.add(page.scanId());
                }
                batches.finishResult(result.id(),user,scans);
                var outcome=new Outcome(item.resultUuid(),"success","created",result.revision()+1,List.copyOf(mappings),null);
                batches.outcome(batch.syncId(),receipt.id(),result.id(),outcome,json(outcome));return outcome;
            }));}catch(V3AuthException e){
                if(e.getStatus()==HttpStatus.FORBIDDEN)throw e;
                outcomes.add(failure(user,request,item,hash,e.getCode(),e.getMessage(),Set.of("DEPENDENCY_NOT_READY","SCAN_EVIDENCE_NOT_FOUND","SCAN_EVIDENCE_STORAGE_FAILED").contains(e.getCode())));
            }catch(DuplicateKeyException e){outcomes.add(failure(user,request,item,hash,"VERIFICATION_IDENTITY_CONFLICT","An answer or verification identity is already recorded.",false));}
            catch(DataAccessException | TransactionException e){outcomes.add(failure(user,request,item,hash,"VERIFICATION_PERSISTENCE_FAILED","Retry the same operation and content.",true));}
        }
        long count=outcomes.stream().filter(o->"success".equals(o.status())).count();
        return new V3VerificationResponse(request.syncUuid(),request.operationUuid(),count==outcomes.size()?"success":count==0?"failed":"partial_success",List.copyOf(outcomes));
    }
    /** Shared teacher-score rules; performs no persistence and requires the caller's locked current reference. */
    public BigDecimal validateCorrectionEvaluation(Evaluation value,V3EvaluationReference.Question question,V3EvaluationReference ref,String type,long questionId) {
        if(value==null || !validator.validate(value).isEmpty())throw invalid("Written evaluation is invalid.");
        if(value.responseText()!=null && value.responseText().isBlank())throw invalid("Use null for absent transcription.");
        if("answered".equals(value.answerStatus()) && value.responseText()==null && value.attachmentUuids().isEmpty())throw invalid("Answered responses require text or evidence.");
        if("blank".equals(value.answerStatus()) && value.responseText()!=null)throw invalid("Blank answers cannot contain text.");
        if(new HashSet<>(value.attachmentUuids()).size()!=value.attachmentUuids().size())throw invalid("Duplicate attachment.");
        if(value instanceof Rubric r && r.criterionScores().stream().map(CriterionScore::rubricCriterionId).distinct().count()!=r.criterionScores().size())throw invalid("Duplicate criterion.");
        Integer maximum=written.maximumResponseLength(questionId);
        if(value.responseText()!=null && maximum!=null && value.responseText().codePointCount(0,value.responseText().length())>maximum)throw conflict("RESPONSE_TEXT_TOO_LONG","Response exceeds the question limit.");
        return points(value,question,ref,type);
    }
    private BigDecimal points(Evaluation value,V3EvaluationReference.Question question,V3EvaluationReference ref,String type) {
        BigDecimal total;
        if(value instanceof Manual manual) {
            if(question.rubricId()!=null)throw conflict("RUBRIC_REQUIRED","The essay has an assigned rubric; submit criterion scores.");
            total=manual.points();
        }else {
            var rubric=(Rubric)value;
            if(!"essay".equals(type) || !Objects.equals(question.rubricId(),rubric.rubricId()))throw conflict("RUBRIC_MISMATCH","The rubric must be assigned to this essay.");
            var definition=ref.rubrics().stream().filter(r->r.rubricId()==rubric.rubricId()).findFirst().orElseThrow(()->invalid("Rubric reference missing."));
            if(definition.criteria().size()!=rubric.criterionScores().size())throw conflict("RUBRIC_SCORE_INCOMPLETE","Every rubric criterion requires a score.");
            total=BigDecimal.ZERO;
            for(var score:rubric.criterionScores()) {
                var criterion=definition.criteria().stream().filter(c->c.rubricCriterionId()==score.rubricCriterionId()).findFirst().orElseThrow(()->conflict("RUBRIC_MISMATCH","Criterion does not belong to this rubric."));
                if(score.pointsAwarded().compareTo(criterion.maximumPoints())>0)throw conflict("RUBRIC_SCORE_OUT_OF_RANGE","Criterion points exceed their maximum.");
                total=total.add(score.pointsAwarded());
            }
        }
        if(total.compareTo(question.maximumPoints())>0)throw conflict("MANUAL_SCORE_OUT_OF_RANGE","Points exceed the question maximum.");
        if("blank".equals(value.answerStatus()) && total.signum()!=0)throw conflict("BLANK_ANSWER_HAS_POINTS","Blank answers require zero points.");
        return total.setScale(2,java.math.RoundingMode.UNNECESSARY);
    }
    private Outcome failure(V3AuthenticatedUser user,V3WrittenVerificationBatch r,Item item,String hash,String code,String message,boolean retryable) {
        try{return transaction.execute(s->{authorize(user,r,List.of(item));var batch=batches.batch(r.operationUuid()).orElseThrow();match(batch.hash(),hash);
            var receipt=batches.item(batch.syncId(),item.resultUuid());var old=decode(receipt.response());if(terminal(old))return old.replay();
            var result=batches.result(item.resultUuid());var outcome=new Outcome(item.resultUuid(),"failed","rejected",null,List.of(),new V3VerificationResponse.Error(code,message,retryable));
            batches.outcome(batch.syncId(),receipt.id(),result.map(V3VerificationRepository.Result::id).orElse(null),outcome,json(outcome));return outcome;
        });}catch(DataAccessException | TransactionException e){throw persistence();}
    }
    private long authorize(V3AuthenticatedUser user,V3WrittenVerificationBatch r,List<Item> items) {
        owners.lockOwner(user.userId(),user.schoolId()).orElseThrow(this::forbidden);
        long assignment=batches.assignment(user,r.assignmentUuid()).orElseThrow(this::forbidden);
        for(var item:items) {
            var result=batches.result(item.resultUuid());
            if(result.isPresent() && (result.get().assignmentId()!=assignment || !batches.resultInSchool(result.get().id(),user.schoolId())))throw forbidden();
            var ids=new TreeSet<String>();item.pageDecisions().forEach(p->ids.add(p.scanPageUuid()));item.answers().forEach(a->ids.add(a.scanPageUuid()));
            for(String uuid:ids)if(batches.pageExists(uuid)) {
                var page=pages.lockPage(user,uuid,true).orElseThrow(this::forbidden);
                if(!page.resultUuid().equals(item.resultUuid()) || page.assignmentId()!=assignment)throw forbidden();
            }
        }
        return assignment;
    }
    private Page page(V3AuthenticatedUser user,String uuid,long result) {
        var p=pages.lockPage(user,uuid,false).orElseThrow(()->conflict("DEPENDENCY_NOT_READY","An eligible committed scan page is required."));
        if(p.resultId()!=result)throw forbidden();
        if(!"selected".equals(p.linkStatus()) || !Set.of("captured","needs_verification","accepted").contains(p.scanStatus()))throw conflict("SCAN_LOCKED","Scan cannot receive initial verification.");
        if(p.sheetVersion()!=p.testVersion())throw conflict("ASSESSMENT_SNAPSHOT_MISMATCH","Assessment differs from its sheet snapshot.");return p;
    }
    private void validate(V3WrittenVerificationBatch r) {
        if(r==null || !validator.validate(r).isEmpty())throw invalid("Written verification metadata is invalid.");
        var results=new HashSet<String>();var answers=new HashSet<String>();var decisions=new HashSet<String>();var attachments=new HashSet<String>();
        for(var item:r.items()) {
            if(!results.add(item.resultUuid()))throw invalid("Duplicate result.");var questions=new HashSet<String>();var pageIds=new HashSet<String>();
            for(var p:item.pageDecisions())if(!decisions.add(p.verificationUuid()) || !pageIds.add(p.scanPageUuid()) || !"accepted".equals(p.action()))throw invalid("Written submission page decisions must be unique initial acceptances.");
            for(var a:item.answers()) {
                if(!answers.add(a.answerUuid()) || !decisions.add(a.verificationUuid()) || !questions.add(a.questionUuid()))throw invalid("Duplicate answer/question/verification.");
                var e=a.evaluation();
                if(e.responseText()!=null && e.responseText().isBlank())throw invalid("Use null for absent transcription.");
                if("answered".equals(e.answerStatus()) && e.responseText()==null && e.attachmentUuids().isEmpty())throw invalid("Answered responses require actual text or uploaded evidence.");
                if("blank".equals(e.answerStatus()) && e.responseText()!=null)throw invalid("Blank answers cannot contain response text.");
                for(String uuid:e.attachmentUuids())if(!attachments.add(uuid))throw invalid("Each evidence attachment can belong to one answer only.");
                if(e instanceof Rubric rubric){var ids=new HashSet<Long>();for(var score:rubric.criterionScores())if(!ids.add(score.rubricCriterionId()))throw invalid("Duplicate rubric criterion.");}
            }
        }
    }
    private Evaluation canonical(Evaluation e){return e instanceof Manual m?new Manual(m.answerStatus(),m.responseText(),m.attachmentUuids().stream().sorted().toList(),m.points().setScale(2)):
        new Rubric(e.answerStatus(),e.responseText(),e.attachmentUuids().stream().sorted().toList(),((Rubric)e).rubricId(),((Rubric)e).criterionScores().stream().sorted(Comparator.comparing(CriterionScore::rubricCriterionId)).map(c->new CriterionScore(c.rubricCriterionId(),c.pointsAwarded().setScale(2),c.comment())).toList());}
    private void unique(String uuid){if(batches.verificationExists(uuid))throw conflict("VERIFICATION_IDENTITY_CONFLICT","Verification UUID is already recorded.");}
    private boolean terminal(Outcome o){return o!=null && ("success".equals(o.status()) || !o.error().retryable());}
    private Outcome decode(String s){if(s==null)return null;try{return mapper.readValue(s,Outcome.class);}catch(java.io.IOException e){throw new IllegalStateException("Invalid stored receipt",e);}}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private String hash(String s){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void match(String a,String b){if(!a.equals(b))throw conflict("VERIFICATION_IDENTITY_CONFLICT","Operation is bound to different content.");}
    private V3AuthException invalid(String m){return new V3AuthException("VALIDATION_FAILED",m,HttpStatus.UNPROCESSABLE_ENTITY);}
    private V3AuthException conflict(String c,String m){return new V3AuthException(c,m,HttpStatus.CONFLICT);}
    private V3AuthException forbidden(){return new V3AuthException("VERIFICATION_FORBIDDEN","Teacher must currently own the assignment, results and pages.",HttpStatus.FORBIDDEN);}
    private V3AuthException persistence(){return new V3AuthException("VERIFICATION_PERSISTENCE_FAILED","Retry the original operation and content.",HttpStatus.INTERNAL_SERVER_ERROR);}
}
