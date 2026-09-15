package com.capstone.assessment.v3.notification.dto;

import java.time.Instant;

public record V3NotificationResponse(
        Long notificationId,
        String notificationUuid,
        String notificationType,
        String title,
        String message,
        String referenceType,
        String referenceId,
        boolean isRead,
        Instant createdAt,
        Instant readAt
) {
}
