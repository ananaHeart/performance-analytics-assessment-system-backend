package com.capstone.assessment.schoolsetup.dto;

public record TeacherDto(
        Long userId,
        String firstName,
        String lastName,
        String email,
        String status
) {
}
