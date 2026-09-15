package com.capstone.assessment.v2.schoolsetup.repository;

import com.capstone.assessment.v2.schoolsetup.dto.V2AcademicYearOption;
import com.capstone.assessment.v2.schoolsetup.dto.V2AvailableClassResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2ClassAssignmentResponse;
import com.capstone.assessment.v2.schoolsetup.dto.V2GradeLevelOption;
import com.capstone.assessment.v2.schoolsetup.dto.V2SubjectOption;
import com.capstone.assessment.v2.schoolsetup.model.V2ClassContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile("v2")
@Repository
public class V2SchoolSetupRepository {

    private static final String ASSIGNMENT_SELECT = """
            SELECT ca.class_assignment_id,
                   c.class_id,
                   ay.academic_year_id,
                   ay.year_name,
                   gl.grade_level_id,
                   gl.grade_level_name,
                   s.section_id,
                   s.section_name,
                   u.user_id AS teacher_user_id,
                   CONCAT_WS(' ', u.first_name, NULLIF(u.middle_name, ''), u.last_name, NULLIF(u.suffix, '')) AS teacher_name,
                   sub.subject_id,
                   sub.subject_name,
                   ca.assignment_role,
                   ca.status,
                   ca.assigned_at
              FROM class_assignments ca
              JOIN classes c ON c.class_id = ca.class_id
              JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
              JOIN sections s ON s.section_id = c.section_id
              JOIN grade_levels gl ON gl.grade_level_id = s.grade_level_id
              JOIN users u ON u.user_id = ca.user_id
              JOIN subjects sub ON sub.subject_id = ca.subject_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public V2SchoolSetupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<V2AcademicYearOption> listAcademicYears() {
        return jdbcTemplate.query(
                "SELECT academic_year_id, year_name, status FROM academic_years ORDER BY start_date DESC",
                (rs, rowNum) -> new V2AcademicYearOption(
                        rs.getInt("academic_year_id"),
                        rs.getString("year_name"),
                        rs.getString("status")
                )
        );
    }

    public List<V2GradeLevelOption> listGradeLevels() {
        return jdbcTemplate.query(
                "SELECT grade_level_id, grade_level_name FROM grade_levels ORDER BY grade_level_id",
                (rs, rowNum) -> new V2GradeLevelOption(
                        rs.getInt("grade_level_id"),
                        rs.getString("grade_level_name")
                )
        );
    }

    public List<V2SubjectOption> listSubjects() {
        return jdbcTemplate.query(
                "SELECT subject_id, subject_code, subject_name FROM subjects ORDER BY subject_name",
                (rs, rowNum) -> new V2SubjectOption(
                        rs.getInt("subject_id"),
                        rs.getString("subject_code"),
                        rs.getString("subject_name")
                )
        );
    }

    public List<V2AvailableClassResponse> findAvailableClasses(
            String schoolId,
            Integer academicYearId,
            Integer gradeLevelId,
            Integer subjectId
    ) {
        return jdbcTemplate.query(
                """
                SELECT c.class_id,
                       ay.academic_year_id,
                       ay.year_name,
                       gl.grade_level_id,
                       gl.grade_level_name,
                       s.section_id,
                       s.section_name,
                       COUNT(DISTINCT cl.class_list_id) AS enrolled_students
                  FROM classes c
                  JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
                  JOIN sections s ON s.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = s.grade_level_id
                  JOIN class_lists cl ON cl.class_id = c.class_id
                  JOIN students st ON st.student_id = cl.student_id AND st.school_id = ?
                 WHERE c.academic_year_id = ?
                   AND gl.grade_level_id = ?
                   AND c.status = 'active'
                   AND NOT EXISTS (
                       SELECT 1
                         FROM class_assignments ca
                        WHERE ca.class_id = c.class_id
                          AND ca.subject_id = ?
                          AND ca.assignment_role = 'primary'
                          AND ca.status = 'active'
                   )
                 GROUP BY c.class_id, ay.academic_year_id, ay.year_name,
                          gl.grade_level_id, gl.grade_level_name, s.section_id, s.section_name
                 ORDER BY s.section_name
                """,
                (rs, rowNum) -> new V2AvailableClassResponse(
                        rs.getLong("class_id"),
                        rs.getInt("academic_year_id"),
                        rs.getString("year_name"),
                        rs.getInt("grade_level_id"),
                        rs.getString("grade_level_name"),
                        rs.getInt("section_id"),
                        rs.getString("section_name"),
                        rs.getInt("enrolled_students")
                ),
                schoolId,
                academicYearId,
                gradeLevelId,
                subjectId
        );
    }

    public Optional<V2ClassContext> findClassContext(long classId) {
        List<V2ClassContext> rows = jdbcTemplate.query(
                """
                SELECT c.class_id, ay.academic_year_id, ay.year_name,
                       gl.grade_level_id, gl.grade_level_name,
                       s.section_id, s.section_name, c.status AS class_status
                  FROM classes c
                  JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
                  JOIN sections s ON s.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = s.grade_level_id
                 WHERE c.class_id = ?
                """,
                (rs, rowNum) -> new V2ClassContext(
                        rs.getLong("class_id"),
                        rs.getInt("academic_year_id"),
                        rs.getString("year_name"),
                        rs.getInt("grade_level_id"),
                        rs.getString("grade_level_name"),
                        rs.getInt("section_id"),
                        rs.getString("section_name"),
                        rs.getString("class_status")
                ),
                classId
        );
        return rows.stream().findFirst();
    }

    public void lockClass(long classId) {
        jdbcTemplate.queryForObject(
                "SELECT class_id FROM classes WHERE class_id = ? FOR UPDATE",
                Long.class,
                classId
        );
    }

    public boolean activeTeacherExistsInSchool(long teacherUserId, String schoolId) {
        return exists(
                """
                SELECT COUNT(*)
                  FROM users u
                  JOIN roles r ON r.role_id = u.role_id
                  JOIN statuses s ON s.status_id = u.status_id
                 WHERE u.user_id = ?
                   AND u.school_id = ?
                   AND r.role_name = 'teacher'
                   AND s.status_name = 'active'
                """,
                teacherUserId,
                schoolId
        );
    }

    public boolean subjectExists(int subjectId) {
        return exists("SELECT COUNT(*) FROM subjects WHERE subject_id = ?", subjectId);
    }

    public boolean classHasEnrolledStudents(long classId, String schoolId) {
        return exists(
                """
                SELECT COUNT(*)
                  FROM class_lists cl
                  JOIN students st ON st.student_id = cl.student_id
                 WHERE cl.class_id = ? AND st.school_id = ?
                """,
                classId,
                schoolId
        );
    }

    public boolean activeAssignmentExists(long classId, long teacherUserId, int subjectId) {
        return exists(
                """
                SELECT COUNT(*) FROM class_assignments
                 WHERE class_id = ? AND user_id = ? AND subject_id = ? AND status = 'active'
                """,
                classId,
                teacherUserId,
                subjectId
        );
    }

    public boolean activeAssignmentExistsExcluding(
            long assignmentId,
            long classId,
            long teacherUserId,
            int subjectId
    ) {
        return exists(
                """
                SELECT COUNT(*) FROM class_assignments
                 WHERE class_assignment_id <> ?
                   AND class_id = ?
                   AND user_id = ?
                   AND subject_id = ?
                   AND status = 'active'
                """,
                assignmentId,
                classId,
                teacherUserId,
                subjectId
        );
    }

    public Optional<Long> findAssignmentId(long classId, long teacherUserId, int subjectId) {
        return jdbcTemplate.query(
                """
                SELECT class_assignment_id
                  FROM class_assignments
                 WHERE class_id = ?
                   AND user_id = ?
                   AND subject_id = ?
                """,
                (rs, rowNum) -> rs.getLong("class_assignment_id"),
                classId,
                teacherUserId,
                subjectId
        ).stream().findFirst();
    }

    public boolean activePrimaryAssignmentExists(long classId, int subjectId) {
        return exists(
                """
                SELECT COUNT(*) FROM class_assignments
                 WHERE class_id = ? AND subject_id = ?
                   AND assignment_role = 'primary' AND status = 'active'
                """,
                classId,
                subjectId
        );
    }

    public boolean activePrimaryAssignmentExistsExcluding(
            long assignmentId,
            long classId,
            int subjectId
    ) {
        return exists(
                """
                SELECT COUNT(*) FROM class_assignments
                 WHERE class_assignment_id <> ?
                   AND class_id = ?
                   AND subject_id = ?
                   AND assignment_role = 'primary'
                   AND status = 'active'
                """,
                assignmentId,
                classId,
                subjectId
        );
    }

    public boolean assignmentHasAssessments(long assignmentId) {
        return exists(
                "SELECT COUNT(*) FROM tests WHERE class_assignment_id = ?",
                assignmentId
        );
    }

    public long insertAssignment(
            long classId,
            long teacherUserId,
            int subjectId,
            String assignmentRole,
            Instant assignedAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO class_assignments (
                        class_id, user_id, subject_id, assignment_role, assigned_at, status
                    ) VALUES (?, ?, ?, ?, ?, 'active')
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, classId);
            statement.setLong(2, teacherUserId);
            statement.setInt(3, subjectId);
            statement.setString(4, assignmentRole);
            statement.setTimestamp(5, Timestamp.from(assignedAt));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Class assignment identifier was not generated.");
        }
        return key.longValue();
    }

    public int reactivateArchivedAssignment(
            long assignmentId,
            String schoolId,
            String assignmentRole,
            Instant assignedAt
    ) {
        return jdbcTemplate.update(
                """
                UPDATE class_assignments ca
                JOIN users u ON u.user_id = ca.user_id
                   SET ca.assignment_role = ?,
                       ca.assigned_at = ?,
                       ca.status = 'active',
                       ca.updated_at = CURRENT_TIMESTAMP
                 WHERE ca.class_assignment_id = ?
                   AND u.school_id = ?
                   AND ca.status = 'archived'
                """,
                assignmentRole,
                Timestamp.from(assignedAt),
                assignmentId,
                schoolId
        );
    }

    public int updateActiveAssignment(
            long assignmentId,
            String schoolId,
            long classId,
            long teacherUserId,
            int subjectId,
            String assignmentRole
    ) {
        return jdbcTemplate.update(
                """
                UPDATE class_assignments ca
                JOIN users current_teacher ON current_teacher.user_id = ca.user_id
                   SET ca.class_id = ?,
                       ca.user_id = ?,
                       ca.subject_id = ?,
                       ca.assignment_role = ?,
                       ca.updated_at = CURRENT_TIMESTAMP
                 WHERE ca.class_assignment_id = ?
                   AND current_teacher.school_id = ?
                   AND ca.status = 'active'
                """,
                classId,
                teacherUserId,
                subjectId,
                assignmentRole,
                assignmentId,
                schoolId
        );
    }

    public Optional<V2ClassAssignmentResponse> findAssignment(long assignmentId, String schoolId) {
        List<V2ClassAssignmentResponse> rows = jdbcTemplate.query(
                ASSIGNMENT_SELECT + " WHERE ca.class_assignment_id = ? AND u.school_id = ?",
                this::mapAssignment,
                assignmentId,
                schoolId
        );
        return rows.stream().findFirst();
    }

    public int archiveActiveAssignment(long assignmentId, String schoolId) {
        return jdbcTemplate.update(
                """
                UPDATE class_assignments ca
                JOIN users u ON u.user_id = ca.user_id
                   SET ca.status = 'archived',
                       ca.updated_at = CURRENT_TIMESTAMP
                 WHERE ca.class_assignment_id = ?
                   AND u.school_id = ?
                   AND ca.status = 'active'
                """,
                assignmentId,
                schoolId
        );
    }

    public List<V2ClassAssignmentResponse> listAssignments(String schoolId, Integer academicYearId) {
        String sql = ASSIGNMENT_SELECT + """
                 WHERE u.school_id = ?
                   AND (? IS NULL OR ay.academic_year_id = ?)
                 ORDER BY ay.start_date DESC, gl.grade_level_id, s.section_name, sub.subject_name, teacher_name
                """;
        return jdbcTemplate.query(sql, this::mapAssignment, schoolId, academicYearId, academicYearId);
    }

    private V2ClassAssignmentResponse mapAssignment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp assignedAt = rs.getTimestamp("assigned_at");
        return new V2ClassAssignmentResponse(
                rs.getLong("class_assignment_id"),
                rs.getLong("class_id"),
                rs.getInt("academic_year_id"),
                rs.getString("year_name"),
                rs.getInt("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getInt("section_id"),
                rs.getString("section_name"),
                rs.getLong("teacher_user_id"),
                rs.getString("teacher_name"),
                rs.getInt("subject_id"),
                rs.getString("subject_name"),
                rs.getString("assignment_role"),
                rs.getString("status"),
                assignedAt == null ? null : assignedAt.toInstant()
        );
    }

    private boolean exists(String sql, Object... arguments) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return count != null && count > 0;
    }
}
