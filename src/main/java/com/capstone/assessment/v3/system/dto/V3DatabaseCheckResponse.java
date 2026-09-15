package com.capstone.assessment.v3.system.dto;

public record V3DatabaseCheckResponse(
        String check,
        String expected,
        String actual,
        boolean passed
) {
}
