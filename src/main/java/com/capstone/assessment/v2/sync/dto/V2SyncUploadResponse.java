package com.capstone.assessment.v2.sync.dto;

import java.time.Instant;
import java.util.List;

public record V2SyncUploadResponse(
        String syncUuid,
        Long syncId,
        Long testId,
        String status,
        Instant completedAt,
        List<V2SyncUploadItemResponse> items
) {
}
