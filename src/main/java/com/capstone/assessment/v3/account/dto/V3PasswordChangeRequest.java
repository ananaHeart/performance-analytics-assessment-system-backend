package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.NotBlank;

/** SET-6: change password while signed in. */
public record V3PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
}
