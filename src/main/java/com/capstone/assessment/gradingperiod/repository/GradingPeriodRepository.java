package com.capstone.assessment.gradingperiod.repository;

import com.capstone.assessment.gradingperiod.dto.CreateGradingPeriodRequest;
import com.capstone.assessment.gradingperiod.dto.GradingPeriodDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

@Repository
public class GradingPeriodRepository {

    private final JdbcTemplate jdbcTemplate;

    public GradingPeriodRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GradingPeriodDto> findByAcademicYearId(Long academicYearId) {
        String sql = """
                SELECT gp.grading_period_id,
                       gp.academic_year_id,
                       ay.year_name AS academic_year,
                       gp.period_name,
                       gp.period_order,
                       gp.start_date,
                       gp.end_date,
                       gp.status
                FROM grading_period gp
                JOIN academic_year ay ON ay.academic_year_id = gp.academic_year_id
                WHERE gp.academic_year_id = ?
                ORDER BY gp.period_order, gp.start_date, gp.grading_period_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new GradingPeriodDto(
                rs.getLong("grading_period_id"),
                rs.getLong("academic_year_id"),
                rs.getString("academic_year"),
                rs.getString("period_name"),
                rs.getInt("period_order"),
                rs.getObject("start_date", Date.class).toLocalDate(),
                rs.getObject("end_date", Date.class).toLocalDate(),
                rs.getString("status")
        ), academicYearId);
    }

    public Long create(CreateGradingPeriodRequest request) {
        String sql = """
                INSERT INTO grading_period (
                    academic_year_id,
                    period_name,
                    period_order,
                    start_date,
                    end_date,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setLong(1, request.academicYearId());
            preparedStatement.setString(2, request.periodName());
            preparedStatement.setInt(3, request.periodOrder());
            preparedStatement.setObject(4, request.startDate());
            preparedStatement.setObject(5, request.endDate());
            preparedStatement.setString(6, request.status());
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated grading_period_id").longValue();
    }

    public boolean academicYearExists(Long academicYearId) {
        String sql = """
                SELECT COUNT(*)
                FROM academic_year
                WHERE academic_year_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, academicYearId);
        return count != null && count > 0;
    }

    public boolean periodNameExists(Long academicYearId, String periodName) {
        String sql = """
                SELECT COUNT(*)
                FROM grading_period
                WHERE academic_year_id = ?
                  AND LOWER(period_name) = LOWER(?)
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, academicYearId, periodName);
        return count != null && count > 0;
    }

    public boolean periodOrderExists(Long academicYearId, Integer periodOrder) {
        String sql = """
                SELECT COUNT(*)
                FROM grading_period
                WHERE academic_year_id = ?
                  AND period_order = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, academicYearId, periodOrder);
        return count != null && count > 0;
    }
}
