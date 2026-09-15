package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.dto.V3WrittenVerificationBatch.*;
import com.capstone.assessment.v3.mobile.repository.V3WrittenFinalizationRepository;
import com.capstone.assessment.v3.mobile.repository.V3DetectionRepository;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ResultContext;
import com.capstone.assessment.v3.scoring.repository.V3ScoringRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/** Validates accepted written facts before the existing scorer computes official result totals. Never changes teacher scores. */
@Service
@Profile("v3")
public class V3WrittenFinalizationService {
    private final V3WrittenFinalizationRepository repository;
    private final V3ScoringRepository scoring;
    private final V3EvaluationReferenceService references;
    private final V3AttachmentUploadService evidence;
    private final V3DetectionRepository pages;
    private final ObjectMapper mapper;
    private final Validator validator;
    public V3WrittenFinalizationService(V3WrittenFinalizationRepository repository,V3ScoringRepository scoring,
            V3EvaluationReferenceService references,V3AttachmentUploadService evidence,V3DetectionRepository pages,ObjectMapper mapper,Validator validator) {
        this.repository=repository;this.scoring=scoring;this.references=references;this.evidence=evidence;this.pages=pages;this.mapper=mapper.copy();this.validator=validator;
    }
    public void prepare(ResultContext context,int expected,long revision) {
        var user=new V3AuthenticatedUser(context.teacherUserId(),context.testSchoolId(),"","teacher","active","");
        String assignment=repository.assignmentUuid(context.testAssignmentId());
        var current=references.lockedForVerification(user,assignment);
        var rows=scoring.findScoringRowsForUpdate(context.testId(),context.testResultId()).stream()
                .filter(r->Set.of("essay","identification","enumeration").contains(r.questionTypeCode())).toList();
        if(rows.size()!=expected || rows.size()>200)throw incomplete();
        for(var row:rows) {
            if(row.studentAnswerId()==null || !"finalized".equals(row.evaluationStatus()) || row.verifiedAt()==null || row.finalizedAt()==null
                    || !Objects.equals(row.verifiedByUserId(),context.teacherUserId()) || row.answerScoreVersion()!=1)throw incomplete();
            var state=repository.state(row.studentAnswerId());
            if(state.reopened() || !"manual".equals(state.source()) || row.selectedQuestionOptionId()!=null)throw mismatch();
            var audits=repository.audits(row.studentAnswerId());if(audits.size()!=1)throw incomplete();var audit=audits.get(0);
            if(audit.teacher()!=context.teacherUserId() || !"manual_scored".equals(audit.action()) || !"success".equals(audit.itemStatus())
                    || audit.resultId()!=context.testResultId() || !context.resultUuid().equals(audit.resultUuid())
                    || audit.assignmentId()!=context.testAssignmentId() || audit.syncUser()!=context.teacherUserId())throw incomplete();
            var savedReference=parse(audit.referenceJson(),V3EvaluationReference.class);
            if(audit.version()!=current.testVersionNumber() || !Objects.equals(audit.hash(),current.evaluationReferenceHash()))
                throw conflict("WRITTEN_REFERENCE_STALE","Scoring references changed after teacher verification; official finalization requires audited reconciliation.");
            if(!current.equals(savedReference))throw mismatch();
            var answer=parse(audit.evaluationJson(),Answer.class);
            var request=parse(audit.requestJson(),V3WrittenVerificationBatch.class);
            if(!validator.validate(answer).isEmpty() || !validator.validate(request).isEmpty()
                    || !assignment.equals(request.assignmentUuid()) || !Objects.equals(audit.operation(),request.operationUuid())
                    || !Objects.equals(audit.syncUuid(),request.syncUuid()))throw mismatch();
            var items=request.items().stream().filter(i->context.resultUuid().equals(i.resultUuid())).toList();if(items.size()!=1)throw mismatch();var item=items.get(0);
            if(item.testVersionNumber()!=audit.version() || !item.evaluationReferenceHash().equals(audit.hash())
                    || item.answers().stream().filter(a->a.answerUuid().equals(answer.answerUuid())).count()!=1
                    || item.answers().stream().noneMatch(answer::equals))throw mismatch();
            var receipt=parse(audit.receiptJson(),V3VerificationResponse.Outcome.class);
            if(!"success".equals(receipt.status()) || !context.resultUuid().equals(receipt.resultUuid()) || receipt.revision()==null
                    || receipt.revision()!=item.expectedRevision()+1 || receipt.revision()>revision || receipt.idMappings()==null
                    || receipt.idMappings().stream().anyMatch(Objects::isNull)
                    || receipt.idMappings().stream().noneMatch(m->"student_answer".equals(m.entityType()) && Objects.equals(m.uuid(),answer.answerUuid()) && m.centralId()==row.studentAnswerId()))throw mismatch();
            if(!answer.answerUuid().equals(row.answerUuid()) || !answer.verificationUuid().equals(audit.uuid())
                    || !answer.questionUuid().equals(repository.questionUuid(row.questionId()))
                    || !Objects.equals(answer.comment(),state.feedback()) || !Objects.equals(answer.comment(),audit.comment())
                    || !Objects.equals(answer.clientDecidedAt().toString(),audit.clientTime())
                    || !Objects.equals(answer.evaluation().answerStatus(),row.answerStatus()) || !Objects.equals(row.answerStatus(),audit.status())
                    || !Objects.equals(answer.evaluation().responseText(),row.responseText()) || !Objects.equals(row.responseText(),audit.text())
                    || !same(row.pointsEarned(),audit.points()) || !Objects.equals(state.evidenceId(),audit.evidenceId()))throw mismatch();
            var page=pages.lockPage(user,answer.scanPageUuid(),true).orElseThrow(this::incomplete);
            if(page.resultId()!=context.testResultId() || !"selected".equals(page.linkStatus()) || !"accepted".equals(page.pageStatus())
                    || !"accepted".equals(page.scanStatus()) || page.sheetVersion()!=current.testVersionNumber())throw incomplete();
            var region=pages.region(page,new V3DetectionBatch.Detection(null,answer.regionUuid(),answer.questionUuid(),null,null,null)).orElseThrow(this::incomplete);
            if(region.questionId()!=row.questionId() || !"written_response".equals(region.regionType()) || !region.type().equals(row.questionTypeCode()))throw mismatch();
            var attachments=repository.attachments(row.studentAnswerId());var submitted=answer.evaluation().attachmentUuids().stream().sorted().toList();
            if(new HashSet<>(submitted).size()!=submitted.size() || !submitted.equals(attachments.stream().map(V3WrittenFinalizationRepository.Attachment::uuid).toList())
                    || !Objects.equals(state.evidenceId(),attachments.isEmpty()?null:attachments.get(0).id()))throw mismatch();
            for(var attachment:attachments)if(evidence.evidenceForScoring(page,attachment.uuid(),region.id())!=attachment.id())throw mismatch();
            if("answered".equals(row.answerStatus()) && (row.responseText()==null || row.responseText().isBlank()) && attachments.isEmpty())throw incomplete();
            if("blank".equals(row.answerStatus()) && row.responseText()!=null)throw mismatch();
            var criteria=repository.criteria(row.studentAnswerId());BigDecimal total=BigDecimal.ZERO;
            if(answer.evaluation() instanceof Manual manual) {
                if(!criteria.isEmpty() || row.questionRubricId()!=null)throw mismatch();total=manual.points();
            }else {
                var rubric=(Rubric)answer.evaluation();
                if(!"essay".equals(row.questionTypeCode()) || !Objects.equals(row.questionRubricId(),rubric.rubricId()))throw mismatch();
                var scores=rubric.criterionScores().stream().sorted(Comparator.comparing(CriterionScore::rubricCriterionId)).toList();
                if(scores.size()!=criteria.size() || scores.stream().map(CriterionScore::rubricCriterionId).distinct().count()!=scores.size())throw mismatch();
                for(int n=0;n<scores.size();n++) {
                    var score=scores.get(n);var stored=criteria.get(n);
                    if(score.rubricCriterionId()!=stored.id() || !same(score.pointsAwarded(),stored.points()) || !Objects.equals(score.comment(),stored.comment())
                            || stored.verification()!=audit.id() || stored.teacher()!=context.teacherUserId() || stored.version()!=1)throw mismatch();
                    total=total.add(score.pointsAwarded());
                }
            }
            // Current question/rubric bounds are also checked by the existing scorer while these reference locks remain held.
            if(!same(total,row.pointsEarned()))throw mismatch();
        }
    }
    private boolean same(BigDecimal a,BigDecimal b){return a!=null && b!=null && a.compareTo(b)==0;}
    private <T>T parse(String json,Class<T> type){if(json==null)throw incomplete();try{var value=mapper.readValue(json,type);if(value==null)throw mismatch();return value;}catch(java.io.IOException | IllegalArgumentException e){throw mismatch();}}
    private V3AuthException incomplete(){return conflict("WRITTEN_VERIFICATION_INCOMPLETE","Every written answer requires its accepted teacher audit, reference receipt and evidence context.");}
    private V3AuthException mismatch(){return conflict("WRITTEN_AUDIT_MISMATCH","Stored written answers, rubric scores, evidence or references differ from their accepted audit.");}
    private V3AuthException conflict(String code,String message){return new V3AuthException(code,message,HttpStatus.CONFLICT);}
}
