package com.capstone.assessment.v2.sync.dto;

public record V2SyncStudentDto(
        Long studentId,
        String schoolId,
        String studentLrn,
        String firstName,
        String middleName,
        String lastName,
        String suffix,
        String status
) {
}
