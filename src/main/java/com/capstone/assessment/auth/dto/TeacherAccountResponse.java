package com.capstone.assessment.auth.dto;

public record TeacherAccountResponse(
        Long userId,
        String firstName,
        String lastName,
        String gender,
        String dateBirth,
        String email,
        String role,
        String status
) {
}
