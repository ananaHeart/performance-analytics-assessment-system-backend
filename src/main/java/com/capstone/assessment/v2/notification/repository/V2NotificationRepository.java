package com.capstone.assessment.v2.notification.repository;

import com.capstone.assessment.v2.notification.dto.V2NotificationResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Profile("v2")
@Repository
public class V2NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public V2NotificationRepository(JdbcTemplate jdbcTemplate) {
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

    public void insertForSchoolPrincipals(
            String schoolId,
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
                       u.user_id,
                       ?, ?, ?, ?, ?, ?, ?
                  FROM users u
                  JOIN roles r ON r.role_id = u.role_id
                  JOIN statuses s ON s.status_id = u.status_id
                 WHERE u.school_id = ?
                   AND r.role_name = 'principal'
                   AND s.status_name = 'active'
                ON DUPLICATE KEY UPDATE notification_id = notification_id
                """,
                notificationType,
                title,
                message,
                referenceType,
                referenceId,
                eventKey,
                Timestamp.from(createdAt),
                schoolId
        );
    }

    public List<V2NotificationResponse> findForUser(long userId, boolean unreadOnly, int limit) {
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
                (resultSet, rowNumber) -> new V2NotificationResponse(
                        resultSet.getLong("notification_id"),
                        resultSet.getString("notification_uuid"),
                        resultSet.getString("notification_type"),
                        resultSet.getString("title"),
                        resultSet.getString("message"),
                        resultSet.getString("reference_type"),
                        resultSet.getString("reference_id"),
                        resultSet.getTimestamp("read_at") != null,
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("read_at") == null
                                ? null
                                : resultSet.getTimestamp("read_at").toInstant()
                ),
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
                   SET read_at = COALESCE(read_at, ?)
                 WHERE notification_id = ?
                   AND recipient_user_id = ?
                """,
                Timestamp.from(readAt),
                notificationId,
                userId
        );
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
