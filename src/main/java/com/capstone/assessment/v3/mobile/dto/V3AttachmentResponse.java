package com.capstone.assessment.v3.mobile.dto;

import java.time.Instant;

public record V3AttachmentResponse(String attachmentUuid, long backendAttachmentId, String contentHash,
                                   String uploadStatus, Instant acknowledgedAt) { }
