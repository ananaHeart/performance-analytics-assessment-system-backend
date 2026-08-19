package com.capstone.assessment.v2.sync.dto;

public record V2SyncUserDto(
        Long userId,
        String schoolId,
        String email,
        String role,
        String status
) {
}
