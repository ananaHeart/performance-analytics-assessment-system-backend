package com.capstone.assessment.v3.school.repository;

import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.AcademicYearRow;
import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.TermPeriodRow;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3AcademicCalendarRepository {

    private static final String YEAR_SELECT = """
            SELECT ay.academic_year_id,
                   ay.school_id,
                   ay.curriculum_id,
                   cur.curriculum_name,
                   ay.year_name,
                   ay.start_date,
                   ay.end_date,
                   ay.status,
                   ay.created_at,
                   ay.updated_at
            FROM academic_years ay
            JOIN curriculums cur ON cur.curriculum_id = ay.curriculum_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public V3AcademicCalendarRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean curriculumExists(int curriculumId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM curriculums WHERE curriculum_id = ?)",
                Boolean.class,
                curriculumId
        ));
    }

    public List<AcademicYearRow> listAcademicYears(String schoolId) {
        return jdbcTemplate.query(
                YEAR_SELECT + " WHERE ay.school_id = ? ORDER BY ay.start_date DESC, ay.academic_year_id DESC",
                this::mapAcademicYear,
                schoolId
        );
    }

    public List<AcademicYearRow> listAutomaticTransitionCandidates(LocalDate schoolDate) {
        return jdbcTemplate.query(
                YEAR_SELECT + """
                         WHERE (ay.status = 'planned' AND ay.start_date <= ?)
                            OR ay.status = 'active'
                         ORDER BY ay.school_id, ay.start_date, ay.academic_year_id
                        """,
                this::mapAcademicYear,
                schoolDate
        );
    }

    public Optional<AcademicYearRow> findAcademicYear(String schoolId, int academicYearId) {
        return jdbcTemplate.query(
                YEAR_SELECT + " WHERE ay.school_id = ? AND ay.academic_year_id = ?",
                this::mapAcademicYear,
                schoolId,
                academicYearId
        ).stream().findFirst();
    }

    public List<TermPeriodRow> listTermPeriods(int academicYearId) {
        return jdbcTemplate.query("""
                        SELECT term_period_id,
                               academic_year_id,
                               term_name,
                               term_order,
                               start_at,
                               end_at,
                               status,
                               activation_mode,
                               activated_at,
                               completed_at,
                               overridden_by_user_id,
                               override_reason,
                               overridden_at
                        FROM term_periods
                        WHERE academic_year_id = ?
                        ORDER BY term_order
                        """,
                this::mapTermPeriod,
                academicYearId
        );
    }

    public Optional<TermPeriodRow> findTermPeriod(int academicYearId, int termPeriodId) {
        return jdbcTemplate.query("""
                        SELECT term_period_id,
                               academic_year_id,
                               term_name,
                               term_order,
                               start_at,
                               end_at,
                               status,
                               activation_mode,
                               activated_at,
                               completed_at,
                               overridden_by_user_id,
                               override_reason,
                               overridden_at
                        FROM term_periods
                        WHERE academic_year_id = ? AND term_period_id = ?
                        """,
                this::mapTermPeriod,
                academicYearId,
                termPeriodId
        ).stream().findFirst();
    }

    public void lockAcademicYear(String schoolId, int academicYearId) {
        jdbcTemplate.queryForObject("""
                        SELECT academic_year_id
                        FROM academic_years
                        WHERE school_id = ? AND academic_year_id = ?
                        FOR UPDATE
                        """,
                Integer.class,
                schoolId,
                academicYearId
        );
    }

    public boolean anotherActiveAcademicYearExists(String schoolId, int academicYearId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM academic_years
                            WHERE school_id = ?
                              AND status = 'active'
                              AND academic_year_id <> ?
                        )
                        """,
                Boolean.class,
                schoolId,
                academicYearId
        ));
    }

    public boolean anotherActiveTermExists(int academicYearId, int termPeriodId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM term_periods
                            WHERE academic_year_id = ?
                              AND status = 'active'
                              AND term_period_id <> ?
                        )
                        """,
                Boolean.class,
                academicYearId,
                termPeriodId
        ));
    }

    public boolean priorIncompleteTermExists(int academicYearId, int termOrder) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM term_periods
                            WHERE academic_year_id = ?
                              AND term_order < ?
                              AND status <> 'completed'
                        )
                        """,
                Boolean.class,
                academicYearId,
                termOrder
        ));
    }

    public long insertAcademicYear(
            String schoolId,
            int curriculumId,
            String yearName,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return insertAndReturnId("""
                INSERT INTO academic_years (
                    school_id, curriculum_id, year_name, start_date, end_date, status
                ) VALUES (?, ?, ?, ?, ?, 'planned')
                """, schoolId, curriculumId, yearName, startDate, endDate);
    }

    public long insertTermPeriod(
            int academicYearId,
            String termName,
            int termOrder,
            Instant startAt,
            Instant endAt,
            String activationMode
    ) {
        return insertAndReturnId("""
                INSERT INTO term_periods (
                    academic_year_id, term_name, term_order, start_at, end_at,
                    status, activation_mode
                ) VALUES (?, ?, ?, ?, ?, 'planned', ?)
                """,
                academicYearId,
                termName,
                termOrder,
                Timestamp.from(startAt),
                Timestamp.from(endAt),
                activationMode
        );
    }

    public int updateAcademicYear(
            int academicYearId,
            int curriculumId,
            String yearName,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return jdbcTemplate.update("""
                        UPDATE academic_years
                        SET curriculum_id = ?, year_name = ?, start_date = ?, end_date = ?
                        WHERE academic_year_id = ? AND status = 'planned'
                        """,
                curriculumId,
                yearName,
                startDate,
                endDate,
                academicYearId
        );
    }

    public int updateTermPeriod(
            int academicYearId,
            int termOrder,
            String termName,
            Instant startAt,
            Instant endAt,
            String activationMode
    ) {
        return jdbcTemplate.update("""
                        UPDATE term_periods
                        SET term_name = ?, start_at = ?, end_at = ?, activation_mode = ?
                        WHERE academic_year_id = ? AND term_order = ? AND status = 'planned'
                        """,
                termName,
                Timestamp.from(startAt),
                Timestamp.from(endAt),
                activationMode,
                academicYearId,
                termOrder
        );
    }

    public int activateAcademicYear(int academicYearId) {
        return jdbcTemplate.update("""
                UPDATE academic_years
                SET status = 'active'
                WHERE academic_year_id = ? AND status = 'planned'
                """, academicYearId);
    }

    public int activateAcademicYearAutomatically(int academicYearId) {
        return jdbcTemplate.update("""
                UPDATE academic_years
                SET status = 'active'
                WHERE academic_year_id = ? AND status = 'planned'
                """, academicYearId);
    }

    public int completeAcademicYear(int academicYearId) {
        return jdbcTemplate.update("""
                UPDATE academic_years
                SET status = 'completed'
                WHERE academic_year_id = ? AND status = 'active'
                """, academicYearId);
    }

    public int completeAcademicYearAutomatically(int academicYearId) {
        return jdbcTemplate.update("""
                UPDATE academic_years
                SET status = 'completed'
                WHERE academic_year_id = ? AND status = 'active'
                """, academicYearId);
    }

    public int activateTermPeriod(int termPeriodId, long userId, String reason, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE term_periods
                        SET status = 'active',
                            activation_mode = 'manual',
                            activated_at = ?,
                            completed_at = NULL,
                            overridden_by_user_id = ?,
                            override_reason = ?,
                            overridden_at = ?
                        WHERE term_period_id = ? AND status = 'planned'
                        """,
                Timestamp.from(now),
                userId,
                reason,
                Timestamp.from(now),
                termPeriodId
        );
    }

    public int completeTermPeriod(int termPeriodId, long userId, String reason, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE term_periods
                        SET status = 'completed',
                            activation_mode = 'manual',
                            completed_at = ?,
                            overridden_by_user_id = ?,
                            override_reason = ?,
                            overridden_at = ?
                        WHERE term_period_id = ? AND status = 'active'
                        """,
                Timestamp.from(now),
                userId,
                reason,
                Timestamp.from(now),
                termPeriodId
        );
    }

    public int activateTermPeriodAutomatically(int termPeriodId, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE term_periods
                        SET status = 'active',
                            activated_at = ?,
                            completed_at = NULL
                        WHERE term_period_id = ?
                          AND status = 'planned'
                          AND activation_mode = 'automatic'
                        """,
                Timestamp.from(now),
                termPeriodId
        );
    }

    public int completeTermPeriodAutomatically(int termPeriodId, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE term_periods
                        SET status = 'completed',
                            completed_at = ?
                        WHERE term_period_id = ?
                          AND status = 'active'
                          AND activation_mode = 'automatic'
                        """,
                Timestamp.from(now),
                termPeriodId
        );
    }

    private AcademicYearRow mapAcademicYear(ResultSet rs, int rowNum) throws SQLException {
        return new AcademicYearRow(
                rs.getInt("academic_year_id"),
                rs.getString("school_id"),
                rs.getInt("curriculum_id"),
                rs.getString("curriculum_name"),
                rs.getString("year_name"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private TermPeriodRow mapTermPeriod(ResultSet rs, int rowNum) throws SQLException {
        long overriddenBy = rs.getLong("overridden_by_user_id");
        return new TermPeriodRow(
                rs.getInt("term_period_id"),
                rs.getInt("academic_year_id"),
                rs.getString("term_name"),
                rs.getInt("term_order"),
                toInstant(rs.getTimestamp("start_at")),
                toInstant(rs.getTimestamp("end_at")),
                rs.getString("status"),
                rs.getString("activation_mode"),
                toInstant(rs.getTimestamp("activated_at")),
                toInstant(rs.getTimestamp("completed_at")),
                rs.wasNull() ? null : overriddenBy,
                rs.getString("override_reason"),
                toInstant(rs.getTimestamp("overridden_at"))
        );
    }

    private long insertAndReturnId(String sql, Object... parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return a generated identifier.");
        }
        return key.longValue();
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
