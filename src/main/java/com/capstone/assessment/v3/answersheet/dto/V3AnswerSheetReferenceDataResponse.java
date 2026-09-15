package com.capstone.assessment.v3.answersheet.dto;

import java.math.BigDecimal;
import java.util.List;

public record V3AnswerSheetReferenceDataResponse(
        int minimumQuestionCount,
        String physicallyValidatedTemplateCode,
        List<String> supportedQuestionTypes,
        List<PaperSizeCapability> paperSizes,
        String capabilityNote
) {
    public record PaperSizeCapability(
            String paperSizeCode,
            String paperSizeName,
            BigDecimal widthPoints,
            BigDecimal heightPoints,
            boolean generationAvailable,
            String templateCode,
            String templateVersion,
            String minimumScannerVersion
    ) {
    }
}
