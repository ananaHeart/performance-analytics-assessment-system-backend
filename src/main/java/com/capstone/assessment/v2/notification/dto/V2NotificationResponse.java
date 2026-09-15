package com.capstone.assessment.v2.notification.dto;

import java.time.Instant;

public record V2NotificationResponse(
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
