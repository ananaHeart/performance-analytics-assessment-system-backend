package com.capstone.assessment.v2.assessment.model;

import java.math.BigDecimal;

public record V2AssessmentPartRow(
        Long testPartId,
        Integer partOrder,
        String partName,
        String partType,
        Integer numberOfItems,
        BigDecimal pointsPerItem
) {
}
