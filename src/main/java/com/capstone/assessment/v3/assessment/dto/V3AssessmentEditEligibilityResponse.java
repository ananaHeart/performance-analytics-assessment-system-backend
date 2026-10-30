package com.capstone.assessment.v3.assessment.dto;

/**
 * ASM-7: what the teacher may do with an assessment now. The backend decides; the web only
 * shows or hides the buttons from these flags.
 * contentEditMode: "full" (anything may change; generated answer sheets must be printed again),
 * "same_layout" (sheets were scanned: text, answer key, objective points and skill mappings may
 * change, the bubble layout stays) or "none" (archived).
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
        String reason,
        String contentEditMode,
        boolean canEditSchedule,
        int pendingResultCount
) {
}
