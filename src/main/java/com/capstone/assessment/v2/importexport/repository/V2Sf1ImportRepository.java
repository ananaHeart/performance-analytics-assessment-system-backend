package com.capstone.assessment.v2.importexport.repository;

import com.capstone.assessment.v2.importexport.dto.V2StudentRecordResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Profile("v2")
@Repository
public class V2Sf1ImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public V2Sf1ImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean academicYearExists(int academicYearId) {
        return exists("SELECT COUNT(*) FROM academic_years WHERE academic_year_id = ?", academicYearId);
    }

    public Optional<String> findAcademicYearName(int academicYearId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT year_name FROM academic_years WHERE academic_year_id = ?",
                (rs, rowNum) -> rs.getString("year_name"),
                academicYearId
        );
        return rows.stream().findFirst();
    }

    public boolean gradeLevelExists(int gradeLevelId) {
        return exists("SELECT COUNT(*) FROM grade_levels WHERE grade_level_id = ?", gradeLevelId);
    }

    public Optional<Integer> findAcademicYearIdByName(String academicYearName) {
        List<Integer> rows = jdbcTemplate.query(
                """
                SELECT academic_year_id
                  FROM academic_years
                 WHERE LOWER(year_name) = LOWER(?)
                 ORDER BY academic_year_id
                """,
                (rs, rowNum) -> rs.getInt("academic_year_id"),
                academicYearName
        );
        return rows.stream().findFirst();
    }

    public Optional<Integer> findGradeLevelIdByName(String gradeLevelName) {
        List<Integer> rows = jdbcTemplate.query(
                """
                SELECT grade_level_id
                  FROM grade_levels
                 WHERE LOWER(grade_level_name) = LOWER(?)
                 ORDER BY grade_level_id
                """,
                (rs, rowNum) -> rs.getInt("grade_level_id"),
                gradeLevelName
        );
        return rows.stream().findFirst();
    }

    public Optional<Integer> findSectionId(String sectionName, int gradeLevelId) {
        List<Integer> rows = jdbcTemplate.query(
                """
                SELECT section_id
                  FROM sections
                 WHERE grade_level_id = ?
                   AND LOWER(section_name) = LOWER(?)
                 ORDER BY section_id
                """,
                (rs, rowNum) -> rs.getInt("section_id"),
                gradeLevelId,
                sectionName
        );
        return rows.stream().findFirst();
    }

    public int createSection(String sectionName, int gradeLevelId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO sections (grade_level_id, section_name) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setInt(1, gradeLevelId);
            statement.setString(2, sectionName);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated section_id.");
        }
        return key.intValue();
    }

    public Optional<Long> findClassId(int academicYearId, int sectionId) {
        List<Long> rows = jdbcTemplate.query(
                """
                SELECT class_id
                  FROM classes
                 WHERE academic_year_id = ?
                   AND section_id = ?
                 ORDER BY class_id
                """,
                (rs, rowNum) -> rs.getLong("class_id"),
                academicYearId,
                sectionId
        );
        return rows.stream().findFirst();
    }

    public long createClass(int academicYearId, int sectionId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO classes (academic_year_id, section_id, status)
                    VALUES (?, ?, 'active')
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setInt(1, academicYearId);
            statement.setInt(2, sectionId);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated class_id.");
        }
        return key.longValue();
    }

    public Optional<ExistingStudent> findStudentByLrn(String studentLrn) {
        List<ExistingStudent> rows = jdbcTemplate.query(
                """
                SELECT student_id, school_id
                  FROM students
                 WHERE student_lrn = ?
                """,
                (rs, rowNum) -> new ExistingStudent(
                        rs.getLong("student_id"),
                        rs.getString("school_id")
                ),
                studentLrn
        );
        return rows.stream().findFirst();
    }

    public Optional<Integer> findGenderId(String genderName) {
        List<Integer> rows = jdbcTemplate.query(
                """
                SELECT gender_id
                  FROM genders
                 WHERE LOWER(gender_name) = LOWER(?)
                """,
                (rs, rowNum) -> rs.getInt("gender_id"),
                genderName
        );
        return rows.stream().findFirst();
    }

    public long createSf1Address() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> connection.prepareStatement(
                """
                INSERT INTO addresses (country_code, address_source)
                VALUES ('PH', 'sf1_import')
                """,
                Statement.RETURN_GENERATED_KEYS
        ), keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated address_id.");
        }
        return key.longValue();
    }

    public long createStudent(
            String schoolId,
            long addressId,
            int genderId,
            String studentLrn,
            String firstName,
            String lastName
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO students (
                        school_id,
                        address_id,
                        gender_id,
                        student_lrn,
                        first_name,
                        last_name,
                        status
                    ) VALUES (?, ?, ?, ?, ?, ?, 'active')
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, schoolId);
            statement.setLong(2, addressId);
            statement.setInt(3, genderId);
            statement.setString(4, studentLrn);
            statement.setString(5, firstName);
            statement.setString(6, lastName);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated student_id.");
        }
        return key.longValue();
    }

    public void updateStudent(
            long studentId,
            int genderId,
            String firstName,
            String lastName
    ) {
        jdbcTemplate.update(
                """
                UPDATE students
                   SET gender_id = ?,
                       first_name = ?,
                       last_name = ?
                 WHERE student_id = ?
                """,
                genderId,
                firstName,
                lastName,
                studentId
        );
    }

    public boolean classMembershipExists(long classId, long studentId) {
        return exists(
                """
                SELECT COUNT(*)
                  FROM class_lists
                 WHERE class_id = ?
                   AND student_id = ?
                """,
                classId,
                studentId
        );
    }

    public void addStudentToClass(long classId, long studentId) {
        jdbcTemplate.update(
                "INSERT INTO class_lists (class_id, student_id) VALUES (?, ?)",
                classId,
                studentId
        );
    }

    public List<V2StudentRecordResponse> listStudents(String schoolId, Integer academicYearId) {
        return jdbcTemplate.query(
                """
                SELECT st.student_id,
                       cl.class_list_id,
                       c.class_id,
                       st.student_lrn,
                       st.first_name,
                       st.middle_name,
                       st.last_name,
                       g.gender_name,
                       s.section_id,
                       s.section_name,
                       gl.grade_level_name,
                       ay.academic_year_id,
                       ay.year_name
                  FROM students st
                  JOIN genders g ON g.gender_id = st.gender_id
                  JOIN class_lists cl ON cl.student_id = st.student_id
                  JOIN classes c ON c.class_id = cl.class_id
                  JOIN sections s ON s.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = s.grade_level_id
                  JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
                 WHERE st.school_id = ?
                   AND (? IS NULL OR ay.academic_year_id = ?)
                 ORDER BY ay.start_date DESC, gl.grade_level_id, s.section_name, st.last_name, st.first_name
                """,
                (rs, rowNum) -> new V2StudentRecordResponse(
                        rs.getLong("student_id"),
                        rs.getLong("class_list_id"),
                        rs.getLong("class_id"),
                        rs.getString("student_lrn"),
                        rs.getString("first_name"),
                        rs.getString("middle_name"),
                        rs.getString("last_name"),
                        rs.getString("gender_name"),
                        rs.getInt("section_id"),
                        rs.getString("section_name"),
                        rs.getString("grade_level_name"),
                        rs.getInt("academic_year_id"),
                        rs.getString("year_name")
                ),
                schoolId,
                academicYearId,
                academicYearId
        );
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    public record ExistingStudent(long studentId, String schoolId) {
    }
}
