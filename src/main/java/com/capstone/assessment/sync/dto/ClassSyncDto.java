package com.capstone.assessment.sync.dto;

public record ClassSyncDto(
        Long classId,
        Long teacherId,
        Long subjectId,
        String subjectName,
        Long sectionId,
        String sectionName,
        Long gradeLevelId,
        String gradeLevelName,
        Long academicYearId,
        String academicYear
) {
}
