package com.capstone.assessment.v2.teacherroster.repository;

import com.capstone.assessment.v2.importexport.dto.V2StudentRecordResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Profile("v2")
@Repository
public class V2TeacherRosterRepository {

    private final JdbcTemplate jdbcTemplate;

    public V2TeacherRosterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<V2StudentRecordResponse> listStudents(
            long teacherUserId,
            String schoolId,
            long classId
    ) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT
                       st.student_id,
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
                  FROM class_assignments ca
                  JOIN classes c ON c.class_id = ca.class_id
                  JOIN class_lists cl ON cl.class_id = c.class_id
                  JOIN students st ON st.student_id = cl.student_id
                  JOIN genders g ON g.gender_id = st.gender_id
                  JOIN sections s ON s.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = s.grade_level_id
                  JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
                 WHERE ca.user_id = ?
                   AND ca.class_id = ?
                   AND ca.status = 'active'
                   AND c.status = 'active'
                   AND st.school_id = ?
                 ORDER BY st.last_name, st.first_name, st.student_id
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
                teacherUserId,
                classId,
                schoolId
        );
    }
}
