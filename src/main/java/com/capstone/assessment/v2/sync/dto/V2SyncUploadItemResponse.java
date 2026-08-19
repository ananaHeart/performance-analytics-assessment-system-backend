package com.capstone.assessment.v2.sync.dto;

public record V2SyncUploadItemResponse(
        String resultUuid,
        Long syncItemId,
        Long testResultId,
        Long scanSessionId,
        String status,
        String errorCode,
        String errorMessage
) {
}
