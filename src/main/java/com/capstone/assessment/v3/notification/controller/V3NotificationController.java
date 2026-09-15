package com.capstone.assessment.v3.notification.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.notification.dto.V3NotificationListResponse;
import com.capstone.assessment.v3.notification.dto.V3NotificationUnreadCountResponse;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/notifications")
public class V3NotificationController {

    private final V3NotificationService notificationService;

    public V3NotificationController(V3NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<V3NotificationListResponse>> list(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Notifications retrieved successfully.",
                notificationService.list(principal, unreadOnly, limit)
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<V3NotificationUnreadCountResponse>> unreadCount(
            @AuthenticationPrincipal V3AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Unread notification count retrieved successfully.",
                notificationService.unreadCount(principal)
        ));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<V3NotificationUnreadCountResponse>> markRead(
            @AuthenticationPrincipal V3AuthenticatedUser principal,
            @PathVariable long notificationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Notification marked as read.",
                notificationService.markRead(principal, notificationId)
        ));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<V3NotificationUnreadCountResponse>> markAllRead(
            @AuthenticationPrincipal V3AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "All notifications marked as read.",
                notificationService.markAllRead(principal)
        ));
    }
}
