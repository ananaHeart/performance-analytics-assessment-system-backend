package com.capstone.assessment.v2.assessment.dto;

import java.math.BigDecimal;
import java.util.List;

public record V2AssessmentPartResponse(
        Long testPartId,
        Integer partOrder,
        String partName,
        String partType,
        Integer numberOfItems,
        BigDecimal pointsPerItem,
        List<V2AssessmentQuestionResponse> questions
) {
}
