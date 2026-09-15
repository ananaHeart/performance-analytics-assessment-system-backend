package com.capstone.assessment.v3.evidence.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Profile("v3")
@Repository
public class V3AnswerAttachmentLineageRepository {

    private final JdbcTemplate jdbcTemplate;

    public V3AnswerAttachmentLineageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<V3AnswerAttachmentLineage> findById(long answerAttachmentId) {
        return jdbcTemplate.query("""
                        SELECT
                            answer_attachment_id,
                            attachment_type,
                            source_answer_attachment_id,
                            scan_session_id,
                            scan_page_id,
                            student_answer_id,
                            answer_sheet_region_id
                        FROM answer_attachments
                        WHERE answer_attachment_id = ?
                        """,
                (resultSet, rowNumber) -> new V3AnswerAttachmentLineage(
                        resultSet.getLong("answer_attachment_id"),
                        resultSet.getString("attachment_type"),
                        nullableLong(resultSet, "source_answer_attachment_id"),
                        nullableLong(resultSet, "scan_session_id"),
                        nullableLong(resultSet, "scan_page_id"),
                        nullableLong(resultSet, "student_answer_id"),
                        nullableLong(resultSet, "answer_sheet_region_id")
                ),
                answerAttachmentId
        ).stream().findFirst();
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String columnName)
            throws java.sql.SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    public record V3AnswerAttachmentLineage(
            long answerAttachmentId,
            String attachmentType,
            Long sourceAnswerAttachmentId,
            Long scanSessionId,
            Long scanPageId,
            Long studentAnswerId,
            Long answerSheetRegionId
    ) {
    }
}
