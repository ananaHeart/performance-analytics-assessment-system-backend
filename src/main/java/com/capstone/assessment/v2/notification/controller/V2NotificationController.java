package com.capstone.assessment.v2.notification.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.notification.dto.V2NotificationListResponse;
import com.capstone.assessment.v2.notification.dto.V2NotificationUnreadCountResponse;
import com.capstone.assessment.v2.notification.service.V2NotificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("v2")
@RestController
@RequestMapping("/api/v2/notifications")
public class V2NotificationController {

    private final V2NotificationService notificationService;

    public V2NotificationController(V2NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<V2NotificationListResponse>> list(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Notifications retrieved successfully.",
                notificationService.list(principal, unreadOnly, limit)
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<V2NotificationUnreadCountResponse>> unreadCount(
            @AuthenticationPrincipal V2AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Unread notification count retrieved successfully.",
                notificationService.unreadCount(principal)
        ));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<V2NotificationUnreadCountResponse>> markRead(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long notificationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Notification marked as read.",
                notificationService.markRead(principal, notificationId)
        ));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<V2NotificationUnreadCountResponse>> markAllRead(
            @AuthenticationPrincipal V2AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "All notifications marked as read.",
                notificationService.markAllRead(principal)
        ));
    }
}
