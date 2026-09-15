package com.capstone.assessment.v2.notification.service;

import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.notification.dto.V2NotificationListResponse;
import com.capstone.assessment.v2.notification.dto.V2NotificationUnreadCountResponse;
import com.capstone.assessment.v2.notification.repository.V2NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Profile("v2")
@Service
public class V2NotificationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final V2NotificationRepository notificationRepository;
    private final Clock clock;

    @Autowired
    public V2NotificationService(V2NotificationRepository notificationRepository) {
        this(notificationRepository, Clock.systemUTC());
    }

    V2NotificationService(V2NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public V2NotificationListResponse list(
            V2AuthenticatedUser principal,
            boolean unreadOnly,
            Integer requestedLimit
    ) {
        requireAuthenticated(principal);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new V2AuthException(
                    "INVALID_NOTIFICATION_LIMIT",
                    "Notification limit must be between 1 and 100.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return new V2NotificationListResponse(
                notificationRepository.countUnread(principal.userId()),
                notificationRepository.findForUser(principal.userId(), unreadOnly, limit)
        );
    }

    @Transactional(readOnly = true)
    public V2NotificationUnreadCountResponse unreadCount(V2AuthenticatedUser principal) {
        requireAuthenticated(principal);
        return new V2NotificationUnreadCountResponse(notificationRepository.countUnread(principal.userId()));
    }

    @Transactional
    public V2NotificationUnreadCountResponse markRead(V2AuthenticatedUser principal, long notificationId) {
        requireAuthenticated(principal);
        if (notificationRepository.markRead(notificationId, principal.userId(), clock.instant()) != 1) {
            throw new V2AuthException(
                    "NOTIFICATION_NOT_FOUND",
                    "Notification was not found for the authenticated user.",
                    HttpStatus.NOT_FOUND
            );
        }
        return unreadCount(principal);
    }

    @Transactional
    public V2NotificationUnreadCountResponse markAllRead(V2AuthenticatedUser principal) {
        requireAuthenticated(principal);
        notificationRepository.markAllRead(principal.userId(), clock.instant());
        return unreadCount(principal);
    }

    public void notifyUser(
            long recipientUserId,
            String notificationType,
            String title,
            String message,
            String referenceType,
            String referenceId,
            String eventKey,
            Instant createdAt
    ) {
        notificationRepository.insertForUser(
                recipientUserId,
                notificationType,
                title,
                message,
                referenceType,
                referenceId,
                eventKey,
                createdAt
        );
    }

    public void notifySchoolPrincipals(
            String schoolId,
            String notificationType,
            String title,
            String message,
            String referenceType,
            String referenceId,
            String eventKey,
            Instant createdAt
    ) {
        notificationRepository.insertForSchoolPrincipals(
                schoolId,
                notificationType,
                title,
                message,
                referenceType,
                referenceId,
                eventKey,
                createdAt
        );
    }

    private void requireAuthenticated(V2AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null) {
            throw new V2AuthException("UNAUTHORIZED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
    }
}
