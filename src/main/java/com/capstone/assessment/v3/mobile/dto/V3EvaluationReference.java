package com.capstone.assessment.v3.mobile.dto;

import java.math.BigDecimal;
import java.util.List;

public record V3EvaluationReference(String contractVersion,String assignmentUuid,int testVersionNumber,
        String evaluationReferenceHash,List<Question> questions,List<Rubric> rubrics) {
    public record Question(String questionUuid,BigDecimal maximumPoints,Long rubricId,Integer expectedResponseCount) { }
    public record Rubric(long rubricId,String name,List<Criterion> criteria) { }
    public record Criterion(long rubricCriterionId,String name,BigDecimal maximumPoints,boolean isRequired) { }
}
