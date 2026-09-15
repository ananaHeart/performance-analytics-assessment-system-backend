package com.capstone.assessment.v3.system.dto;

public record V3MobileReleaseCheckResponse(
        String check,
        boolean passed,
        String detail
) {
}
