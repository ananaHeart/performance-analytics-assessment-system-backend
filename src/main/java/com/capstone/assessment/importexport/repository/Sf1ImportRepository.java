package com.capstone.assessment.importexport.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

@Repository
public class Sf1ImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public Sf1ImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean sectionExists(Long sectionId) {
        String sql = """
                SELECT COUNT(*)
                FROM section
                WHERE section_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, sectionId);
        return count != null && count > 0;
    }

    public boolean sectionMatchesAcademicYear(Long sectionId, Long academicYearId) {
        if (!sectionHasAcademicYearColumn()) {
            return sectionExists(sectionId);
        }

        String sql = """
                SELECT COUNT(*)
                FROM section
                WHERE section_id = ?
                  AND academic_year_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, sectionId, academicYearId);
        return count != null && count > 0;
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

    public boolean gradeLevelExists(Long gradeLevelId) {
        String sql = """
                SELECT COUNT(*)
                FROM grade_level
                WHERE grade_level_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, gradeLevelId);
        return count != null && count > 0;
    }

    public Optional<Long> findGradeLevelIdByName(String gradeLevelName) {
        String sql = """
                SELECT grade_level_id
                FROM grade_level
                WHERE LOWER(grade_level_name) = LOWER(?)
                ORDER BY grade_level_id
                """;

        return jdbcTemplate.query(
                sql,
                rs -> rs.next() ? Optional.of(rs.getLong("grade_level_id")) : Optional.empty(),
                gradeLevelName
        );
    }

    public Optional<Long> findAcademicYearIdByName(String academicYear) {
        String sql = """
                SELECT academic_year_id
                FROM academic_year
                WHERE LOWER(year_name) = LOWER(?)
                """;

        return jdbcTemplate.query(
                sql,
                rs -> rs.next() ? Optional.of(rs.getLong("academic_year_id")) : Optional.empty(),
                academicYear
        );
    }

    public Optional<Long> findSectionIdByContext(String sectionName, Long gradeLevelId, Long academicYearId) {
        String sql = sectionHasAcademicYearColumn()
                ? """
                    SELECT section_id
                    FROM section
                    WHERE LOWER(section_name) = LOWER(?)
                      AND grade_level_id = ?
                      AND academic_year_id = ?
                    ORDER BY section_id
                    """
                : """
                    SELECT section_id
                    FROM section
                    WHERE LOWER(section_name) = LOWER(?)
                      AND grade_level_id = ?
                    ORDER BY section_id
                    """;

        if (sectionHasAcademicYearColumn()) {
            return jdbcTemplate.query(
                    sql,
                    rs -> rs.next() ? Optional.of(rs.getLong("section_id")) : Optional.empty(),
                    sectionName,
                    gradeLevelId,
                    academicYearId
            );
        }

        return jdbcTemplate.query(
                sql,
                rs -> rs.next() ? Optional.of(rs.getLong("section_id")) : Optional.empty(),
                sectionName,
                gradeLevelId
        );
    }

    public Long createSection(Long gradeLevelId, Long academicYearId, String sectionName) {
        String sql = sectionHasAcademicYearColumn()
                ? """
                    INSERT INTO section (grade_level_id, academic_year_id, section_name)
                    VALUES (?, ?, ?)
                    """
                : """
                    INSERT INTO section (grade_level_id, section_name)
                    VALUES (?, ?)
                    """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setLong(1, gradeLevelId);
            if (sectionHasAcademicYearColumn()) {
                preparedStatement.setLong(2, academicYearId);
                preparedStatement.setString(3, sectionName);
            } else {
                preparedStatement.setString(2, sectionName);
            }
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated section_id").longValue();
    }

    private boolean sectionHasAcademicYearColumn() {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'section'
                  AND column_name = 'academic_year_id'
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null && count > 0;
    }

    public Optional<Long> findStudentIdByLrn(String studentLrn) {
        String sql = """
                SELECT student_id
                FROM student
                WHERE student_lrn = ?
                """;

        return jdbcTemplate.query(sql, rs -> rs.next() ? Optional.of(rs.getLong("student_id")) : Optional.empty(), studentLrn);
    }

    public Long createStudent(String studentLrn, String firstName, String lastName, String gender) {
        String sql = """
                INSERT INTO student (student_lrn, first_name, last_name, gender)
                VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, studentLrn);
            preparedStatement.setString(2, firstName);
            preparedStatement.setString(3, lastName);
            preparedStatement.setString(4, gender);
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated student_id").longValue();
    }

    public void updateStudent(Long studentId, String firstName, String lastName, String gender) {
        String sql = """
                UPDATE student
                SET first_name = ?,
                    last_name = ?,
                    gender = ?
                WHERE student_id = ?
                """;

        jdbcTemplate.update(sql, firstName, lastName, gender, studentId);
    }

    public boolean enrollmentExists(Long studentId, Long sectionId, Long academicYearId) {
        String sql = """
                SELECT COUNT(*)
                FROM student_enrollment
                WHERE student_id = ?
                  AND section_id = ?
                  AND academic_year_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, studentId, sectionId, academicYearId);
        return count != null && count > 0;
    }

    public void enrollStudent(Long studentId, Long sectionId, Long academicYearId) {
        String sql = """
                INSERT INTO student_enrollment (student_id, section_id, academic_year_id)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(sql, studentId, sectionId, academicYearId);
    }
}
