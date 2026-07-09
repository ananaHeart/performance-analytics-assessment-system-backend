package com.capstone.assessment.assessmentsetup.repository;

import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingEntryDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class PartSkillMappingRepository {

    private final JdbcTemplate jdbcTemplate;

    public PartSkillMappingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TestPartMappingContext> findTestPartContext(Long testPartId) {
        String sql = """
                SELECT tp.test_part_id,
                       tp.competency_id AS parent_competency_id,
                       ct.competency_name AS parent_competency_name,
                       ct.parent_competency_id AS selected_parent_id,
                       tp.number_of_items
                FROM test_part tp
                JOIN competency_tags ct ON ct.competency_id = tp.competency_id
                WHERE tp.test_part_id = ?
                """;

        List<TestPartMappingContext> results = jdbcTemplate.query(sql, (rs, rowNum) -> new TestPartMappingContext(
                rs.getLong("test_part_id"),
                rs.getLong("parent_competency_id"),
                rs.getString("parent_competency_name"),
                getNullableLong(rs, "selected_parent_id"),
                rs.getInt("number_of_items")
        ), testPartId);

        return results.stream().findFirst();
    }

    public Optional<CompetencyContext> findCompetencyContext(Long competencyId) {
        String sql = """
                SELECT competency_id,
                       parent_competency_id,
                       competency_name
                FROM competency_tags
                WHERE competency_id = ?
                """;

        List<CompetencyContext> results = jdbcTemplate.query(sql, (rs, rowNum) -> new CompetencyContext(
                rs.getLong("competency_id"),
                getNullableLong(rs, "parent_competency_id"),
                rs.getString("competency_name")
        ), competencyId);

        return results.stream().findFirst();
    }

    public void deleteByTestPartId(Long testPartId) {
        String sql = """
                DELETE FROM part_skill_mapping
                WHERE test_part_id = ?
                """;

        jdbcTemplate.update(sql, testPartId);
    }

    public Long insertMapping(
            Long testPartId,
            Long competencyId,
            String mappingMode,
            Integer itemCount,
            Integer startItem,
            Integer endItem
    ) {
        String sql = """
                INSERT INTO part_skill_mapping (
                    test_part_id,
                    competency_id,
                    mapping_mode,
                    item_count,
                    start_item,
                    end_item
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setLong(1, testPartId);
            preparedStatement.setLong(2, competencyId);
            preparedStatement.setString(3, mappingMode);
            preparedStatement.setInt(4, itemCount);
            if (startItem == null) {
                preparedStatement.setObject(5, null);
            } else {
                preparedStatement.setInt(5, startItem);
            }
            if (endItem == null) {
                preparedStatement.setObject(6, null);
            } else {
                preparedStatement.setInt(6, endItem);
            }
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated mapping_id").longValue();
    }

    public void insertSkillItem(Long mappingId, Integer itemNumber) {
        String sql = """
                INSERT INTO skill_item (mapping_id, item_number)
                VALUES (?, ?)
                """;

        jdbcTemplate.update(sql, mappingId, itemNumber);
    }

    public List<PartSkillMappingEntryDto> findMappingsByTestPartId(Long testPartId) {
        String sql = """
                SELECT psm.mapping_id,
                       psm.test_part_id,
                       psm.competency_id,
                       ct.competency_name,
                       psm.mapping_mode,
                       psm.item_count,
                       psm.start_item,
                       psm.end_item
                FROM part_skill_mapping psm
                JOIN competency_tags ct ON ct.competency_id = psm.competency_id
                WHERE psm.test_part_id = ?
                ORDER BY psm.mapping_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long mappingId = rs.getLong("mapping_id");
            return new PartSkillMappingEntryDto(
                    mappingId,
                    rs.getLong("test_part_id"),
                    rs.getLong("competency_id"),
                    rs.getString("competency_name"),
                    rs.getString("mapping_mode"),
                    rs.getInt("item_count"),
                    getNullableInt(rs, "start_item"),
                    getNullableInt(rs, "end_item"),
                    findItemNumbersByMappingId(mappingId)
            );
        }, testPartId);
    }

    public List<Integer> findItemNumbersByMappingId(Long mappingId) {
        String sql = """
                SELECT item_number
                FROM skill_item
                WHERE mapping_id = ?
                ORDER BY item_number
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("item_number"), mappingId);
    }

    private Long getNullableLong(java.sql.ResultSet rs, String columnLabel) throws java.sql.SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private Integer getNullableInt(java.sql.ResultSet rs, String columnLabel) throws java.sql.SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? null : value;
    }

    public record TestPartMappingContext(
            Long testPartId,
            Long parentCompetencyId,
            String parentCompetencyName,
            Long selectedParentId,
            Integer numberOfItems
    ) {
    }

    public record CompetencyContext(
            Long competencyId,
            Long parentCompetencyId,
            String competencyName
    ) {
    }
}
