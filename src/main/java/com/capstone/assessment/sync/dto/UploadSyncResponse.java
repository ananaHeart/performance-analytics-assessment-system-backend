package com.capstone.assessment.sync.dto;

public record UploadSyncResponse(
        String status,
        Integer uploadedResults,
        Integer uploadedItems,
        String message
) {
}
