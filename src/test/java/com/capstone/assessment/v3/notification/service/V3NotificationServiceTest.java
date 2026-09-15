package com.capstone.assessment.v3.notification.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.notification.dto.V3NotificationResponse;
import com.capstone.assessment.v3.notification.repository.V3NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V3NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T01:00:00Z");
    private static final V3AuthenticatedUser TEACHER = user("teacher", "active", "SCHOOL-001");
    private static final V3AuthenticatedUser PRINCIPAL = user("principal", "active", "SCHOOL-001");

    @Mock
    private V3NotificationRepository notificationRepository;

    private V3NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new V3NotificationService(
                notificationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void activeTeacherListsOnlyOwnNotificationsUsingDefaultLimit() {
        V3NotificationResponse notification = new V3NotificationResponse(
                1L,
                "11111111-1111-4111-8111-111111111111",
                "class_assignment_created",
                "New class assignment",
                "You were assigned to Grade 7 - Rizal for English (2025-2026).",
                "class_assignments",
                "9001",
                false,
                NOW,
                null
        );
        when(notificationRepository.countUnread(20L)).thenReturn(1L);
        when(notificationRepository.findForUser(20L, true, 20)).thenReturn(List.of(notification));

        var response = notificationService.list(TEACHER, true, null);

        assertEquals(1L, response.unreadCount());
        assertEquals(List.of(notification), response.notifications());
        verify(notificationRepository).findForUser(20L, true, 20);
    }

    @Test
    void activePrincipalCanReadUnreadCount() {
        when(notificationRepository.countUnread(20L)).thenReturn(3L);

        assertEquals(3L, notificationService.unreadCount(PRINCIPAL).unreadCount());
    }

    @Test
    void invalidNotificationLimitsAreRejected() {
        V3AuthException zero = assertThrows(
                V3AuthException.class,
                () -> notificationService.list(TEACHER, false, 0)
        );
        V3AuthException excessive = assertThrows(
                V3AuthException.class,
                () -> notificationService.list(TEACHER, false, 101)
        );

        assertEquals("INVALID_NOTIFICATION_LIMIT", zero.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, excessive.getStatus());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void unauthenticatedInactiveAndUnsupportedAccountsAreRejected() {
        V3AuthException unauthenticated = assertThrows(
                V3AuthException.class,
                () -> notificationService.unreadCount(null)
        );
        V3AuthException inactive = assertThrows(
                V3AuthException.class,
                () -> notificationService.unreadCount(user("teacher", "inactive", "SCHOOL-001"))
        );
        V3AuthException unsupported = assertThrows(
                V3AuthException.class,
                () -> notificationService.unreadCount(user("student", "active", "SCHOOL-001"))
        );
        V3AuthException missingSchool = assertThrows(
                V3AuthException.class,
                () -> notificationService.unreadCount(user("teacher", "active", null))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatus());
        assertEquals("ACTIVE_SCHOOL_ACCOUNT_REQUIRED", inactive.getCode());
        assertEquals(HttpStatus.FORBIDDEN, unsupported.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, missingSchool.getStatus());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void unreadNotificationCanBeMarkedRead() {
        when(notificationRepository.markRead(91L, 20L, NOW)).thenReturn(1);
        when(notificationRepository.countUnread(20L)).thenReturn(2L);

        var response = notificationService.markRead(TEACHER, 91L);

        assertEquals(2L, response.unreadCount());
        verify(notificationRepository, never()).existsForUser(91L, 20L);
    }

    @Test
    void markingAnAlreadyReadOwnedNotificationIsIdempotent() {
        when(notificationRepository.markRead(91L, 20L, NOW)).thenReturn(0);
        when(notificationRepository.existsForUser(91L, 20L)).thenReturn(true);
        when(notificationRepository.countUnread(20L)).thenReturn(0L);

        var response = notificationService.markRead(TEACHER, 91L);

        assertEquals(0L, response.unreadCount());
    }

    @Test
    void userCannotReadAnotherUsersNotification() {
        when(notificationRepository.markRead(91L, 20L, NOW)).thenReturn(0);
        when(notificationRepository.existsForUser(91L, 20L)).thenReturn(false);

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> notificationService.markRead(TEACHER, 91L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("NOTIFICATION_NOT_FOUND", exception.getCode());
        verify(notificationRepository, never()).countUnread(20L);
    }

    @Test
    void markAllReadReturnsCurrentUnreadCount() {
        when(notificationRepository.countUnread(20L)).thenReturn(0L);

        var response = notificationService.markAllRead(TEACHER);

        assertEquals(0L, response.unreadCount());
        verify(notificationRepository).markAllRead(20L, NOW);
    }

    @Test
    void lifecycleEventsUseStableRecipientsAndReferences() {
        notificationService.notifyTeacherPendingApproval(42L, NOW);
        notificationService.notifyTeacherApproved(42L, NOW);
        notificationService.notifyTeacherRejected(43L, NOW);
        notificationService.notifyClassAssignmentCreated(
                42L, 900L, "Grade 7", "Rizal", "English", "2025-2026", NOW
        );

        verify(notificationRepository).insertForSchoolPrincipalsOfUser(
                eq(42L),
                eq("teacher_pending_approval"),
                eq("Teacher approval required"),
                any(String.class),
                eq("users"),
                eq("42"),
                eq("teacher-registration:42:pending-approval"),
                eq(NOW)
        );
        verify(notificationRepository).insertForUser(
                eq(42L),
                eq("teacher_account_approved"),
                eq("Teacher account approved"),
                any(String.class),
                eq("users"),
                eq("42"),
                eq("teacher-account:42:approved"),
                eq(NOW)
        );
        verify(notificationRepository).insertForUser(
                eq(43L),
                eq("teacher_account_rejected"),
                eq("Teacher account request not approved"),
                any(String.class),
                eq("users"),
                eq("43"),
                eq("teacher-account:43:rejected"),
                eq(NOW)
        );
        verify(notificationRepository).insertForUser(
                eq(42L),
                eq("class_assignment_created"),
                eq("New class assignment"),
                eq("You were assigned to Grade 7 - Rizal for English (2025-2026)."),
                eq("class_assignments"),
                eq("900"),
                eq("class-assignment:900:created"),
                eq(NOW)
        );
    }

    private static V3AuthenticatedUser user(String role, String status, String schoolId) {
        return new V3AuthenticatedUser(
                20L,
                schoolId,
                "user@example.com",
                role,
                status,
                "session"
        );
    }
}
