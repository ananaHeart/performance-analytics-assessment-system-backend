package com.capstone.assessment.v2.sync.model;

import java.math.BigDecimal;

public record V2SyncQuestionScoringRow(
        Long questionId,
        String correctOption,
        BigDecimal pointsPerItem
) {
}
