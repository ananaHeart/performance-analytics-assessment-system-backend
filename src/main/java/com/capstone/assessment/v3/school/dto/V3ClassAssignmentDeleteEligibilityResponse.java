package com.capstone.assessment.v3.school.dto;

/** CLS-4: whether the principal may really delete an assignment, or must archive it, and why. */
public record V3ClassAssignmentDeleteEligibilityResponse(
        long classAssignmentId,
        String status,
        boolean canHardDelete,
        boolean canArchive,
        int assessmentCount,
        int resultCount,
        String reason
) {
}
