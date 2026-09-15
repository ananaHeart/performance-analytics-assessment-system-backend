package com.capstone.assessment.v3.answersheet.repository;

import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.AssignmentContext;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.PaperSize;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Question;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.StoredVersion;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Template;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.TemplateRegion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3AnswerSheetRepository {

    private static final String VALIDATED_TEMPLATE_CODE = com.capstone.assessment.v3.answersheet.service.V3DynamicLayout.CODE;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public V3AnswerSheetRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<PaperSize> findActivePaperSizes() {
        return jdbcTemplate.query(
                """
                SELECT paper_size_id, paper_size_code, paper_size_name,
                       width_points, height_points, is_active
                  FROM paper_sizes
                 WHERE is_active = TRUE
                 ORDER BY paper_size_id
                """,
                (rs, rowNumber) -> new PaperSize(
                        rs.getInt("paper_size_id"),
                        rs.getString("paper_size_code"),
                        rs.getString("paper_size_name"),
                        rs.getBigDecimal("width_points"),
                        rs.getBigDecimal("height_points"),
                        rs.getBoolean("is_active")
                )
        );
    }

    public Optional<PaperSize> findActivePaperSize(String paperSizeCode) {
        return first(jdbcTemplate.query(
                """
                SELECT paper_size_id, paper_size_code, paper_size_name,
                       width_points, height_points, is_active
                  FROM paper_sizes
                 WHERE paper_size_code = ?
                   AND is_active = TRUE
                """,
                (rs, rowNumber) -> new PaperSize(
                        rs.getInt("paper_size_id"),
                        rs.getString("paper_size_code"),
                        rs.getString("paper_size_name"),
                        rs.getBigDecimal("width_points"),
                        rs.getBigDecimal("height_points"),
                        rs.getBoolean("is_active")
                ),
                paperSizeCode
        ));
    }

    public Optional<AssignmentContext> findOwnedAssignment(
            long testAssignmentId,
            long teacherUserId,
            String schoolId
    ) {
        return findOwnedAssignment(testAssignmentId, teacherUserId, schoolId, false);
    }

    public Optional<AssignmentContext> lockOwnedAssignment(
            long testAssignmentId,
            long teacherUserId,
            String schoolId
    ) {
        return findOwnedAssignment(testAssignmentId, teacherUserId, schoolId, true);
    }

    private Optional<AssignmentContext> findOwnedAssignment(
            long testAssignmentId,
            long teacherUserId,
            String schoolId,
            boolean lock
    ) {
        String sql = """
                SELECT delivery.test_assignment_id, delivery.assignment_uuid,
                       assessment.test_id, assessment.test_uuid,
                       assessment.version_number, assessment.test_name,
                       assessment.status AS test_status, assessment.total_items,
                       delivery.assignment_status,
                       assignment.status AS class_assignment_status,
                       grade.grade_level_name, section.section_name,
                       subject.subject_name
                  FROM test_assignments delivery
                  JOIN tests assessment ON assessment.test_id = delivery.test_id
                  JOIN class_assignments assignment
                    ON assignment.class_assignment_id = delivery.class_assignment_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  JOIN classes cohort ON cohort.class_id = assignment.class_id
                  JOIN sections section ON section.section_id = cohort.section_id
                  JOIN grade_levels grade ON grade.grade_level_id = section.grade_level_id
                  JOIN subjects subject ON subject.subject_id = assignment.subject_id
                 WHERE delivery.test_assignment_id = ?
                   AND assignment.user_id = ?
                   AND teacher.school_id = ?
                   AND assessment.school_id = ?
                """ + (lock ? " FOR UPDATE" : "");

        return first(jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> new AssignmentContext(
                        rs.getLong("test_assignment_id"),
                        rs.getString("assignment_uuid"),
                        rs.getLong("test_id"),
                        rs.getString("test_uuid"),
                        rs.getInt("version_number"),
                        rs.getString("test_name"),
                        rs.getString("test_status"),
                        rs.getInt("total_items"),
                        rs.getString("assignment_status"),
                        rs.getString("class_assignment_status"),
                        rs.getString("grade_level_name"),
                        rs.getString("section_name"),
                        rs.getString("subject_name")
                ),
                testAssignmentId,
                teacherUserId,
                schoolId,
                schoolId
        ));
    }

    public List<Question> findQuestions(long testId) {
        return jdbcTemplate.query(
                """
                SELECT question.question_id, question.question_uuid,
                       part.test_part_id, part.part_order, part.number_of_items,
                       question.item_number,
                       ROW_NUMBER() OVER (
                           PARTITION BY part.test_id
                           ORDER BY part.part_order, question.item_number
                       ) AS global_item_number,
                       type.question_type_id, type.question_type_code,
                       COUNT(DISTINCT option_row.question_option_id) AS active_option_count,
                       GROUP_CONCAT(
                           DISTINCT option_row.option_key
                           ORDER BY option_row.option_order SEPARATOR ','
                       ) AS active_option_keys,
                       COUNT(DISTINCT answer_key.answer_key_id) AS answer_key_count,
                       COUNT(DISTINCT CASE
                           WHEN answer_key.answer_key_type = 'option'
                            AND correct_option.question_id = question.question_id
                           THEN answer_key.answer_key_id
                       END) AS valid_option_answer_key_count,
                       COUNT(DISTINCT mapping.part_skill_mapping_id) AS skill_mapping_count
                  FROM questions question
                  JOIN test_parts part ON part.test_part_id = question.test_part_id
                  JOIN question_types type
                    ON type.question_type_id = question.question_type_id
                  LEFT JOIN question_options option_row
                    ON option_row.question_id = question.question_id
                   AND option_row.is_active = TRUE
                  LEFT JOIN answer_keys answer_key
                    ON answer_key.question_id = question.question_id
                  LEFT JOIN question_options correct_option
                    ON correct_option.question_option_id = answer_key.correct_question_option_id
                  LEFT JOIN part_skill_mappings mapping
                    ON mapping.test_part_id = part.test_part_id
                   AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                 WHERE part.test_id = ?
                 GROUP BY question.question_id, question.question_uuid,
                          part.test_part_id, part.part_order, part.number_of_items,
                          question.item_number,
                          type.question_type_id, type.question_type_code
                 ORDER BY part.part_order, question.item_number
                """,
                (rs, rowNumber) -> new Question(
                        rs.getLong("question_id"),
                        rs.getString("question_uuid"),
                        rs.getLong("test_part_id"),
                        rs.getInt("part_order"),
                        rs.getInt("number_of_items"),
                        rs.getInt("item_number"),
                        rs.getInt("global_item_number"),
                        rs.getInt("question_type_id"),
                        rs.getString("question_type_code"),
                        rs.getInt("active_option_count"),
                        splitCsv(rs.getString("active_option_keys")),
                        rs.getInt("answer_key_count"),
                        rs.getInt("valid_option_answer_key_count"),
                        rs.getInt("skill_mapping_count")
                ),
                testId
        );
    }

    public Optional<Template> findValidatedTemplate(String paperSizeCode) {
        List<Template> templates = jdbcTemplate.query(
                """
                SELECT template.omr_template_id, template.template_code,
                       template.template_name, template.template_version,
                       template.paper_size_id, size.paper_size_code,
                       size.width_points, size.height_points,
                       template.page_orientation, template.minimum_item_count,
                       template.maximum_item_count, template.option_count,
                       template.qr_payload_version, template.minimum_scanner_version,
                       template.coordinate_origin, template.required_print_scale_percent,
                       template.geometry_hash
                  FROM omr_templates template
                  JOIN paper_sizes size ON size.paper_size_id = template.paper_size_id
                 WHERE template.template_code = ?
                   AND template.template_status = 'active'
                   AND size.is_active = TRUE
                   AND size.paper_size_code = ?
                """,
                (rs, rowNumber) -> new Template(
                            rs.getLong("omr_template_id"),
                            rs.getString("template_code"),
                            rs.getString("template_name"),
                            rs.getString("template_version"),
                            rs.getInt("paper_size_id"),
                            rs.getString("paper_size_code"),
                            rs.getBigDecimal("width_points"),
                            rs.getBigDecimal("height_points"),
                            rs.getString("page_orientation"),
                            nullableInteger(rs.getObject("minimum_item_count")),
                            nullableInteger(rs.getObject("maximum_item_count")),
                            nullableInteger(rs.getObject("option_count")),
                            rs.getInt("qr_payload_version"),
                            rs.getString("minimum_scanner_version"),
                            rs.getString("coordinate_origin"),
                            rs.getBigDecimal("required_print_scale_percent"),
                            rs.getString("geometry_hash"),
                            List.of()
                    ),
                VALIDATED_TEMPLATE_CODE,
                paperSizeCode
        );
        if (templates.isEmpty()) {
            return Optional.empty();
        }
        Template template = templates.get(0);
        return Optional.of(new Template(
                template.omrTemplateId(),
                template.code(),
                template.name(),
                template.version(),
                template.paperSizeId(),
                template.paperSizeCode(),
                template.pageWidthPoints(),
                template.pageHeightPoints(),
                template.orientation(),
                template.minimumItemCount(),
                template.maximumItemCount(),
                template.optionCount(),
                template.qrPayloadVersion(),
                template.minimumScannerVersion(),
                template.coordinateOrigin(),
                template.requiredPrintScalePercent(),
                template.geometryHash(),
                findTemplateRegions(template.omrTemplateId())
        ));
    }

    public int nextGenerationNumber(long testAssignmentId, int paperSizeId) {
        Integer value = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(generation_number), 0) + 1
                  FROM answer_sheet_versions
                 WHERE test_assignment_id = ?
                   AND paper_size_id = ?
                """,
                Integer.class,
                testAssignmentId,
                paperSizeId
        );
        return value == null ? 1 : value;
    }

    public long insertGeneratingVersion(
            String answerSheetUuid,
            long testAssignmentId,
            int paperSizeId,
            int generationNumber,
            int testVersionNumber,
            int totalQuestions,
            String manifestHash,
            long generatedByUserId
    ) {
        return insertGeneratingVersion(answerSheetUuid, testAssignmentId, paperSizeId, generationNumber,
                testVersionNumber, totalQuestions, manifestHash, generatedByUserId, 1, 1);
    }

    public long insertGeneratingVersion(String answerSheetUuid, long testAssignmentId, int paperSizeId,
            int generationNumber, int testVersionNumber, int totalQuestions, String manifestHash,
            long generatedByUserId, int totalPages, int manifestVersion) {
        String sql = """
                INSERT INTO answer_sheet_versions (
                    answer_sheet_uuid, test_assignment_id, paper_size_id,
                    generation_number, test_version_number, total_questions,
                    total_pages, manifest_version, manifest_hash,
                    generation_status, generated_by_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'generating', ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator statementCreator = connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, answerSheetUuid);
            statement.setLong(2, testAssignmentId);
            statement.setInt(3, paperSizeId);
            statement.setInt(4, generationNumber);
            statement.setInt(5, testVersionNumber);
            statement.setInt(6, totalQuestions);
            statement.setInt(7, totalPages);
            statement.setInt(8, manifestVersion);
            statement.setString(9, manifestHash);
            statement.setLong(10, generatedByUserId);
            return statement;
        };
        jdbcTemplate.update(statementCreator, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The generated answer-sheet version ID was not returned.");
        }
        return key.longValue();
    }

    public long insertPage(
            String pageUuid,
            long answerSheetVersionId,
            long omrTemplateId,
            String qrPayload,
            String qrPayloadHash,
            String pageGeometryHash
    ) {
        return insertPage(pageUuid, answerSheetVersionId, omrTemplateId, qrPayload, qrPayloadHash, pageGeometryHash, 1, 1);
    }

    public long insertPage(String pageUuid, long answerSheetVersionId, long omrTemplateId, String qrPayload,
            String qrPayloadHash, String pageGeometryHash, int pageNumber, int totalPages) {
        String sql = """
                INSERT INTO answer_sheet_pages (
                    page_uuid, answer_sheet_version_id, omr_template_id,
                    page_number, total_pages, qr_payload, qr_payload_hash,
                    page_geometry_hash, page_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ready')
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, pageUuid);
            statement.setLong(2, answerSheetVersionId);
            statement.setLong(3, omrTemplateId);
            statement.setInt(4, pageNumber);
            statement.setInt(5, totalPages);
            statement.setString(6, qrPayload);
            statement.setString(7, qrPayloadHash);
            statement.setString(8, pageGeometryHash);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The generated answer-sheet page ID was not returned.");
        }
        return key.longValue();
    }

    public void insertObjectiveRegion(
            String regionUuid,
            long answerSheetVersionId,
            long answerSheetPageId,
            TemplateRegion templateRegion,
            Question question
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO answer_sheet_regions (
                    region_uuid, answer_sheet_version_id, answer_sheet_page_id,
                    omr_template_region_id, question_id, test_part_id,
                    question_type_id, global_item_number, part_item_number,
                    region_sequence, region_type, response_region_size,
                    expected_response_count_snapshot, response_line_count,
                    geometry_snapshot, geometry_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1,
                          'objective_bubbles', 'none', NULL, NULL, ?, ?)
                """,
                regionUuid,
                answerSheetVersionId,
                answerSheetPageId,
                templateRegion.omrTemplateRegionId(),
                question.questionId(),
                question.testPartId(),
                question.questionTypeId(),
                question.globalItemNumber(),
                question.partItemNumber(),
                templateRegion.geometryJson(),
                templateRegion.geometryHash()
        );
    }

    public void markVersionReady(
            long answerSheetVersionId,
            String storageKey,
            String pdfContentHash,
            long pdfFileSizeBytes,
            Instant generatedAt
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE answer_sheet_versions
                   SET pdf_storage_key = ?,
                       pdf_content_hash = ?,
                       pdf_file_size_bytes = ?,
                       generation_status = 'ready',
                       generated_at = ?
                 WHERE answer_sheet_version_id = ?
                   AND generation_status = 'generating'
                """,
                storageKey,
                pdfContentHash,
                pdfFileSizeBytes,
                Timestamp.from(generatedAt),
                answerSheetVersionId
        );
        if (updated != 1) {
            throw new IllegalStateException("The answer-sheet version could not be marked ready.");
        }
    }

    public Optional<StoredVersion> findOwnedVersion(
            long answerSheetVersionId,
            long teacherUserId,
            String schoolId
    ) {
        return first(jdbcTemplate.query(
                """
                SELECT sheet.answer_sheet_version_id, sheet.answer_sheet_uuid,
                       sheet.test_assignment_id, delivery.assignment_uuid,
                       size.paper_size_code, sheet.generation_number,
                       sheet.test_version_number, sheet.total_questions,
                       sheet.total_pages, sheet.manifest_version,
                       sheet.manifest_hash, sheet.generation_status,
                       sheet.pdf_storage_key, sheet.pdf_content_hash,
                       sheet.pdf_file_size_bytes, sheet.generated_at
                  FROM answer_sheet_versions sheet
                  JOIN paper_sizes size ON size.paper_size_id = sheet.paper_size_id
                  JOIN test_assignments delivery
                    ON delivery.test_assignment_id = sheet.test_assignment_id
                  JOIN class_assignments assignment
                    ON assignment.class_assignment_id = delivery.class_assignment_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  JOIN tests assessment ON assessment.test_id = delivery.test_id
                 WHERE sheet.answer_sheet_version_id = ?
                   AND assignment.user_id = ?
                   AND teacher.school_id = ?
                   AND assessment.school_id = ?
                """,
                (rs, rowNumber) -> new StoredVersion(
                        rs.getLong("answer_sheet_version_id"),
                        rs.getString("answer_sheet_uuid"),
                        rs.getLong("test_assignment_id"),
                        rs.getString("assignment_uuid"),
                        rs.getString("paper_size_code"),
                        rs.getInt("generation_number"),
                        rs.getInt("test_version_number"),
                        rs.getInt("total_questions"),
                        rs.getInt("total_pages"),
                        rs.getInt("manifest_version"),
                        rs.getString("manifest_hash"),
                        rs.getString("generation_status"),
                        rs.getString("pdf_storage_key"),
                        rs.getString("pdf_content_hash"),
                        rs.getLong("pdf_file_size_bytes"),
                        toInstant(rs.getTimestamp("generated_at"))
                ),
                answerSheetVersionId,
                teacherUserId,
                schoolId,
                schoolId
        ));
    }

    private List<TemplateRegion> findTemplateRegions(long templateId) {
        return jdbcTemplate.query(
                """
                SELECT region.omr_template_region_id, region.region_code,
                       region.region_order, region.region_type,
                       region.question_type_id, type.question_type_code,
                       region.layout_variant, region.response_region_size,
                       region.x_points, region.y_points, region.width_points,
                       region.height_points, region.geometry_definition,
                       region.geometry_hash, region.is_required
                  FROM omr_template_regions region
                  LEFT JOIN question_types type
                    ON type.question_type_id = region.question_type_id
                 WHERE region.omr_template_id = ?
                 ORDER BY region.region_order
                """,
                (rs, rowNumber) -> {
                    String geometryJson = rs.getString("geometry_definition");
                    return new TemplateRegion(
                            rs.getLong("omr_template_region_id"),
                            rs.getString("region_code"),
                            rs.getInt("region_order"),
                            rs.getString("region_type"),
                            nullableInteger(rs.getObject("question_type_id")),
                            rs.getString("question_type_code"),
                            rs.getString("layout_variant"),
                            rs.getString("response_region_size"),
                            rs.getBigDecimal("x_points"),
                            rs.getBigDecimal("y_points"),
                            rs.getBigDecimal("width_points"),
                            rs.getBigDecimal("height_points"),
                            parseJson(geometryJson),
                            geometryJson,
                            rs.getString("geometry_hash"),
                            rs.getBoolean("is_required")
                    );
                },
                templateId
        );
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored OMR template geometry is invalid JSON.", exception);
        }
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
