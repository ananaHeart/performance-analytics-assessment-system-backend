package com.capstone.assessment.v3.assessment.dto;

/**
 * R2-9: what saving an edit would do, before the teacher saves.
 * mode: "draft" (no answer sheet yet), "same_layout" (bubbles stay; printed sheets still scan, no
 * reprint) or "layout_change" (items, question types or choices change; the generated sheet is
 * removed and must be printed again). canSave false means the save would be refused with
 * blockedCode (for example ASSESSMENT_STRUCTURE_LOCKED once sheets were scanned).
 */
public record V3AssessmentEditImpactResponse(
        long testId,
        String status,
        String mode,
        boolean canSave,
        boolean reprintRequired,
        int generatedAnswerSheetCount,
        boolean printedTextChanged,
        boolean answerKeyChanged,
        int rescoreResultCount,
        String blockedCode,
        String message
) {
}
