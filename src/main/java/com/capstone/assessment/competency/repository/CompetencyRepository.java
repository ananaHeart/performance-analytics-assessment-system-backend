package com.capstone.assessment.competency.repository;

import com.capstone.assessment.competency.dto.CompetencyTreeDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CompetencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompetencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CompetencyTreeDto> findParentCompetencies(Long gradeLevelId, Long subjectId) {
        String sql = """
                SELECT competency_id,
                       parent_competency_id,
                       grade_level_id,
                       subject_id,
                       competency_name
                FROM competency_tags
                WHERE parent_competency_id IS NULL
                  AND (? IS NULL OR grade_level_id = ?)
                  AND (? IS NULL OR subject_id = ?)
                ORDER BY competency_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CompetencyTreeDto(
                rs.getLong("competency_id"),
                null,
                rs.getLong("grade_level_id"),
                rs.getLong("subject_id"),
                rs.getString("competency_name"),
                List.of()
        ), gradeLevelId, gradeLevelId, subjectId, subjectId);
    }

    public List<CompetencyTreeDto> findBranchesByParentId(Long parentCompetencyId) {
        String sql = """
                SELECT competency_id,
                       parent_competency_id,
                       grade_level_id,
                       subject_id,
                       competency_name
                FROM competency_tags
                WHERE parent_competency_id = ?
                ORDER BY competency_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CompetencyTreeDto(
                rs.getLong("competency_id"),
                rs.getLong("parent_competency_id"),
                rs.getLong("grade_level_id"),
                rs.getLong("subject_id"),
                rs.getString("competency_name"),
                List.of()
        ), parentCompetencyId);
    }
}
