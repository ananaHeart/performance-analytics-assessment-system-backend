package com.capstone.assessment.v3.answersheet.dto;

import java.time.Instant;

public record V3AnswerSheetVersionResponse(
        long answerSheetVersionId,
        String answerSheetUuid,
        long testAssignmentId,
        String assignmentUuid,
        String paperSizeCode,
        int generationNumber,
        int testVersionNumber,
        int totalQuestions,
        int totalPages,
        int manifestVersion,
        String manifestHash,
        String generationStatus,
        String pdfContentHash,
        long pdfFileSizeBytes,
        Instant generatedAt,
        String pdfDownloadPath
) {
}
