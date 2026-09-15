package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3EvaluationReference;
import com.capstone.assessment.v3.mobile.dto.V3EvaluationReference.*;
import com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository;
import com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository.QuestionRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@Profile("v3")
public class V3EvaluationReferenceService {
    private final V3EvaluationReferenceRepository repository;
    private final TransactionTemplate snapshot;
    private final boolean enabled;
    public V3EvaluationReferenceService(V3EvaluationReferenceRepository repository,PlatformTransactionManager manager,
            @Value("${app.v3.mobile.evaluation-reference-enabled:false}") boolean enabled) {
        this.repository=repository;this.enabled=enabled;snapshot=new TransactionTemplate(manager);
        snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);snapshot.setReadOnly(true);
    }
    public V3EvaluationReference get(V3AuthenticatedUser user,String uuid) {
        if(user==null) throw error("AUTHENTICATION_REQUIRED","Authentication is required.",HttpStatus.UNAUTHORIZED);
        if(!"teacher".equals(user.role()) || !"active".equals(user.status()) || user.schoolId()==null || user.schoolId().isBlank())
            throw error("EVALUATION_REFERENCE_ACCESS_DENIED","An active school teacher is required.",HttpStatus.FORBIDDEN);
        if(uuid==null || !uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw error("VALIDATION_FAILED","A canonical assignment UUID is required.",HttpStatus.UNPROCESSABLE_ENTITY);
        if(!enabled) throw error("EVALUATION_REFERENCE_UNAVAILABLE","Evaluation-reference awaits Mobile release acceptance.",HttpStatus.SERVICE_UNAVAILABLE);
        return snapshot.execute(s->{
            if(!repository.active(user)) throw error("EVALUATION_REFERENCE_ACCESS_DENIED","The school teacher account is no longer active.",HttpStatus.FORBIDDEN);
            return build(user,uuid,false);
        });
    }
    /** Internal write precondition: caller holds the owner lock and a read/write transaction. */
    public V3EvaluationReference lockedForVerification(V3AuthenticatedUser user,String uuid) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("A write transaction is required for reference binding.");
        return build(user,uuid,true);
    }
    private V3EvaluationReference build(V3AuthenticatedUser user,String uuid,boolean lock) {
            var assignment=(lock?repository.assignment(user,uuid,true):repository.assignment(user,uuid)).orElseThrow(()->error("RESOURCE_NOT_FOUND","The owned assignment was not found.",HttpStatus.NOT_FOUND));
            if(!Set.of("active","completed").contains(assignment.testStatus()) || "archived".equals(assignment.assignmentStatus()))
                throw conflict("EVALUATION_REFERENCE_NOT_READY","The assignment is not available for scoring-reference download.");
            if(assignment.version()<1) throw invalid();
            var rows=lock?repository.questions(assignment.testId(),true):repository.questions(assignment.testId());
            if(rows.size()>200) throw limit();
            var questions=new ArrayList<Question>();var rubrics=new TreeMap<Long,Rubric>();var totals=new HashMap<Long,BigDecimal>();
            for(var row:rows) {
                validateStrategy(row);
                if(row.uuid()==null || !row.uuid().matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw invalid();
                if(row.expectedCount()!=null && row.expectedCount()<1) throw invalid();
                var maximum=points(row.maximum());
                if(row.rubricId()!=null) {
                    safeId(row.rubricId());
                    if(!rubrics.containsKey(row.rubricId())) {
                        var rubric=(lock?repository.rubric(row.rubricId(),user.schoolId(),true):repository.rubric(row.rubricId(),user.schoolId())).orElseThrow(this::invalid);
                        if(!"active".equals(rubric.status()) || rubric.name()==null || rubric.name().isBlank()) throw invalid();
                        var criteria=lock?repository.criteria(rubric.id(),true):repository.criteria(rubric.id());if(criteria.size()>100) throw limit();if(criteria.isEmpty()) throw invalid();
                        BigDecimal total=BigDecimal.ZERO;var normalized=new ArrayList<Criterion>();
                        for(var criterion:criteria) {
                            safeId(criterion.rubricCriterionId());if(criterion.name()==null || criterion.name().isBlank()) throw invalid();
                            var bound=points(criterion.maximumPoints());total=total.add(bound);
                            normalized.add(new Criterion(criterion.rubricCriterionId(),criterion.name(),bound,criterion.isRequired()));
                        }
                        if(total.compareTo(points(rubric.total()))!=0) throw invalid();
                        totals.put(rubric.id(),total);rubrics.put(rubric.id(),new Rubric(rubric.id(),rubric.name(),List.copyOf(normalized)));
                    }
                    if(totals.get(row.rubricId()).compareTo(maximum)!=0) throw invalid();
                }
                questions.add(new Question(row.uuid(),maximum,row.rubricId(),row.expectedCount()));
            }
            var publicQuestions=List.copyOf(questions);var publicRubrics=List.copyOf(rubrics.values());
            return new V3EvaluationReference("3.0",uuid,assignment.version(),fingerprint(uuid,assignment.version(),publicQuestions,publicRubrics),publicQuestions,publicRubrics);
    }
    private void validateStrategy(QuestionRow q) {
        if(Set.of("multiple_choice","true_false").contains(q.type())) {if(q.rubricId()!=null)throw invalid();return;}
        if(Set.of("identification","enumeration").contains(q.type())) {
            if(q.rubricId()!=null || !("accepted_text".equals(q.keyType()) || "manual".equals(q.keyType()))) throw invalid();return;
        }
        if(!"essay".equals(q.type())) throw invalid();
        if(q.rubricId()==null) {if(!"manual".equals(q.keyType()) || q.keyRubricId()!=null)throw invalid();}
        else if(!"rubric".equals(q.keyType()) || !q.rubricId().equals(q.keyRubricId())) throw invalid();
    }
    private BigDecimal points(BigDecimal value) {
        if(value==null || value.signum()<=0 || value.compareTo(new BigDecimal("999999.99"))>0) throw invalid();
        try{return value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw invalid();}
    }
    private void safeId(long id) {if(id<1 || id>9007199254740991L)throw invalid();}
    /** Hash a fixed ordered projection, with decimal points represented as two-place strings. */
    private String fingerprint(String uuid,int version,List<Question> questions,List<Rubric> rubrics) {
        var root=new LinkedHashMap<String,Object>();root.put("contractVersion","3.0");root.put("assignmentUuid",uuid);root.put("testVersionNumber",version);
        var qRows=new ArrayList<Object>();
        for(var q:questions){var row=new LinkedHashMap<String,Object>();row.put("questionUuid",q.questionUuid());row.put("maximumPoints",q.maximumPoints().toPlainString());row.put("rubricId",q.rubricId());row.put("expectedResponseCount",q.expectedResponseCount());qRows.add(row);}
        root.put("questions",qRows);var rRows=new ArrayList<Object>();
        for(var r:rubrics){var row=new LinkedHashMap<String,Object>();row.put("rubricId",r.rubricId());row.put("name",r.name());var cRows=new ArrayList<Object>();
            for(var c:r.criteria()){var cRow=new LinkedHashMap<String,Object>();cRow.put("rubricCriterionId",c.rubricCriterionId());cRow.put("name",c.name());cRow.put("maximumPoints",c.maximumPoints().toPlainString());cRow.put("isRequired",c.isRequired());cRows.add(cRow);}row.put("criteria",cRows);rRows.add(row);}
        root.put("rubrics",rRows);
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new ObjectMapper().writeValueAsBytes(root)));}
        catch(JsonProcessingException | NoSuchAlgorithmException e){throw new IllegalStateException("Cannot fingerprint evaluation reference.",e);}
    }
    private V3AuthException invalid(){return conflict("EVALUATION_REFERENCE_INVALID","Scoring references are incomplete or inconsistent within the owned school.");}
    private V3AuthException limit(){return conflict("EVALUATION_REFERENCE_LIMIT_EXCEEDED","Scoring references exceed the bounded Mobile contract.");}
    private V3AuthException conflict(String code,String message){return error(code,message,HttpStatus.CONFLICT);}
    private V3AuthException error(String code,String message,HttpStatus status){return new V3AuthException(code,message,status);}
}
