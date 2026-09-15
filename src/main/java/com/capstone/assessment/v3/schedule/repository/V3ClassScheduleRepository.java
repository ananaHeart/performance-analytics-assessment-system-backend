package com.capstone.assessment.v3.schedule.repository;

import com.capstone.assessment.v3.schedule.model.V3ClassScheduleModels.AssignmentContext;
import com.capstone.assessment.v3.schedule.model.V3ClassScheduleModels.ScheduleRow;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3ClassScheduleRepository {

    private static final String ASSIGNMENT_SELECT = """
            SELECT assignment.class_assignment_id,
                   assignment.user_id AS teacher_user_id,
                   teacher.school_id,
                   assignment.status AS assignment_status,
                   class_row.status AS class_status,
                   academic_year.academic_year_id,
                   academic_year.start_date AS academic_year_start_date,
                   academic_year.end_date AS academic_year_end_date
              FROM class_assignments assignment
              JOIN users teacher ON teacher.user_id = assignment.user_id
              JOIN classes class_row ON class_row.class_id = assignment.class_id
              JOIN academic_years academic_year
                ON academic_year.academic_year_id = class_row.academic_year_id
            """;

    private static final String SCHEDULE_SELECT = """
            SELECT schedule.class_assignment_schedule_id,
                   schedule.schedule_uuid,
                   schedule.class_assignment_id,
                   schedule.day_of_week,
                   schedule.start_time,
                   schedule.end_time,
                   schedule.timezone_name,
                   schedule.effective_from,
                   schedule.effective_to,
                   schedule.schedule_status,
                   schedule.status_changed_by_user_id,
                   schedule.status_reason,
                   schedule.archived_at,
                   schedule.created_at,
                   schedule.updated_at
              FROM class_assignment_schedules schedule
            """;

    private final JdbcTemplate jdbcTemplate;

    public V3ClassScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockTeacher(long teacherUserId) {
        jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE",
                Long.class,
                teacherUserId
        );
    }

    public Optional<AssignmentContext> findAssignmentContext(long classAssignmentId) {
        return jdbcTemplate.query(
                ASSIGNMENT_SELECT + " WHERE assignment.class_assignment_id = ?",
                this::mapAssignment,
                classAssignmentId
        ).stream().findFirst();
    }

    public List<ScheduleRow> listSchedules(long classAssignmentId) {
        return jdbcTemplate.query(
                SCHEDULE_SELECT + """
                         WHERE schedule.class_assignment_id = ?
                         ORDER BY schedule.day_of_week,
                                  schedule.start_time,
                                  schedule.effective_from,
                                  schedule.class_assignment_schedule_id
                        """,
                this::mapSchedule,
                classAssignmentId
        );
    }

    public Optional<ScheduleRow> findSchedule(long classAssignmentId, long scheduleId) {
        return jdbcTemplate.query(
                SCHEDULE_SELECT + """
                         WHERE schedule.class_assignment_id = ?
                           AND schedule.class_assignment_schedule_id = ?
                        """,
                this::mapSchedule,
                classAssignmentId,
                scheduleId
        ).stream().findFirst();
    }

    public boolean hasConflictingActiveSchedule(
            long teacherUserId,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Long excludedScheduleId
    ) {
        Boolean conflict = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM class_assignment_schedules schedule
                      JOIN class_assignments assignment
                        ON assignment.class_assignment_id = schedule.class_assignment_id
                     WHERE assignment.user_id = ?
                       AND assignment.status = 'active'
                       AND schedule.schedule_status = 'active'
                       AND schedule.day_of_week = ?
                       AND schedule.start_time < ?
                       AND schedule.end_time > ?
                       AND schedule.effective_from <= COALESCE(?, '9999-12-31')
                       AND COALESCE(schedule.effective_to, '9999-12-31') >= ?
                       AND (? IS NULL OR schedule.class_assignment_schedule_id <> ?)
                )
                """,
                Boolean.class,
                teacherUserId,
                dayOfWeek,
                Time.valueOf(endTime),
                Time.valueOf(startTime),
                toDate(effectiveTo),
                Date.valueOf(effectiveFrom),
                excludedScheduleId,
                excludedScheduleId
        );
        return Boolean.TRUE.equals(conflict);
    }

    public long insertSchedule(
            String scheduleUuid,
            long classAssignmentId,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String timezoneName,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            long createdByUserId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO class_assignment_schedules (
                        schedule_uuid,
                        class_assignment_id,
                        day_of_week,
                        start_time,
                        end_time,
                        timezone_name,
                        effective_from,
                        effective_to,
                        created_by_user_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, scheduleUuid);
            statement.setLong(2, classAssignmentId);
            statement.setInt(3, dayOfWeek);
            statement.setTime(4, Time.valueOf(startTime));
            statement.setTime(5, Time.valueOf(endTime));
            statement.setString(6, timezoneName);
            statement.setDate(7, Date.valueOf(effectiveFrom));
            statement.setDate(8, toDate(effectiveTo));
            statement.setLong(9, createdByUserId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a class schedule identifier.");
        }
        return key.longValue();
    }

    public int updateSchedule(
            long scheduleId,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String timezoneName,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        return jdbcTemplate.update("""
                UPDATE class_assignment_schedules
                   SET day_of_week = ?,
                       start_time = ?,
                       end_time = ?,
                       timezone_name = ?,
                       effective_from = ?,
                       effective_to = ?
                 WHERE class_assignment_schedule_id = ?
                   AND schedule_status = 'active'
                """,
                dayOfWeek,
                Time.valueOf(startTime),
                Time.valueOf(endTime),
                timezoneName,
                Date.valueOf(effectiveFrom),
                toDate(effectiveTo),
                scheduleId
        );
    }

    public int archiveSchedule(long scheduleId, long userId, String reason, Instant archivedAt) {
        return jdbcTemplate.update("""
                UPDATE class_assignment_schedules
                   SET schedule_status = 'archived',
                       status_changed_by_user_id = ?,
                       status_reason = ?,
                       archived_at = ?
                 WHERE class_assignment_schedule_id = ?
                   AND schedule_status = 'active'
                """,
                userId,
                reason,
                Timestamp.from(archivedAt),
                scheduleId
        );
    }

    private AssignmentContext mapAssignment(ResultSet rs, int rowNum) throws SQLException {
        return new AssignmentContext(
                rs.getLong("class_assignment_id"),
                rs.getLong("teacher_user_id"),
                rs.getString("school_id"),
                rs.getString("assignment_status"),
                rs.getString("class_status"),
                rs.getInt("academic_year_id"),
                rs.getDate("academic_year_start_date").toLocalDate(),
                rs.getDate("academic_year_end_date").toLocalDate()
        );
    }

    private ScheduleRow mapSchedule(ResultSet rs, int rowNum) throws SQLException {
        Timestamp archivedAt = rs.getTimestamp("archived_at");
        Long statusUserId = rs.getObject("status_changed_by_user_id", Long.class);
        Date effectiveTo = rs.getDate("effective_to");
        return new ScheduleRow(
                rs.getLong("class_assignment_schedule_id"),
                rs.getString("schedule_uuid"),
                rs.getLong("class_assignment_id"),
                rs.getInt("day_of_week"),
                rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time").toLocalTime(),
                rs.getString("timezone_name"),
                rs.getDate("effective_from").toLocalDate(),
                effectiveTo == null ? null : effectiveTo.toLocalDate(),
                rs.getString("schedule_status"),
                statusUserId,
                rs.getString("status_reason"),
                archivedAt == null ? null : archivedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private Date toDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
