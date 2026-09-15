package com.capstone.assessment.v3.mobile.dto;

import java.time.Instant;

/** Frozen 3.0 attachment request. Local paths and client central IDs are never accepted. */
public record V3AttachmentMetadata(String contractVersion, String syncUuid, String operationUuid,
        String attachmentUuid, String resultUuid, String scanPageUuid, String attachmentType,
        String regionUuid, String sourceAttachmentUuid, Crop crop, String contentHash,
        long fileSizeBytes, String mimeType, Instant capturedAt) {
    public record Crop(String baseAttachmentUuid, String coordinateSpace, long baseWidthPx,
                       long baseHeightPx, long x, long y, long width, long height) { }
}
