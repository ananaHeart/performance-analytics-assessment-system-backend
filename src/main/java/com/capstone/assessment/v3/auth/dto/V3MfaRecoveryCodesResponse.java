package com.capstone.assessment.v3.auth.dto;

import java.util.List;

public record V3MfaRecoveryCodesResponse(
        boolean enabled,
        String factorUuid,
        List<String> recoveryCodes
) {
}
