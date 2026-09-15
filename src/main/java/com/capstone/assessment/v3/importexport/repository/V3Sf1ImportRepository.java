package com.capstone.assessment.v3.importexport.repository;

import com.capstone.assessment.v3.importexport.dto.V3Sf1ImportResponse;
import com.capstone.assessment.v3.importexport.model.V3Sf1Models.EnrollmentContext;
import com.capstone.assessment.v3.importexport.model.V3Sf1Models.ImportHeader;
import com.capstone.assessment.v3.importexport.model.V3Sf1Models.ReferenceContext;
import com.capstone.assessment.v3.importexport.model.V3Sf1Models.StudentContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3Sf1ImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public V3Sf1ImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ReferenceContext> findReferenceContext(
            String schoolId,
            int academicYearId,
            int gradeLevelId,
            String sectionName
    ) {
        return jdbcTemplate.query("""
                SELECT academic_year.academic_year_id,
                       academic_year.year_name AS academic_year_name,
                       grade_level.grade_level_id,
                       grade_level.grade_level_name,
                       ? AS section_name,
                       (
                           SELECT class_row.class_id
                             FROM classes class_row
                             JOIN sections section_row ON section_row.section_id = class_row.section_id
                            WHERE section_row.school_id = ?
                              AND class_row.academic_year_id = academic_year.academic_year_id
                              AND section_row.grade_level_id = grade_level.grade_level_id
                              AND section_row.section_name = ?
                            LIMIT 1
                       ) AS existing_class_id
                  FROM academic_years academic_year
                  JOIN grade_levels grade_level ON grade_level.grade_level_id = ?
                 WHERE academic_year.academic_year_id = ?
                """, (rs, rowNum) -> new ReferenceContext(
                rs.getInt("academic_year_id"),
                rs.getString("academic_year_name"),
                rs.getInt("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getString("section_name"),
                nullableLong(rs, "existing_class_id")
        ), sectionName, schoolId, sectionName, gradeLevelId, academicYearId).stream().findFirst();
    }

    public int countPreviousCompletedImports(
            String schoolId,
            int academicYearId,
            String sourceFileHash,
            Long excludedImportId
    ) {
        String excludedClause = excludedImportId == null ? "" : " AND sf1_import_id <> ?";
        Object[] parameters = excludedImportId == null
                ? new Object[]{schoolId, academicYearId, sourceFileHash}
                : new Object[]{schoolId, academicYearId, sourceFileHash, excludedImportId};
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sf1_imports
                 WHERE school_id = ?
                   AND academic_year_id = ?
                   AND source_file_hash = ?
                   AND import_status IN ('completed', 'partial_success')
                """ + excludedClause, Integer.class, parameters);
        return count == null ? 0 : count;
    }

    public Optional<StudentContext> findStudentByLrn(String studentLrn) {
        return jdbcTemplate.query("""
                SELECT student.student_id,
                       student.school_id,
                       student.student_lrn,
                       student.first_name,
                       student.last_name,
                       gender.gender_name,
                       student.status
                  FROM students student
                  JOIN genders gender ON gender.gender_id = student.gender_id
                 WHERE student.student_lrn = ?
                """, (rs, rowNum) -> new StudentContext(
                rs.getLong("student_id"),
                rs.getString("school_id"),
                rs.getString("student_lrn"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("gender_name"),
                rs.getString("status")
        ), studentLrn).stream().findFirst();
    }

    public Optional<EnrollmentContext> findActiveEnrollment(long studentId, int academicYearId) {
        return jdbcTemplate.query("""
                SELECT class_list.class_list_id,
                       class_list.class_id,
                       section_row.section_name,
                       class_list.enrollment_status
                  FROM class_lists class_list
                  JOIN classes class_row ON class_row.class_id = class_list.class_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                 WHERE class_list.student_id = ?
                   AND class_list.academic_year_id = ?
                   AND class_list.enrollment_status = 'enrolled'
                """, this::mapEnrollment, studentId, academicYearId).stream().findFirst();
    }

    public Optional<EnrollmentContext> lockActiveEnrollment(long studentId, int academicYearId) {
        return jdbcTemplate.query("""
                SELECT class_list.class_list_id,
                       class_list.class_id,
                       section_row.section_name,
                       class_list.enrollment_status
                  FROM class_lists class_list
                  JOIN classes class_row ON class_row.class_id = class_list.class_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                 WHERE class_list.student_id = ?
                   AND class_list.academic_year_id = ?
                   AND class_list.enrollment_status = 'enrolled'
                 FOR UPDATE
                """, this::mapEnrollment, studentId, academicYearId).stream().findFirst();
    }

    public Optional<EnrollmentContext> findMembership(long studentId, long classId) {
        return jdbcTemplate.query("""
                SELECT class_list.class_list_id,
                       class_list.class_id,
                       section_row.section_name,
                       class_list.enrollment_status
                  FROM class_lists class_list
                  JOIN classes class_row ON class_row.class_id = class_list.class_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                 WHERE class_list.student_id = ?
                   AND class_list.class_id = ?
                """, this::mapEnrollment, studentId, classId).stream().findFirst();
    }

    public Optional<EnrollmentContext> lockMembership(long studentId, long classId) {
        return jdbcTemplate.query("""
                SELECT class_list.class_list_id,
                       class_list.class_id,
                       section_row.section_name,
                       class_list.enrollment_status
                  FROM class_lists class_list
                  JOIN classes class_row ON class_row.class_id = class_list.class_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                 WHERE class_list.student_id = ?
                   AND class_list.class_id = ?
                 FOR UPDATE
                """, this::mapEnrollment, studentId, classId).stream().findFirst();
    }

    public Optional<Integer> findGenderId(String genderName) {
        return jdbcTemplate.query("""
                SELECT gender_id
                  FROM genders
                 WHERE LOWER(gender_name) = LOWER(?)
                """, (rs, rowNum) -> rs.getInt("gender_id"), genderName).stream().findFirst();
    }

    public long insertBlankSf1Address() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> connection.prepareStatement("""
                INSERT INTO addresses (country_code, address_source)
                VALUES ('PH', 'sf1_import')
                """, Statement.RETURN_GENERATED_KEYS), keyHolder);
        return generatedId(keyHolder);
    }

    public long insertStudent(
            String schoolId,
            long addressId,
            int genderId,
            String studentLrn,
            String firstName,
            String lastName
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO students
                        (school_id, address_id, gender_id, student_lrn, first_name, last_name, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'active')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, schoolId);
            statement.setLong(2, addressId);
            statement.setInt(3, genderId);
            statement.setString(4, studentLrn);
            statement.setString(5, firstName);
            statement.setString(6, lastName);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public long insertClassMembership(
            String membershipUuid,
            long classId,
            long studentId,
            int academicYearId,
            long sf1ImportId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO class_lists
                        (membership_uuid, class_id, student_id, academic_year_id,
                         enrollment_status, enrollment_source, source_sf1_import_id)
                    VALUES (?, ?, ?, ?, 'enrolled', 'sf1', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, membershipUuid);
            statement.setLong(2, classId);
            statement.setLong(3, studentId);
            statement.setInt(4, academicYearId);
            statement.setLong(5, sf1ImportId);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public int reactivateMembership(
            long classListId,
            long sf1ImportId,
            long principalUserId,
            Instant now
    ) {
        return jdbcTemplate.update("""
                UPDATE class_lists
                   SET enrollment_status = 'enrolled',
                       enrollment_source = 'sf1',
                       source_sf1_import_id = ?,
                       enrolled_at = ?,
                       ended_at = NULL,
                       status_reason = 'Re-enrolled through an authorized SF1 import.',
                       status_changed_by_user_id = ?
                 WHERE class_list_id = ?
                   AND enrollment_status IN ('transferred', 'dropped', 'completed')
                """, sf1ImportId, Timestamp.from(now), principalUserId, classListId);
    }

    public Optional<ImportHeader> lockImportByUuid(String importUuid) {
        return jdbcTemplate.query("""
                SELECT sf1_import_id, import_uuid, school_id, academic_year_id,
                       target_class_id, uploaded_by_user_id, source_file_name, source_file_hash,
                       import_status, total_row_count, created_student_count,
                       updated_student_count, unchanged_student_count, conflict_row_count,
                       invalid_row_count, started_at, completed_at
                  FROM sf1_imports
                 WHERE import_uuid = ?
                 FOR UPDATE
                """, this::mapImportHeader, importUuid).stream().findFirst();
    }

    public long insertImport(
            String importUuid,
            String schoolId,
            int academicYearId,
            long targetClassId,
            long uploadedByUserId,
            String sourceFileName,
            String sourceFileHash,
            String detectedSchoolYear,
            String detectedGradeLevel,
            String detectedSectionName,
            int totalRows,
            Instant now
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sf1_imports
                        (import_uuid, school_id, academic_year_id, target_class_id,
                         uploaded_by_user_id, source_file_name, source_file_hash,
                         detected_school_year, detected_grade_level, detected_section_name,
                         import_status, total_row_count, started_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'processing', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, importUuid);
            statement.setString(2, schoolId);
            statement.setInt(3, academicYearId);
            statement.setLong(4, targetClassId);
            statement.setLong(5, uploadedByUserId);
            statement.setString(6, sourceFileName);
            statement.setString(7, sourceFileHash);
            statement.setString(8, detectedSchoolYear);
            statement.setString(9, detectedGradeLevel);
            statement.setString(10, detectedSectionName);
            statement.setInt(11, totalRows);
            statement.setTimestamp(12, Timestamp.from(now));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public long insertImportItem(
            long sf1ImportId,
            int rowNumber,
            String studentLrnSnapshot,
            String sourceRowHash,
            Long studentId,
            Long classListId,
            String outcomeStatus,
            String warningCode,
            String message,
            Instant processedAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sf1_import_items
                        (sf1_import_id, row_number, student_lrn_snapshot, source_row_hash,
                         student_id, class_list_id, outcome_status, warning_code,
                         outcome_message, processed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, sf1ImportId);
            statement.setInt(2, rowNumber);
            statement.setString(3, studentLrnSnapshot);
            statement.setString(4, sourceRowHash);
            setNullableLong(statement, 5, studentId);
            setNullableLong(statement, 6, classListId);
            statement.setString(7, outcomeStatus);
            statement.setString(8, warningCode);
            statement.setString(9, message);
            statement.setTimestamp(10, Timestamp.from(processedAt));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    public int completeImport(
            long sf1ImportId,
            String importStatus,
            int createdStudents,
            int updatedStudents,
            int unchangedStudents,
            int conflictRows,
            int invalidRows,
            Instant completedAt
    ) {
        return jdbcTemplate.update("""
                UPDATE sf1_imports
                   SET import_status = ?,
                       created_student_count = ?,
                       updated_student_count = ?,
                       unchanged_student_count = ?,
                       conflict_row_count = ?,
                       invalid_row_count = ?,
                       completed_at = ?
                 WHERE sf1_import_id = ? AND import_status = 'processing'
                """, importStatus, createdStudents, updatedStudents, unchangedStudents,
                conflictRows, invalidRows, Timestamp.from(completedAt), sf1ImportId);
    }

    public Optional<ImportHeader> findImport(long sf1ImportId) {
        return jdbcTemplate.query("""
                SELECT sf1_import_id, import_uuid, school_id, academic_year_id,
                       target_class_id, uploaded_by_user_id, source_file_name, source_file_hash,
                       import_status, total_row_count, created_student_count,
                       updated_student_count, unchanged_student_count, conflict_row_count,
                       invalid_row_count, started_at, completed_at
                  FROM sf1_imports
                 WHERE sf1_import_id = ?
                """, this::mapImportHeader, sf1ImportId).stream().findFirst();
    }

    public List<V3Sf1ImportResponse.Row> listImportRows(long sf1ImportId) {
        return jdbcTemplate.query("""
                SELECT sf1_import_item_id, row_number, student_lrn_snapshot,
                       student_id, class_list_id, outcome_status, warning_code,
                       outcome_message, processed_at
                  FROM sf1_import_items
                 WHERE sf1_import_id = ?
                 ORDER BY row_number
                """, (rs, rowNum) -> new V3Sf1ImportResponse.Row(
                rs.getLong("sf1_import_item_id"),
                rs.getInt("row_number"),
                rs.getString("student_lrn_snapshot"),
                nullableLong(rs, "student_id"),
                nullableLong(rs, "class_list_id"),
                rs.getString("outcome_status"),
                rs.getString("warning_code"),
                rs.getString("outcome_message"),
                requiredInstant(rs, "processed_at")
        ), sf1ImportId);
    }

    private EnrollmentContext mapEnrollment(ResultSet rs, int rowNum) throws SQLException {
        return new EnrollmentContext(
                rs.getLong("class_list_id"),
                rs.getLong("class_id"),
                rs.getString("section_name"),
                rs.getString("enrollment_status")
        );
    }

    private ImportHeader mapImportHeader(ResultSet rs, int rowNum) throws SQLException {
        return new ImportHeader(
                rs.getLong("sf1_import_id"),
                rs.getString("import_uuid"),
                rs.getString("school_id"),
                rs.getInt("academic_year_id"),
                rs.getLong("target_class_id"),
                rs.getLong("uploaded_by_user_id"),
                rs.getString("source_file_name"),
                rs.getString("source_file_hash"),
                rs.getString("import_status"),
                rs.getInt("total_row_count"),
                rs.getInt("created_student_count"),
                rs.getInt("updated_student_count"),
                rs.getInt("unchanged_student_count"),
                rs.getInt("conflict_row_count"),
                rs.getInt("invalid_row_count"),
                requiredInstant(rs, "started_at"),
                nullableInstant(rs, "completed_at")
        );
    }

    private long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return a generated identifier.");
        }
        return key.longValue();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant requiredInstant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }
}
