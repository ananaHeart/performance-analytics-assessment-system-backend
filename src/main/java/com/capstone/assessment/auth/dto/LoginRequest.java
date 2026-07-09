package com.capstone.assessment.auth.dto;

public record LoginRequest(
        String email,
        String password
) {
}
