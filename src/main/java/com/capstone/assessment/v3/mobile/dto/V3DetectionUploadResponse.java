package com.capstone.assessment.v3.mobile.dto;

import java.time.Instant;
import java.util.List;

public record V3DetectionUploadResponse(String syncUuid, String operationUuid, String disposition,
        long revision, List<IdMapping> idMappings, Instant acknowledgedAt) {
    public record IdMapping(String entityType, String uuid, long centralId) { }
    public V3DetectionUploadResponse replay() {
        return new V3DetectionUploadResponse(syncUuid, operationUuid, "replayed", revision, idMappings, acknowledgedAt);
    }
}
