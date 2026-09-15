package com.capstone.assessment.v3.mobile.dto;

import jakarta.validation.constraints.*;

public record V3ReopenRequest(
        @NotNull @Pattern(regexp="3\\.0") String contractVersion,
        @NotNull @Pattern(regexp=UUID) String syncUuid,
        @NotNull @Pattern(regexp=UUID) String operationUuid,
        @NotNull @Min(1) @Max(9007199254740991L) Long expectedRevision,
        @NotNull @Min(1) @Max(9007199254740991L) Long expectedScoreVersion,
        @NotBlank @Size(max=50) String reasonCode,
        @Size(max=4000) String comment) {
    public static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
}
