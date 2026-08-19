package com.capstone.assessment.v2.sync.dto;

public record V2SyncClassDto(
        Long classId,
        Integer academicYearId,
        String yearName,
        Integer gradeLevelId,
        String gradeLevelName,
        Integer sectionId,
        String sectionName,
        String status
) {
}
