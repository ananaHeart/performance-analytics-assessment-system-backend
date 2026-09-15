package com.capstone.assessment.v3.importexport.model;

import java.time.Instant;

public final class V3Sf1Models {

    private V3Sf1Models() {
    }

    public record ReferenceContext(
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            String sectionName,
            Long existingClassId
    ) {
    }

    public record StudentContext(
            long studentId,
            String schoolId,
            String studentLrn,
            String firstName,
            String lastName,
            String genderName,
            String status
    ) {
    }

    public record EnrollmentContext(
            long classListId,
            long classId,
            String sectionName,
            String enrollmentStatus
    ) {
    }

    public record ImportHeader(
            long sf1ImportId,
            String importUuid,
            String schoolId,
            int academicYearId,
            long targetClassId,
            long uploadedByUserId,
            String sourceFileName,
            String sourceFileHash,
            String importStatus,
            int totalRows,
            int createdStudents,
            int updatedStudents,
            int unchangedStudents,
            int conflictRows,
            int invalidRows,
            Instant startedAt,
            Instant completedAt
    ) {
    }
}
