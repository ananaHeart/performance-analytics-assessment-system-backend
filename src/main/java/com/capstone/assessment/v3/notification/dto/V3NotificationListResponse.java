package com.capstone.assessment.v3.notification.dto;

import java.util.List;

public record V3NotificationListResponse(
        long unreadCount,
        List<V3NotificationResponse> notifications
) {
}
