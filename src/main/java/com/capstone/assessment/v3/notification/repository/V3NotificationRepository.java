package com.capstone.assessment.v3.notification.repository;

import com.capstone.assessment.v3.notification.dto.V3NotificationResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Profile("v3")
@Repository
public class V3NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public V3NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertForUser(
            long recipientUserId,
            String notificationType,
            String title,
            String message,
            String referenceType,
            String referenceId,
            String eventKey,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO notifications (
                    notification_uuid,
                    recipient_user_id,
                    notification_type,
                    title,
                    message,
                    reference_type,
                    reference_id,
                    event_key,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE notification_id = notification_id
                """,
                UUID.randomUUID().toString(),
                recipientUserId,
                notificationType,
                title,
                message,
                referenceType,
                referenceId,
                eventKey,
                Timestamp.from(createdAt)
        );
    }

    public void insertForSchoolPrincipalsOfUser(
            long sourceUserId,
            String notificationType,
            String title,
            String message,
            String referenceType,
            String referenceId,
            String eventKey,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO notifications (
                    notification_uuid,
                    recipient_user_id,
                    notification_type,
                    title,
                    message,
                    reference_type,
                    reference_id,
                    event_key,
                    created_at
                )
                SELECT UUID(),
                       principal.user_id,
                       ?, ?, ?, ?, ?, ?, ?
                  FROM users applicant
                  JOIN users principal ON principal.school_id = applicant.school_id
                  JOIN roles role_ref ON role_ref.role_id = principal.role_id
                  JOIN statuses status_ref ON status_ref.status_id = principal.status_id
                 WHERE applicant.user_id = ?
                   AND role_ref.role_name = 'principal'
                   AND status_ref.status_name = 'active'
                ON DUPLICATE KEY UPDATE notification_id = notification_id
                """,
                notificationType,
                title,
                message,
                referenceType,
                referenceId,
                eventKey,
                Timestamp.from(createdAt),
                sourceUserId
        );
    }

    public List<V3NotificationResponse> findForUser(long userId, boolean unreadOnly, int limit) {
        String unreadFilter = unreadOnly ? " AND n.read_at IS NULL" : "";
        return jdbcTemplate.query(
                """
                SELECT n.notification_id,
                       n.notification_uuid,
                       n.notification_type,
                       n.title,
                       n.message,
                       n.reference_type,
                       n.reference_id,
                       n.read_at,
                       n.created_at
                  FROM notifications n
                 WHERE n.recipient_user_id = ?
                """ + unreadFilter + " ORDER BY n.created_at DESC, n.notification_id DESC LIMIT ?",
                (resultSet, rowNumber) -> {
                    Timestamp readAt = resultSet.getTimestamp("read_at");
                    return new V3NotificationResponse(
                            resultSet.getLong("notification_id"),
                            resultSet.getString("notification_uuid"),
                            resultSet.getString("notification_type"),
                            resultSet.getString("title"),
                            resultSet.getString("message"),
                            resultSet.getString("reference_type"),
                            resultSet.getString("reference_id"),
                            readAt != null,
                            resultSet.getTimestamp("created_at").toInstant(),
                            readAt == null ? null : readAt.toInstant()
                    );
                },
                userId,
                limit
        );
    }

    public long countUnread(long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ? AND read_at IS NULL",
                Long.class,
                userId
        );
        return count == null ? 0 : count;
    }

    public int markRead(long notificationId, long userId, Instant readAt) {
        return jdbcTemplate.update(
                """
                UPDATE notifications
                   SET read_at = ?
                 WHERE notification_id = ?
                   AND recipient_user_id = ?
                   AND read_at IS NULL
                """,
                Timestamp.from(readAt),
                notificationId,
                userId
        );
    }

    public boolean existsForUser(long notificationId, long userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM notifications
                 WHERE notification_id = ?
                   AND recipient_user_id = ?
                """,
                Long.class,
                notificationId,
                userId
        );
        return count != null && count > 0;
    }

    public int markAllRead(long userId, Instant readAt) {
        return jdbcTemplate.update(
                """
                UPDATE notifications
                   SET read_at = ?
                 WHERE recipient_user_id = ?
                   AND read_at IS NULL
                """,
                Timestamp.from(readAt),
                userId
        );
    }
}
