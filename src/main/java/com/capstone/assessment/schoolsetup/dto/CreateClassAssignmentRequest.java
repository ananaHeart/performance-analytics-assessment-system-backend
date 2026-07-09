package com.capstone.assessment.schoolsetup.dto;

public record CreateClassAssignmentRequest(
        Long academicYearId,
        Long teacherId,
        Long subjectId,
        Long sectionId
) {
}
