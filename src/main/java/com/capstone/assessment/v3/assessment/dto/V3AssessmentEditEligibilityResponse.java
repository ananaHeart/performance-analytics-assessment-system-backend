package com.capstone.assessment.v3.assessment.dto;

/**
 * ASM-7: what the teacher may do with an assessment now. The backend decides; the web only
 * shows or hides the buttons from these flags.
 */
public record V3AssessmentEditEligibilityResponse(
        long testId,
        String status,
        boolean hasResults,
        int resultCount,
        boolean hasScans,
        boolean canEditContent,
        boolean canEditDetails,
        boolean canCorrectAnswerKey,
        boolean canHardDelete,
        boolean canArchive,
        boolean canRestore,
        String reason
) {
}
