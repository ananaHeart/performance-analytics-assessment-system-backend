package com.capstone.assessment.v2.sync.dto;

import java.math.BigDecimal;

public record V2SyncTestPartDto(
        Long testPartId,
        Long testId,
        Integer partOrder,
        String partName,
        String partType,
        Integer numberOfItems,
        BigDecimal pointsPerItem
) {
}
