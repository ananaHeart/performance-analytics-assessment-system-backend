USE performance_assessment_v2_db;

CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Internal notification identifier.',
    notification_uuid CHAR(36) NOT NULL COMMENT 'Public notification identifier.',
    recipient_user_id BIGINT UNSIGNED NOT NULL COMMENT 'User who may view and read this notification.',
    notification_type VARCHAR(50) NOT NULL COMMENT 'Machine-readable event type.',
    title VARCHAR(120) NOT NULL COMMENT 'Short user-facing notification title.',
    message VARCHAR(500) NOT NULL COMMENT 'User-facing notification message.',
    reference_type VARCHAR(50) NULL COMMENT 'Related entity type, such as users or syncs.',
    reference_id VARCHAR(100) NULL COMMENT 'Related entity identifier.',
    event_key VARCHAR(150) NOT NULL COMMENT 'Idempotency key preventing duplicate event notifications per recipient.',
    read_at TIMESTAMP NULL COMMENT 'Timestamp when the recipient marked the notification as read.',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Timestamp when the notification was created.',
    CONSTRAINT pk_notifications PRIMARY KEY (notification_id),
    CONSTRAINT uk_notifications_uuid UNIQUE (notification_uuid),
    CONSTRAINT uk_notifications_recipient_event UNIQUE (recipient_user_id, event_key),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    INDEX idx_notifications_recipient_read_created (recipient_user_id, read_at, created_at),
    INDEX idx_notifications_reference (reference_type, reference_id)
) COMMENT='Persistent role-scoped notifications and unread state for authenticated users.';
