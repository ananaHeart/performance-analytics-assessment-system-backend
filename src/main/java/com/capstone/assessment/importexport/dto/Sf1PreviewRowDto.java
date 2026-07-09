package com.capstone.assessment.importexport.dto;

public record Sf1PreviewRowDto(
        Integer rowNumber,
        String studentLrn,
        String firstName,
        String lastName,
        String gender,
        String status,
        String message
) {
}
