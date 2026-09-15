package com.capstone.assessment.importexport.dto;

import java.util.List;

public record Sf1ImportPreviewResponse(
        String detectedSchoolYear,
        String detectedSectionName,
        String detectedGradeLevelName,
        Integer totalRows,
        Integer validRows,
        Integer invalidRows,
        List<Sf1PreviewRowDto> rows
) {
}
