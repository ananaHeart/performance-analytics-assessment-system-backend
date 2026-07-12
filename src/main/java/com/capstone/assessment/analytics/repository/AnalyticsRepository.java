package com.capstone.assessment.analytics.repository;

import com.capstone.assessment.analytics.dto.AssessmentTrendDto;
import com.capstone.assessment.analytics.dto.InterventionStudentDto;
import com.capstone.assessment.analytics.dto.ItemAnalysisDto;
import com.capstone.assessment.analytics.dto.LmsAffectedStudentsDto;
import com.capstone.assessment.analytics.dto.LmsDto;
import com.capstone.assessment.analytics.dto.SchoolLmsDto;
import com.capstone.assessment.analytics.dto.StudentSkillMasteryDto;
import com.capstone.assessment.analytics.dto.SyncActivityDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class AnalyticsRepository {

    private static final double MASTERY_THRESHOLD = 75.0;

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ItemAnalysisDto> getItemAnalysis(Long testId) {
        String sql = """
                SELECT tp.test_part_id AS test_part_id,
                       tir.item_number AS item_number,
                       ct.competency_name AS competency_name,
                       SUM(CASE WHEN tir.is_correct = 1 THEN 1 ELSE 0 END) AS correct_responses,
                       COUNT(tir.item_result_id) AS total_responses,
                       ROUND(SUM(CASE WHEN tir.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(tir.item_result_id), 2) AS difficulty
                FROM test_result tr
                JOIN test_item_result tir ON tir.test_result_id = tr.test_result_id
                JOIN test_part tp
                  ON tp.test_part_id = tir.test_part_id
                 AND tp.test_id = tr.test_id
                LEFT JOIN competency_tags ct ON ct.competency_id = tp.competency_id
                WHERE tr.test_id = ?
                GROUP BY tp.test_part_id, tir.item_number, ct.competency_name
                ORDER BY tp.test_part_id, tir.item_number
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double difficulty = rs.getDouble("difficulty");
            return new ItemAnalysisDto(
                    rs.getLong("test_part_id"),
                    rs.getInt("item_number"),
                    rs.getString("competency_name"),
                    rs.getInt("correct_responses"),
                    rs.getInt("total_responses"),
                    difficulty,
                    difficulty
            );
        }, testId);
    }

    public List<LmsDto> getLmsByTest(Long testId) {
        if (hasBranchMappingsByTest(testId)) {
            return getBranchLmsByTest(testId);
        }

        String sql = """
                SELECT ct.competency_id,
                       ct.competency_name,
                       ROUND(SUM(CASE WHEN tir.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS mastery_rate
                FROM test_result tr
                JOIN test_item_result tir ON tir.test_result_id = tr.test_result_id
                JOIN test_part tp
                  ON tp.test_part_id = tir.test_part_id
                 AND tp.test_id = tr.test_id
                JOIN competency_tags ct ON tp.competency_id = ct.competency_id
                WHERE tr.test_id = ?
                GROUP BY ct.competency_id, ct.competency_name
                ORDER BY mastery_rate ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double masteryRate = rs.getDouble("mastery_rate");
            return new LmsDto(
                    rs.getLong("competency_id"),
                    rs.getString("competency_name"),
                    masteryRate,
                    getInterventionLabel(masteryRate)
            );
        }, testId);
    }

    private List<LmsDto> getBranchLmsByTest(Long testId) {
        String sql = """
                SELECT ct.competency_id,
                       ct.competency_name,
                       ROUND(SUM(CASE WHEN tir.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS mastery_rate
                FROM test_result tr
                JOIN test_item_result tir ON tir.test_result_id = tr.test_result_id
                JOIN part_skill_mapping psm
                  ON psm.test_part_id = tir.test_part_id
                LEFT JOIN (
                    SELECT DISTINCT mapping_id, item_number
                    FROM skill_item
                ) si
                  ON si.mapping_id = psm.mapping_id
                 AND si.item_number = tir.item_number
                JOIN competency_tags ct ON ct.competency_id = psm.competency_id
                WHERE tr.test_id = ?
                  AND (
                        (psm.mapping_mode = 'RANGE' AND tir.item_number BETWEEN psm.start_item AND psm.end_item)
                        OR
                        (psm.mapping_mode = 'CUSTOM' AND si.mapping_id IS NOT NULL)
                      )
                GROUP BY ct.competency_id, ct.competency_name
                ORDER BY mastery_rate ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double masteryRate = rs.getDouble("mastery_rate");
            return new LmsDto(
                    rs.getLong("competency_id"),
                    rs.getString("competency_name"),
                    masteryRate,
                    getInterventionLabel(masteryRate)
            );
        }, testId);
    }

    public List<LmsAffectedStudentsDto> getLmsAffectedStudents(Long testId) {
        return getLmsByTest(testId).stream()
                .map(lms -> {
                    List<InterventionStudentDto> affectedStudents = getStudentsForIntervention(testId, lms.competencyId());
                    return new LmsAffectedStudentsDto(
                            lms.competencyId(),
                            lms.competencyName(),
                            lms.masteryRate(),
                            affectedStudents
                    );
                })
                .filter(lms -> !lms.affectedStudents().isEmpty())
                .toList();
    }

    public List<InterventionStudentDto> getStudentsForIntervention(Long testId, Long competencyId) {
        if (hasBranchMappingForCompetency(testId, competencyId)) {
            return getStudentsForBranchIntervention(testId, competencyId);
        }

        String sql = """
                SELECT s.student_id,
                       CONCAT(s.first_name, ' ', s.last_name) AS student_name,
                       SUM(CASE WHEN tir.is_correct = 1 THEN 1 ELSE 0 END) AS score,
                       COUNT(*) AS total_items,
                       ROUND(SUM(CASE WHEN tir.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS percentage,
                       SUM(CASE WHEN tir.is_correct = 0 THEN 1 ELSE 0 END) AS wrong_items
                FROM test_result tr
                JOIN test_item_result tir ON tir.test_result_id = tr.test_result_id
                JOIN student s ON tr.student_id = s.student_id
                JOIN test_part tp
                  ON tp.test_part_id = tir.test_part_id
                  AND tp.test_id = tr.test_id
                WHERE tr.test_id = ?
                  AND tp.competency_id = ?
                GROUP BY s.student_id, s.first_name, s.last_name
                HAVING wrong_items > 0
                ORDER BY percentage ASC, s.student_id ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new InterventionStudentDto(
                rs.getLong("student_id"),
                rs.getString("student_name"),
                rs.getInt("score"),
                rs.getInt("total_items"),
                rs.getDouble("percentage")
        ), testId, competencyId);
    }

    private List<InterventionStudentDto> getStudentsForBranchIntervention(Long testId, Long competencyId) {
        String sql = """
                SELECT s.student_id,
                       CONCAT(s.first_name, ' ', s.last_name) AS student_name,
                       SUM(CASE WHEN tir.is_correct = 1 THEN 1 ELSE 0 END) AS score,
                       COUNT(*) AS total_items,
                       ROUND(SUM(CASE WHEN tir.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS percentage
                FROM test_result tr
                JOIN test_item_result tir ON tir.test_result_id = tr.test_result_id
                JOIN student s ON tr.student_id = s.student_id
                JOIN part_skill_mapping psm
                  ON psm.test_part_id = tir.test_part_id
                 AND psm.competency_id = ?
                LEFT JOIN (
                    SELECT DISTINCT mapping_id, item_number
                    FROM skill_item
                ) si
                  ON si.mapping_id = psm.mapping_id
                 AND si.item_number = tir.item_number
                WHERE tr.test_id = ?
                  AND (
                        (psm.mapping_mode = 'RANGE' AND tir.item_number BETWEEN psm.start_item AND psm.end_item)
                        OR
                        (psm.mapping_mode = 'CUSTOM' AND si.mapping_id IS NOT NULL)
                      )
                GROUP BY s.student_id, s.first_name, s.last_name
                HAVING percentage < ?
                ORDER BY percentage ASC, s.student_id ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new InterventionStudentDto(
                rs.getLong("student_id"),
                rs.getString("student_name"),
                rs.getInt("score"),
                rs.getInt("total_items"),
                rs.getDouble("percentage")
        ), competencyId, testId, MASTERY_THRESHOLD);
    }

    public List<SchoolLmsDto> getSchoolWideLms(Long gradeLevelId, Long sectionId, Long subjectId, Long teacherId) {
        String sql = """
                SELECT ct.competency_id,
                       ct.competency_name,
                       ROUND(SUM(CASE WHEN tir.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS mastery_rate
                FROM test_result tr
                JOIN test_item_result tir ON tir.test_result_id = tr.test_result_id
                JOIN `test` t ON tr.test_id = t.test_id
                JOIN `class` c ON t.class_id = c.class_id
                JOIN section sec ON c.section_id = sec.section_id
                JOIN grade_level gl ON sec.grade_level_id = gl.grade_level_id
                JOIN test_part tp
                  ON tp.test_part_id = tir.test_part_id
                 AND tp.test_id = tr.test_id
                JOIN competency_tags ct ON tp.competency_id = ct.competency_id
                WHERE (? IS NULL OR gl.grade_level_id = ?)
                  AND (? IS NULL OR c.section_id = ?)
                  AND (? IS NULL OR c.subject_id = ?)
                  AND (? IS NULL OR c.user_id = ?)
                GROUP BY ct.competency_id, ct.competency_name
                ORDER BY mastery_rate ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new SchoolLmsDto(
                rs.getLong("competency_id"),
                rs.getString("competency_name"),
                rs.getDouble("mastery_rate")
        ), gradeLevelId, gradeLevelId, sectionId, sectionId, subjectId, subjectId, teacherId, teacherId);
    }

    public List<StudentSkillMasteryDto> getStudentSkillMastery(Long studentId, Long classId) {
        String sql = """
                SELECT mastery.student_id,
                       mastery.competency_id,
                       mastery.competency_name,
                       SUM(mastery.earned_points) AS earned_points,
                       SUM(mastery.total_points) AS total_points,
                       ROUND(SUM(mastery.earned_points) * 100.0 / SUM(mastery.total_points), 2) AS mastery_rate,
                       COUNT(DISTINCT mastery.test_id) AS assessments_count
                FROM (
                    SELECT tr.student_id,
                           tr.test_id,
                           psm.competency_id,
                           ct.competency_name,
                           CASE WHEN tir.is_correct = 1 THEN tp.points_per_item ELSE 0 END AS earned_points,
                           tp.points_per_item AS total_points
                    FROM test_result tr
                    JOIN `test` t ON t.test_id = tr.test_id
                    JOIN test_item_result tir ON tir.test_result_id = tr.test_result_id
                    JOIN test_part tp
                      ON tp.test_part_id = tir.test_part_id
                     AND tp.test_id = tr.test_id
                    JOIN part_skill_mapping psm
                      ON psm.test_part_id = tir.test_part_id
                    LEFT JOIN (
                        SELECT DISTINCT mapping_id, item_number
                        FROM skill_item
                    ) si
                      ON si.mapping_id = psm.mapping_id
                     AND si.item_number = tir.item_number
                    JOIN competency_tags ct ON ct.competency_id = psm.competency_id
                    WHERE tr.student_id = ?
                      AND t.class_id = ?
                      AND (
                            (psm.mapping_mode = 'RANGE' AND tir.item_number BETWEEN psm.start_item AND psm.end_item)
                            OR
                            (psm.mapping_mode = 'CUSTOM' AND si.mapping_id IS NOT NULL)
                          )

                    UNION ALL

                    SELECT tr.student_id,
                           tr.test_id,
                           tp.competency_id,
                           ct.competency_name,
                           CASE WHEN tir.is_correct = 1 THEN tp.points_per_item ELSE 0 END AS earned_points,
                           tp.points_per_item AS total_points
                    FROM test_result tr
                    JOIN `test` t ON t.test_id = tr.test_id
                    JOIN test_item_result tir ON tir.test_result_id = tr.test_result_id
                    JOIN test_part tp
                      ON tp.test_part_id = tir.test_part_id
                     AND tp.test_id = tr.test_id
                    JOIN competency_tags ct ON ct.competency_id = tp.competency_id
                    WHERE tr.student_id = ?
                      AND t.class_id = ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM part_skill_mapping psm
                          WHERE psm.test_part_id = tp.test_part_id
                      )
                ) mastery
                GROUP BY mastery.student_id, mastery.competency_id, mastery.competency_name
                ORDER BY mastery_rate ASC, mastery.competency_name ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double masteryRate = rs.getDouble("mastery_rate");
            return new StudentSkillMasteryDto(
                    rs.getLong("student_id"),
                    rs.getLong("competency_id"),
                    rs.getString("competency_name"),
                    rs.getInt("earned_points"),
                    rs.getInt("total_points"),
                    masteryRate,
                    getStudentMasteryStatus(masteryRate),
                    rs.getInt("assessments_count")
            );
        }, studentId, classId, studentId, classId);
    }

    public List<AssessmentTrendDto> getAssessmentTrends(Long classId) {
        String sql = """
                SELECT t.test_id,
                       t.test_name,
                       t.test_date,
                       AVG(tr.total_score) AS average_score
                FROM `test` t
                JOIN test_result tr ON t.test_id = tr.test_id
                WHERE t.class_id = ?
                GROUP BY t.test_id, t.test_name, t.test_date
                ORDER BY t.test_date ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new AssessmentTrendDto(
                rs.getLong("test_id"),
                rs.getString("test_name"),
                rs.getObject("test_date", Date.class).toLocalDate(),
                rs.getDouble("average_score")
        ), classId);
    }

    public List<SyncActivityDto> getSyncActivity(Long teacherId) {
        String sql = """
                SELECT t.test_id,
                       t.test_name,
                       sl.sync_timestamp,
                       sl.sync_status
                FROM sync_log sl
                JOIN `test` t ON sl.test_id = t.test_id
                WHERE sl.user_id = ?
                ORDER BY sl.sync_timestamp DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new SyncActivityDto(
                rs.getLong("test_id"),
                rs.getString("test_name"),
                rs.getObject("sync_timestamp", Timestamp.class).toLocalDateTime(),
                rs.getString("sync_status")
        ), teacherId);
    }

    private boolean hasBranchMappingsByTest(Long testId) {
        String sql = """
                SELECT COUNT(*)
                FROM part_skill_mapping psm
                JOIN test_part tp ON tp.test_part_id = psm.test_part_id
                WHERE tp.test_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, testId);
        return count != null && count > 0;
    }

    private boolean hasBranchMappingForCompetency(Long testId, Long competencyId) {
        String sql = """
                SELECT COUNT(*)
                FROM part_skill_mapping psm
                JOIN test_part tp ON tp.test_part_id = psm.test_part_id
                WHERE tp.test_id = ?
                  AND psm.competency_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, testId, competencyId);
        return count != null && count > 0;
    }

    private String getInterventionLabel(double masteryRate) {
        if (masteryRate >= 80.0) {
            return "Maintain";
        }

        if (masteryRate >= 60.0) {
            return "Review";
        }

        if (masteryRate >= 40.0) {
            return "Reteach";
        }

        return "Priority Intervention";
    }

    private String getStudentMasteryStatus(double masteryRate) {
        if (masteryRate >= 80.0) {
            return "Mastered";
        }

        if (masteryRate >= 60.0) {
            return "Developing";
        }

        return "Needs Support";
    }
}
