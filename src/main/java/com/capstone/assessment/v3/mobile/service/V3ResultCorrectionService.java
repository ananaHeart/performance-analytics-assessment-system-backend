package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.dto.V3CorrectionRequest.*;
import com.capstone.assessment.v3.mobile.repository.*;
import com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ResultContext;
import com.capstone.assessment.v3.scoring.repository.V3ScoringRepository;
import com.capstone.assessment.v3.scoring.service.V3ScoringService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.util.*;

/** One complete teacher review plus official re-finalization, atomically, for one existing reopened result. */
@Service
@Profile("v3")
public class V3ResultCorrectionService {
    private final V3ResultCorrectionRepository repository;
    private final V3ResultReopenRepository ownership;
    private final V3ScanUploadLedgerRepository owners;
    private final V3ScoringRepository scoring;
    private final V3ScoringService scorer;
    private final V3MobileFinalizationRepository finalizations;
    private final V3VerificationRepository verifications;
    private final V3DetectionRepository pages;
    private final V3EvaluationReferenceService references;
    private final V3WrittenVerificationService writtenRules;
    private final V3AttachmentUploadService evidence;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    public V3ResultCorrectionService(V3ResultCorrectionRepository repository,V3ResultReopenRepository ownership,V3ScanUploadLedgerRepository owners,
            V3ScoringRepository scoring,V3ScoringService scorer,V3MobileFinalizationRepository finalizations,V3VerificationRepository verifications,
            V3DetectionRepository pages,V3EvaluationReferenceService references,V3WrittenVerificationService writtenRules,V3AttachmentUploadService evidence,
            Validator validator,ObjectMapper mapper,PlatformTransactionManager manager,@Value("${app.v3.mobile.correction-enabled:false}") boolean enabled){
        this.repository=repository;this.ownership=ownership;this.owners=owners;this.scoring=scoring;this.scorer=scorer;this.finalizations=finalizations;
        this.verifications=verifications;this.pages=pages;this.references=references;this.writtenRules=writtenRules;this.evidence=evidence;this.validator=validator;this.mapper=mapper.copy();this.enabled=enabled;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public V3ScoredResultResponse correct(V3AuthenticatedUser user,String uuid,V3CorrectionRequest request){
        if(user==null)throw error("AUTHENTICATION_REQUIRED","Authentication is required.",HttpStatus.UNAUTHORIZED);
        if(!"teacher".equals(user.role()) || !"active".equals(user.status()) || user.schoolId()==null || user.schoolId().isBlank())throw forbidden();
        validate(uuid,request);
        if(!enabled)throw error("MOBILE_CORRECTION_UNAVAILABLE","Correction upload awaits reviewed deployment and coordinated release.",HttpStatus.SERVICE_UNAVAILABLE);
        String requestJson=json(request),hash=hash(json(List.of("mobile-correction-3.2",user.userId(),user.schoolId(),uuid,request)));
        try{return transaction.execute(s->{
            if(owners.lockOwner(user.userId(),user.schoolId()).isEmpty())throw forbidden();
            long result=ownership.ownedResult(user,uuid).orElseThrow(()->error("RESOURCE_NOT_FOUND","The owned result was not found.",HttpStatus.NOT_FOUND));
            var saved=repository.receipt(request.operationUuid());
            if(saved.isPresent()){
                var old=saved.get();if(old.result()!=result || old.teacher()!=user.userId() || !old.hash().equals(hash) || !old.syncUuid().equals(request.syncUuid()))throw conflict("CORRECTION_IDENTITY_CONFLICT","Operation is bound to different content.");
                var response=parse(old.json());
                if(response.testResultId()!=result || !uuid.equals(response.resultUuid()) || response.scoreVersion()!=old.version() || old.version()!=request.expectedScoreVersion()+1)throw conflict("CORRECTION_STATE_CONFLICT","Saved correction identity requires reconciliation.");
                return replay(response);
            }
            if(ownership.syncExists(request.syncUuid()))throw conflict("SYNC_IDENTITY_CONFLICT","Each operation requires a distinct sync UUID.");
            var context=scoring.findResultContextForUpdate(result).orElseThrow();
            if(!"pending_verification".equals(context.resultStatus()))throw conflict("RESULT_NOT_REOPENED","Reopen the official result before submitting a correction.");
            long revision=finalizations.revision(result);
            if(revision!=request.expectedRevision() || context.scoreVersion()!=request.expectedScoreVersion())throw conflict("REVISION_CONFLICT","Reconcile current revision and score version before a new correction operation.");
            if(revision>=9007199254740991L)throw conflict("RESULT_REVISION_EXHAUSTED","Revision cannot advance safely.");
            if(context.scoreVersion()>=Integer.MAX_VALUE)throw conflict("SCORE_VERSION_EXHAUSTED","Score version cannot advance safely.");
            var reopen=repository.reopen(request.reopenOperationUuid()).orElseThrow(()->conflict("RESULT_NOT_REOPENED","A committed reopen operation is required."));
            var official=finalizations.receipt(result).orElseThrow(()->conflict("OFFICIAL_SCORE_UNAVAILABLE","The previous official receipt is required."));
            if(reopen.result()!=result || reopen.version()!=context.scoreVersion() || reopen.revision()>revision || reopen.previousRevision()!=official.revision()
                    || official.scoreVersion()!=context.scoreVersion() || !reopen.official().equals(official.json()) || repository.usedReopen(request.reopenOperationUuid()))
                throw conflict("REOPEN_STATE_CONFLICT","The reopen must match the unconsumed current official score.");
            String assignment=repository.assignmentUuid(context.testAssignmentId());var reference=references.lockedForVerification(user,assignment);
            if(reference.testVersionNumber()!=request.testVersionNumber() || !reference.evaluationReferenceHash().equals(request.evaluationReferenceHash()))throw conflict("EVALUATION_REFERENCE_STALE","Refresh the reference and have the teacher review the complete result.");
            var rows=scoring.findScoringRowsForUpdate(context.testId(),result);
            if(rows.isEmpty() || rows.size()>200 || rows.size()!=request.answers().size() || scoring.countAnswersOutsideTest(result,context.testId())!=0)throw conflict("CORRECTION_COVERAGE_INVALID","Submit one reviewed evaluation for every existing question.");
            var submitted=new HashMap<String,Answer>();request.answers().forEach(a->submitted.put(a.questionUuid(),a));
            var audits=new ArrayList<Changed>();int nextVersion=context.scoreVersion()+1;
            for(var old:rows){
                var answer=submitted.get(repository.questionUuid(old.questionId()));
                if(answer==null || old.studentAnswerId()==null || !answer.answerUuid().equals(old.answerUuid()) || old.answerScoreVersion()!=context.scoreVersion()
                        || !"finalized".equals(old.evaluationStatus()) || old.verifiedAt()==null || old.finalizedAt()==null)throw conflict("CORRECTION_COVERAGE_INVALID","Answer identities and prior accepted versions must match the reopened result.");
                if(verifications.verificationExists(answer.verificationUuid()) || repository.verificationExists(answer.verificationUuid()))throw conflict("CORRECTION_IDENTITY_CONFLICT","Use a fresh verification UUID for each reviewed answer.");
                var page=pages.lockPage(user,answer.scanPageUuid(),true).orElseThrow(this::forbidden);
                if(page.resultId()!=result || !"selected".equals(page.linkStatus()) || !"accepted".equals(page.pageStatus()) || !"accepted".equals(page.scanStatus())
                        || page.sheetVersion()!=reference.testVersionNumber())throw conflict("CORRECTION_CAPTURE_INVALID","Correction requires the same accepted current sheet capture.");
                var region=pages.region(page,new V3DetectionBatch.Detection(null,answer.regionUuid(),answer.questionUuid(),null,null,null)).orElseThrow(()->conflict("VERIFICATION_REGION_MISMATCH","Question and region must match the retained page."));
                if(region.questionId()!=old.questionId())throw conflict("VERIFICATION_REGION_MISMATCH","Wrong question region.");
                String before=json(repository.snapshot(old.studentAnswerId()));BigDecimal points=BigDecimal.ZERO;Long option=null,detection=null;
                var attachmentIds=new ArrayList<Long>();
                if(answer.evaluation() instanceof Objective objective){
                    if(!Set.of("multiple_choice","true_false").contains(region.type()) || !"objective_bubbles".equals(region.regionType()))throw conflict("OBJECTIVE_REGION_REQUIRED","Objective correction requires an objective region.");
                    var source=verifications.detection(objective.detectionUuid(),page.pageId(),region.id(),region.questionId()).orElseThrow(()->conflict("DETECTION_NOT_FOUND","Retain the original detection for this answer."));
                    if(!repository.originalDetection(old.studentAnswerId(),source.id()))throw conflict("DETECTION_NOT_FOUND","Detection must be the original accepted answer evidence.");
                    detection=source.id();
                    if(objective.selectedOption()!=null){
                        if("true_false".equals(region.type()) && !Set.of("A","B").contains(objective.selectedOption()))throw invalid("True/false accepts only A or B.");
                        option=repository.option(old.questionId(),objective.selectedOption()).orElseThrow(()->conflict("CORRECTION_OPTION_INVALID","Selected option must be active on this question."));
                    }
                }else{
                    if(!Set.of("essay","identification","enumeration").contains(region.type()) || !"written_response".equals(region.regionType()))throw conflict("WRITTEN_REGION_REQUIRED","Written correction requires a written region.");
                    var value=V3CorrectionRequest.written(answer.evaluation());
                    var question=reference.questions().stream().filter(q->q.questionUuid().equals(answer.questionUuid())).findFirst().orElseThrow();
                    points=writtenRules.validateCorrectionEvaluation(value,question,reference,region.type(),old.questionId());
                    for(String attachment:value.attachmentUuids().stream().sorted().toList()){
                        long id=evidence.evidenceForScoring(page,attachment,region.id());repository.linkEvidence(old.studentAnswerId(),id);attachmentIds.add(id);
                    }
                }
                Long verification=repository.updateAnswer(user,old,answer,nextVersion,option,points,attachmentIds,reopen.at(),request.reasonCode(),json(answer));
                audits.add(new Changed(answer,old.studentAnswerId(),detection,verification,before));
            }
            // All teacher decisions, reference locks and evidence checks remain in this transaction through scoring commit.
            var response=scorer.finalizeCorrection(user,result,new ValidatedCorrection(context,revision,official));
            repository.save(user,result,context.testAssignmentId(),request,hash,requestJson,json(reference),json(response),revision+1,response.scoreVersion());
            for(var audit:audits)repository.answerAudit(request.operationUuid(),audit.answer(),audit.id(),audit.detection(),audit.verification(),audit.before(),json(repository.snapshot(audit.id())));
            return response;
        });}catch(DuplicateKeyException e){throw conflict("CORRECTION_IDENTITY_CONFLICT","An operation, reopen, verification or score version is already reserved.");}
        catch(DataAccessException | TransactionException e){throw error("CORRECTION_PERSISTENCE_UNAVAILABLE","Retry the identical correction request; commit acknowledgement may have been lost.",HttpStatus.SERVICE_UNAVAILABLE);}
    }
    private void validate(String uuid,V3CorrectionRequest r){
        if(uuid==null || !uuid.matches(V3ReopenRequest.UUID) || r==null || !validator.validate(r).isEmpty())throw invalid("Use the explicit 3.2 complete-result correction contract.");
        var answers=new HashSet<String>();var questions=new HashSet<String>();var decisions=new HashSet<String>();var attachments=new HashSet<String>();
        for(var a:r.answers()){
            if(!answers.add(a.answerUuid()) || !questions.add(a.questionUuid()) || !decisions.add(a.verificationUuid()))throw invalid("Duplicate answer, question or verification.");
            if(a.evaluation() instanceof Objective o){if("answered".equals(o.answerStatus())!=(o.selectedOption()!=null))throw invalid("Answered objective decisions require one option; blank requires null.");}
            else for(String attachment:V3CorrectionRequest.written(a.evaluation()).attachmentUuids())if(!attachments.add(attachment))throw invalid("Evidence may be submitted for only one answer.");
        }
    }
    private V3ScoredResultResponse parse(String value){
        try{var r=mapper.readValue(value,V3ScoredResultResponse.class);
            if(r==null || r.totalScore()==null || r.maxScore()==null || r.percentage()==null || r.scoredAt()==null || !"finalized".equals(r.resultStatus())
                    || !r.scoreChanged() || r.parts()==null || r.parts().isEmpty() || r.parts().stream().anyMatch(Objects::isNull))throw conflict("CORRECTION_STATE_CONFLICT","Invalid saved correction response.");return r;
        }catch(java.io.IOException e){throw conflict("CORRECTION_STATE_CONFLICT","Invalid saved correction response.");}
    }
    private V3ScoredResultResponse replay(V3ScoredResultResponse r){return new V3ScoredResultResponse(r.testResultId(),r.resultUuid(),r.testAssignmentId(),r.testId(),r.classListId(),r.studentId(),r.studentName(),r.attemptNumber(),r.totalScore(),r.maxScore(),r.percentage(),r.performanceStatus(),r.performanceLabel(),r.performanceRuleSetId(),r.itemsEvaluated(),r.scoreVersion(),r.resultStatus(),r.scoredAt(),false,r.parts());}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private String hash(String v){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private V3AuthException invalid(String m){return error("VALIDATION_FAILED",m,HttpStatus.UNPROCESSABLE_ENTITY);}
    private V3AuthException forbidden(){return error("RESULT_ACCESS_DENIED","The active teacher must own the result and original capture.",HttpStatus.FORBIDDEN);}
    private V3AuthException conflict(String c,String m){return error(c,m,HttpStatus.CONFLICT);}
    private V3AuthException error(String c,String m,HttpStatus s){return new V3AuthException(c,m,s);}
    private record Changed(Answer answer,long id,Long detection,Long verification,String before) { }

    /** Unforgeable by request DTOs; constructed only after every answer is checked, and valid only in the originating transaction. */
    public static final class ValidatedCorrection {
        private final long result,teacher,revision;
        private final int previousVersion;
        private final V3MobileFinalizationRepository.Receipt receipt;
        private final Thread thread=Thread.currentThread();
        private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private ValidatedCorrection(ResultContext c,long revision,V3MobileFinalizationRepository.Receipt receipt){this.result=c.testResultId();this.teacher=c.teacherUserId();this.previousVersion=c.scoreVersion();this.revision=revision;this.receipt=receipt;}
        boolean matches(ResultContext c,long currentRevision,V3MobileFinalizationRepository.Receipt saved){return Thread.currentThread()==thread && TransactionSynchronizationManager.isActualTransactionActive()
                && !resources.isEmpty() && resources.equals(TransactionSynchronizationManager.getResourceMap()) && c.testResultId()==result && c.teacherUserId()==teacher
                && "pending_verification".equals(c.resultStatus()) && c.scoreVersion()==previousVersion && currentRevision==revision && receipt.equals(saved);}
        int targetVersion(){return previousVersion+1;}
    }
}
