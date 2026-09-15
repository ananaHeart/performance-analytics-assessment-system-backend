package com.capstone.assessment.v2.notification.dto;

import java.util.List;

public record V2NotificationListResponse(
        long unreadCount,
        List<V2NotificationResponse> notifications
) {
}
