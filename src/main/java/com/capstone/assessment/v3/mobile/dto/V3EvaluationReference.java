package com.capstone.assessment.v3.mobile.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

/**
 * Contract 3.1: besides score bounds, carries the answer keys so the phone can show a
 * display-only PRELIMINARY score right after a scan. The official score is still computed
 * by the backend at finalization; answer explanations are never included.
 */
public record V3EvaluationReference(String contractVersion,String assignmentUuid,int testVersionNumber,
        String evaluationReferenceHash,List<Question> questions,List<Rubric> rubrics) {
    /** correctOptionKey: MC/TF only (question_options.option_key, i.e. the manifest option's
     *  storedValue; T/F is always A=True, B=False). acceptedAnswers: identification and
     *  enumeration only, a teacher guide - the teacher's points stay official. */
    public record Question(String questionUuid,String questionType,BigDecimal maximumPoints,Long rubricId,
            Integer expectedResponseCount,String correctOptionKey,List<AcceptedAnswer> acceptedAnswers) {
        public Question { acceptedAnswers=acceptedAnswers==null?List.of():List.copyOf(acceptedAnswers); }
    }
    public record AcceptedAnswer(String text,String matchingMode,boolean caseSensitive,BigDecimal points) { }
    public record Rubric(long rubricId,String name,List<Criterion> criteria) { }
    /** levelDefinition is rubric_criteria.level_definition passed through as stored (or null).
     *  A JSON null parses back as NullNode, not Java null. Finalization compares the reference
     *  saved at verification with the current one using equals(), so both forms must collapse
     *  to Java null or every rubric without levels fails with WRITTEN_AUDIT_MISMATCH. */
    public record Criterion(long rubricCriterionId,String name,BigDecimal maximumPoints,boolean isRequired,
            String description,JsonNode levelDefinition) {
        public Criterion {
            if(levelDefinition!=null && (levelDefinition.isNull() || levelDefinition.isMissingNode())) levelDefinition=null;
        }
    }
}
