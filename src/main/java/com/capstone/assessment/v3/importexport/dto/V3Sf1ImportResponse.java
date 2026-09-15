package com.capstone.assessment.v3.importexport.dto;

import java.time.Instant;
import java.util.List;

public record V3Sf1ImportResponse(
        long sf1ImportId,
        String importUuid,
        String importStatus,
        String sourceFileName,
        String sourceFileHash,
        boolean duplicateFile,
        int previousCompletedImportCount,
        TargetClass targetClass,
        int totalRows,
        int createdStudents,
        int updatedStudents,
        int unchangedStudents,
        int conflictRows,
        int invalidRows,
        Instant startedAt,
        Instant completedAt,
        boolean replayed,
        List<Row> rows
) {
    public record TargetClass(
            long classId,
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            int sectionId,
            String sectionName
    ) {
    }

    public record Row(
            long sf1ImportItemId,
            int rowNumber,
            String studentLrn,
            Long studentId,
            Long classListId,
            String outcomeStatus,
            String warningCode,
            String message,
            Instant processedAt
    ) {
    }
}
