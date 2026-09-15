package com.capstone.assessment.v2.notification.service;

import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.notification.dto.V2NotificationResponse;
import com.capstone.assessment.v2.notification.repository.V2NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T04:00:00Z");
    private static final V2AuthenticatedUser TEACHER = new V2AuthenticatedUser(
            20L,
            "SCHOOL-001",
            "teacher@example.com",
            "teacher",
            "active",
            "session"
    );

    @Mock
    private V2NotificationRepository notificationRepository;

    private V2NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new V2NotificationService(
                notificationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void authenticatedUserListsOnlyOwnNotifications() {
        V2NotificationResponse notification = new V2NotificationResponse(
                1L,
                "11111111-1111-4111-8111-111111111111",
                "sync_success",
                "Assessment sync completed",
                "1 of 1 result synchronized.",
                "syncs",
                "8001",
                false,
                NOW,
                null
        );
        when(notificationRepository.countUnread(20L)).thenReturn(1L);
        when(notificationRepository.findForUser(20L, true, 20)).thenReturn(List.of(notification));

        var response = notificationService.list(TEACHER, true, null);

        assertEquals(1L, response.unreadCount());
        assertEquals(1, response.notifications().size());
        verify(notificationRepository).findForUser(20L, true, 20);
    }

    @Test
    void userCannotMarkAnotherUsersNotificationAsRead() {
        when(notificationRepository.markRead(99L, 20L, NOW)).thenReturn(0);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> notificationService.markRead(TEACHER, 99L)
        );

        assertEquals("NOTIFICATION_NOT_FOUND", exception.getCode());
    }

    @Test
    void markAllReadReturnsUpdatedUnreadCount() {
        when(notificationRepository.countUnread(20L)).thenReturn(0L);

        var response = notificationService.markAllRead(TEACHER);

        assertEquals(0L, response.unreadCount());
        verify(notificationRepository).markAllRead(20L, NOW);
    }
}
