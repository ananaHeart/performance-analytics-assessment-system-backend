package com.capstone.assessment.auth.dto;

public record TeacherRegistrationRequest(
        String firstName,
        String lastName,
        String gender,
        String dateBirth,
        String email,
        String password
) {
}
