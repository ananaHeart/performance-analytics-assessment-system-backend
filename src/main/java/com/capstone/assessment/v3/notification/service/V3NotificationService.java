package com.capstone.assessment.v3.notification.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.notification.dto.V3NotificationListResponse;
import com.capstone.assessment.v3.notification.dto.V3NotificationUnreadCountResponse;
import com.capstone.assessment.v3.notification.repository.V3NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Profile("v3")
@Service
public class V3NotificationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> ALLOWED_ROLES = Set.of("principal", "teacher");

    private final V3NotificationRepository notificationRepository;
    private final Clock clock;

    @Autowired
    public V3NotificationService(V3NotificationRepository notificationRepository) {
        this(notificationRepository, Clock.systemUTC());
    }

    V3NotificationService(V3NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public V3NotificationListResponse list(
            V3AuthenticatedUser principal,
            boolean unreadOnly,
            Integer requestedLimit
    ) {
        requireActiveUser(principal);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new V3AuthException(
                    "INVALID_NOTIFICATION_LIMIT",
                    "Notification limit must be between 1 and 100.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return new V3NotificationListResponse(
                notificationRepository.countUnread(principal.userId()),
                notificationRepository.findForUser(principal.userId(), unreadOnly, limit)
        );
    }

    @Transactional(readOnly = true)
    public V3NotificationUnreadCountResponse unreadCount(V3AuthenticatedUser principal) {
        requireActiveUser(principal);
        return unreadCountFor(principal.userId());
    }

    @Transactional
    public V3NotificationUnreadCountResponse markRead(
            V3AuthenticatedUser principal,
            long notificationId
    ) {
        requireActiveUser(principal);
        if (notificationId <= 0) {
            throw notificationNotFound();
        }
        int changed = notificationRepository.markRead(
                notificationId,
                principal.userId(),
                clock.instant()
        );
        if (changed == 0 && !notificationRepository.existsForUser(notificationId, principal.userId())) {
            throw notificationNotFound();
        }
        return unreadCountFor(principal.userId());
    }

    @Transactional
    public V3NotificationUnreadCountResponse markAllRead(V3AuthenticatedUser principal) {
        requireActiveUser(principal);
        notificationRepository.markAllRead(principal.userId(), clock.instant());
        return unreadCountFor(principal.userId());
    }

    public void notifyTeacherPendingApproval(long teacherUserId, Instant createdAt) {
        String referenceId = Long.toString(teacherUserId);
        notificationRepository.insertForSchoolPrincipalsOfUser(
                teacherUserId,
                "teacher_pending_approval",
                "Teacher approval required",
                "A verified teacher registration is ready for review.",
                "users",
                referenceId,
                "teacher-registration:" + referenceId + ":pending-approval",
                createdAt
        );
    }

    public void notifyTeacherApproved(long teacherUserId, Instant createdAt) {
        notifyTeacherDecision(
                teacherUserId,
                "teacher_account_approved",
                "Teacher account approved",
                "Your teacher account was approved. You can now sign in.",
                "approved",
                createdAt
        );
    }

    public void notifyTeacherRejected(long teacherUserId, Instant createdAt) {
        notifyTeacherDecision(
                teacherUserId,
                "teacher_account_rejected",
                "Teacher account request not approved",
                "Your teacher account request was not approved. Contact your school principal for details.",
                "rejected",
                createdAt
        );
    }

    public void notifyClassAssignmentCreated(
            long teacherUserId,
            long classAssignmentId,
            String gradeLevelName,
            String sectionName,
            String subjectName,
            String academicYearName,
            Instant createdAt
    ) {
        notifyClassAssignment(
                teacherUserId,
                classAssignmentId,
                "class_assignment_created",
                "New class assignment",
                "You were assigned to " + classLabel(gradeLevelName, sectionName)
                        + " for " + subjectName + " (" + academicYearName + ").",
                "created",
                createdAt
        );
    }

    public void notifyClassAssignmentArchived(
            long teacherUserId,
            long classAssignmentId,
            String gradeLevelName,
            String sectionName,
            String subjectName,
            String academicYearName,
            Instant createdAt
    ) {
        notifyClassAssignment(
                teacherUserId,
                classAssignmentId,
                "class_assignment_archived",
                "Class assignment archived",
                "Your assignment to " + classLabel(gradeLevelName, sectionName)
                        + " for " + subjectName + " (" + academicYearName + ") was archived.",
                "archived:" + createdAt.toEpochMilli(),
                createdAt
        );
    }

    public void notifyClassAssignmentReactivated(
            long teacherUserId,
            long classAssignmentId,
            String gradeLevelName,
            String sectionName,
            String subjectName,
            String academicYearName,
            Instant createdAt
    ) {
        notifyClassAssignment(
                teacherUserId,
                classAssignmentId,
                "class_assignment_reactivated",
                "Class assignment reactivated",
                "Your assignment to " + classLabel(gradeLevelName, sectionName)
                        + " for " + subjectName + " (" + academicYearName + ") was reactivated.",
                "reactivated:" + createdAt.toEpochMilli(),
                createdAt
        );
    }

    private void notifyTeacherDecision(
            long teacherUserId,
            String type,
            String title,
            String message,
            String decision,
            Instant createdAt
    ) {
        String referenceId = Long.toString(teacherUserId);
        notificationRepository.insertForUser(
                teacherUserId,
                type,
                title,
                message,
                "users",
                referenceId,
                "teacher-account:" + referenceId + ":" + decision,
                createdAt
        );
    }

    private void notifyClassAssignment(
            long teacherUserId,
            long classAssignmentId,
            String type,
            String title,
            String message,
            String eventSuffix,
            Instant createdAt
    ) {
        String referenceId = Long.toString(classAssignmentId);
        notificationRepository.insertForUser(
                teacherUserId,
                type,
                title,
                message,
                "class_assignments",
                referenceId,
                "class-assignment:" + referenceId + ":" + eventSuffix,
                createdAt
        );
    }

    private String classLabel(String gradeLevelName, String sectionName) {
        return gradeLevelName + " - " + sectionName;
    }

    private V3NotificationUnreadCountResponse unreadCountFor(long userId) {
        return new V3NotificationUnreadCountResponse(notificationRepository.countUnread(userId));
    }

    private void requireActiveUser(V3AuthenticatedUser principal) {
        if (principal == null) {
            throw new V3AuthException(
                    "AUTHENTICATION_REQUIRED",
                    "Authentication is required.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        if (!"active".equalsIgnoreCase(principal.status())
                || principal.schoolId() == null
                || principal.schoolId().isBlank()
                || principal.role() == null
                || !ALLOWED_ROLES.contains(principal.role().toLowerCase(Locale.ROOT))) {
            throw new V3AuthException(
                    "ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active principal or teacher account associated with a school is required.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private V3AuthException notificationNotFound() {
        return new V3AuthException(
                "NOTIFICATION_NOT_FOUND",
                "Notification was not found for the authenticated user.",
                HttpStatus.NOT_FOUND
        );
    }
}
