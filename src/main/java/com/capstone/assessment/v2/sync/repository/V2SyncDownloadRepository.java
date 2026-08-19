package com.capstone.assessment.v2.sync.repository;

import com.capstone.assessment.v2.sync.dto.V2SyncAnswerKeyDto;
import com.capstone.assessment.v2.sync.dto.V2SyncClassAssignmentDto;
import com.capstone.assessment.v2.sync.dto.V2SyncClassDto;
import com.capstone.assessment.v2.sync.dto.V2SyncClassListDto;
import com.capstone.assessment.v2.sync.dto.V2SyncQuestionDto;
import com.capstone.assessment.v2.sync.dto.V2SyncQuestionMappingDto;
import com.capstone.assessment.v2.sync.dto.V2SyncSkillDto;
import com.capstone.assessment.v2.sync.dto.V2SyncStudentDto;
import com.capstone.assessment.v2.sync.dto.V2SyncTestDto;
import com.capstone.assessment.v2.sync.dto.V2SyncTestPartDto;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Profile("v2")
@Repository
public class V2SyncDownloadRepository {

    private final JdbcTemplate jdbcTemplate;

    public V2SyncDownloadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<V2SyncClassAssignmentDto> listActiveAssignments(long teacherUserId, String schoolId) {
        return jdbcTemplate.query(
                """
                SELECT ca.class_assignment_id,
                       ca.class_id,
                       c.academic_year_id,
                       ay.year_name,
                       gl.grade_level_id,
                       gl.grade_level_name,
                       s.section_id,
                       s.section_name,
                       sub.subject_id,
                       sub.subject_name,
                       ca.assignment_role,
                       ca.status AS assignment_status
                  FROM class_assignments ca
                  JOIN users u ON u.user_id = ca.user_id
                  JOIN classes c ON c.class_id = ca.class_id
                  JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
                  JOIN sections s ON s.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = s.grade_level_id
                  JOIN subjects sub ON sub.subject_id = ca.subject_id
                 WHERE ca.user_id = ?
                   AND u.school_id = ?
                   AND ca.status = 'active'
                   AND c.status = 'active'
                 ORDER BY ay.start_date DESC, gl.grade_level_id, s.section_name, sub.subject_name
                """,
                (rs, rowNum) -> new V2SyncClassAssignmentDto(
                        rs.getLong("class_assignment_id"),
                        rs.getLong("class_id"),
                        rs.getInt("academic_year_id"),
                        rs.getString("year_name"),
                        rs.getInt("grade_level_id"),
                        rs.getString("grade_level_name"),
                        rs.getInt("section_id"),
                        rs.getString("section_name"),
                        rs.getInt("subject_id"),
                        rs.getString("subject_name"),
                        rs.getString("assignment_role"),
                        rs.getString("assignment_status")
                ),
                teacherUserId,
                schoolId
        );
    }

    public List<V2SyncClassDto> listClasses(List<Long> classIds) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT c.class_id,
                       c.academic_year_id,
                       ay.year_name,
                       gl.grade_level_id,
                       gl.grade_level_name,
                       s.section_id,
                       s.section_name,
                       c.status
                  FROM classes c
                  JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
                  JOIN sections s ON s.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = s.grade_level_id
                 WHERE c.class_id IN (%s)
                 ORDER BY ay.start_date DESC, gl.grade_level_id, s.section_name
                """.formatted(placeholders(classIds.size())),
                (rs, rowNum) -> new V2SyncClassDto(
                        rs.getLong("class_id"),
                        rs.getInt("academic_year_id"),
                        rs.getString("year_name"),
                        rs.getInt("grade_level_id"),
                        rs.getString("grade_level_name"),
                        rs.getInt("section_id"),
                        rs.getString("section_name"),
                        rs.getString("status")
                ),
                classIds.toArray()
        );
    }

    public List<V2SyncClassListDto> listClassLists(List<Long> classIds, String schoolId) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        List<Object> params = paramsWithSchool(classIds, schoolId);
        return jdbcTemplate.query(
                """
                SELECT cl.class_list_id,
                       cl.class_id,
                       cl.student_id
                  FROM class_lists cl
                  JOIN students st ON st.student_id = cl.student_id
                 WHERE cl.class_id IN (%s)
                   AND st.school_id = ?
                   AND st.status = 'active'
                 ORDER BY cl.class_id, st.last_name, st.first_name, cl.class_list_id
                """.formatted(placeholders(classIds.size())),
                (rs, rowNum) -> new V2SyncClassListDto(
                        rs.getLong("class_list_id"),
                        rs.getLong("class_id"),
                        rs.getLong("student_id")
                ),
                params.toArray()
        );
    }

    public List<V2SyncStudentDto> listStudents(List<Long> classIds, String schoolId) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        List<Object> params = paramsWithSchool(classIds, schoolId);
        return jdbcTemplate.query(
                """
                SELECT DISTINCT st.student_id,
                       st.school_id,
                       st.student_lrn,
                       st.first_name,
                       st.middle_name,
                       st.last_name,
                       st.suffix,
                       st.status
                  FROM class_lists cl
                  JOIN students st ON st.student_id = cl.student_id
                 WHERE cl.class_id IN (%s)
                   AND st.school_id = ?
                   AND st.status = 'active'
                 ORDER BY st.last_name, st.first_name, st.student_id
                """.formatted(placeholders(classIds.size())),
                (rs, rowNum) -> new V2SyncStudentDto(
                        rs.getLong("student_id"),
                        rs.getString("school_id"),
                        rs.getString("student_lrn"),
                        rs.getString("first_name"),
                        rs.getString("middle_name"),
                        rs.getString("last_name"),
                        rs.getString("suffix"),
                        rs.getString("status")
                ),
                params.toArray()
        );
    }

    public List<V2SyncTestDto> listActiveTests(List<Long> classAssignmentIds) {
        if (classAssignmentIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT t.test_id,
                       t.class_assignment_id,
                       t.term_period_id,
                       tp.term_name,
                       t.test_name,
                       t.test_type,
                       t.test_date,
                       t.instructions,
                       t.total_items,
                       t.status
                  FROM tests t
                  JOIN term_periods tp ON tp.term_period_id = t.term_period_id
                 WHERE t.class_assignment_id IN (%s)
                   AND t.status = 'active'
                 ORDER BY t.test_date DESC, t.test_id DESC
                """.formatted(placeholders(classAssignmentIds.size())),
                (rs, rowNum) -> new V2SyncTestDto(
                        rs.getLong("test_id"),
                        rs.getLong("class_assignment_id"),
                        rs.getInt("term_period_id"),
                        rs.getString("term_name"),
                        rs.getString("test_name"),
                        rs.getString("test_type"),
                        toLocalDate(rs.getDate("test_date")),
                        rs.getString("instructions"),
                        rs.getInt("total_items"),
                        rs.getString("status")
                ),
                classAssignmentIds.toArray()
        );
    }

    public List<V2SyncTestPartDto> listTestParts(List<Long> testIds) {
        if (testIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT test_part_id,
                       test_id,
                       part_order,
                       part_name,
                       part_type,
                       number_of_items,
                       points_per_item
                  FROM test_parts
                 WHERE test_id IN (%s)
                 ORDER BY test_id, part_order
                """.formatted(placeholders(testIds.size())),
                (rs, rowNum) -> new V2SyncTestPartDto(
                        rs.getLong("test_part_id"),
                        rs.getLong("test_id"),
                        rs.getInt("part_order"),
                        rs.getString("part_name"),
                        rs.getString("part_type"),
                        rs.getInt("number_of_items"),
                        rs.getBigDecimal("points_per_item")
                ),
                testIds.toArray()
        );
    }

    public List<V2SyncQuestionDto> listQuestions(List<Long> testPartIds) {
        if (testPartIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT q.question_id,
                       q.test_part_id,
                       q.item_number,
                       q.question_text,
                       q.option_a,
                       q.option_b,
                       q.option_c,
                       q.option_d,
                       q.option_e
                  FROM questions q
                  JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                 WHERE q.test_part_id IN (%s)
                 ORDER BY tp.test_id, tp.part_order, q.item_number
                """.formatted(placeholders(testPartIds.size())),
                (rs, rowNum) -> new V2SyncQuestionDto(
                        rs.getLong("question_id"),
                        rs.getLong("test_part_id"),
                        rs.getInt("item_number"),
                        rs.getString("question_text"),
                        rs.getString("option_a"),
                        rs.getString("option_b"),
                        rs.getString("option_c"),
                        rs.getString("option_d"),
                        rs.getString("option_e")
                ),
                testPartIds.toArray()
        );
    }

    public List<V2SyncAnswerKeyDto> listAnswerKeys(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT question_id,
                       correct_option
                  FROM answer_keys
                 WHERE question_id IN (%s)
                 ORDER BY question_id
                """.formatted(placeholders(questionIds.size())),
                (rs, rowNum) -> new V2SyncAnswerKeyDto(
                        rs.getLong("question_id"),
                        rs.getString("correct_option")
                ),
                questionIds.toArray()
        );
    }

    public List<V2SyncQuestionMappingDto> listQuestionMappings(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT question_id,
                       skill_id
                  FROM mappings
                 WHERE question_id IN (%s)
                 ORDER BY question_id, skill_id
                """.formatted(placeholders(questionIds.size())),
                (rs, rowNum) -> new V2SyncQuestionMappingDto(
                        rs.getLong("question_id"),
                        rs.getLong("skill_id")
                ),
                questionIds.toArray()
        );
    }

    public List<V2SyncSkillDto> listSkills(List<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT sk.skill_id,
                       ct.competency_id,
                       ct.competency_name,
                       rt.root_tag_id,
                       rt.root_tag_name,
                       sk.term_period_id,
                       sk.grade_level_id,
                       sk.subject_id
                  FROM skills sk
                  JOIN competency_tags ct ON ct.competency_id = sk.competency_id
                  JOIN root_tags rt ON rt.root_tag_id = ct.root_tag_id
                 WHERE sk.skill_id IN (%s)
                 ORDER BY rt.root_tag_name, ct.competency_name, sk.skill_id
                """.formatted(placeholders(skillIds.size())),
                (rs, rowNum) -> new V2SyncSkillDto(
                        rs.getLong("skill_id"),
                        rs.getLong("competency_id"),
                        rs.getString("competency_name"),
                        rs.getInt("root_tag_id"),
                        rs.getString("root_tag_name"),
                        rs.getInt("term_period_id"),
                        rs.getInt("grade_level_id"),
                        rs.getInt("subject_id")
                ),
                skillIds.toArray()
        );
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private List<Object> paramsWithSchool(List<Long> ids, String schoolId) {
        List<Object> params = new ArrayList<>(ids);
        params.add(schoolId);
        return params;
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
