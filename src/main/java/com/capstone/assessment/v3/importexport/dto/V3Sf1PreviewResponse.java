package com.capstone.assessment.v3.importexport.dto;

import java.util.List;

public record V3Sf1PreviewResponse(
        String importUuid,
        String sourceFileName,
        String sourceFileHash,
        boolean duplicateFile,
        int previousCompletedImportCount,
        ImportContext selectedContext,
        DetectedContext detectedContext,
        boolean requiresContextOverride,
        List<String> warnings,
        int totalRows,
        int validRows,
        int invalidRows,
        List<Row> rows
) {
    public record ImportContext(
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            String sectionName,
            Long existingClassId
    ) {
    }

    public record DetectedContext(
            String academicYearName,
            String gradeLevelName,
            String sectionName
    ) {
    }

    public record Row(
            int rowNumber,
            String studentLrn,
            String firstName,
            String lastName,
            String gender,
            String parserStatus,
            String plannedOutcome,
            String warningCode,
            String message
    ) {
    }
}
