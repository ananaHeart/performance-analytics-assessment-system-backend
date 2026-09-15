package com.capstone.assessment.v3.school.repository;

import com.capstone.assessment.v3.school.dto.V3ClassAssignmentResponse;
import com.capstone.assessment.v3.school.dto.V3ClassResponse;
import com.capstone.assessment.v3.school.dto.V3SchoolProfileResponse;
import com.capstone.assessment.v3.school.dto.V3SchoolProfileUpdateRequest;
import com.capstone.assessment.v3.school.dto.V3SchoolSetupReferenceDataResponse;
import com.capstone.assessment.v3.school.dto.V3StudentRosterEntryResponse;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.AssignmentContext;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.ClassContext;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.EnrollmentContext;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.StudentContext;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3SchoolSetupRepository {

    private static final String CLASS_SELECT = """
            SELECT class_row.class_id,
                   section_row.school_id,
                   class_row.academic_year_id,
                   academic_year.year_name AS academic_year_name,
                   section_row.section_id,
                   section_row.grade_level_id,
                   grade_level.grade_level_name,
                   section_row.section_name,
                   class_row.status,
                   COUNT(CASE WHEN membership.enrollment_status = 'enrolled' THEN 1 END)
                       AS enrolled_student_count
              FROM classes class_row
              JOIN academic_years academic_year
                ON academic_year.academic_year_id = class_row.academic_year_id
              JOIN sections section_row
                ON section_row.section_id = class_row.section_id
              JOIN grade_levels grade_level
                ON grade_level.grade_level_id = section_row.grade_level_id
              LEFT JOIN class_lists membership
                ON membership.class_id = class_row.class_id
            """;

    private static final String ASSIGNMENT_SELECT = """
            SELECT assignment.class_assignment_id,
                   section_row.school_id,
                   assignment.class_id,
                   assignment.user_id AS teacher_user_id,
                   CONCAT_WS(' ', teacher.first_name, NULLIF(teacher.middle_name, ''),
                             teacher.last_name, NULLIF(suffix.suffix_name, '')) AS teacher_name,
                   assignment.subject_id,
                   subject.subject_name,
                   class_row.academic_year_id,
                   academic_year.year_name AS academic_year_name,
                   section_row.grade_level_id,
                   grade_level.grade_level_name,
                   section_row.section_id,
                   section_row.section_name,
                   assignment.assignment_role,
                   assignment.status,
                   assignment.assigned_at,
                   assignment.ended_at,
                   assignment.status_reason
              FROM class_assignments assignment
              JOIN classes class_row ON class_row.class_id = assignment.class_id
              JOIN academic_years academic_year
                ON academic_year.academic_year_id = class_row.academic_year_id
              JOIN sections section_row ON section_row.section_id = class_row.section_id
              JOIN grade_levels grade_level
                ON grade_level.grade_level_id = section_row.grade_level_id
              JOIN users teacher ON teacher.user_id = assignment.user_id
              LEFT JOIN suffixes suffix ON suffix.suffix_id = teacher.suffix_id
              JOIN subjects subject ON subject.subject_id = assignment.subject_id
            """;

    private static final String STUDENT_SELECT = """
            SELECT student.student_id,
                   student.school_id,
                   student.address_id,
                   student.gender_id,
                   student.student_lrn,
                   student.first_name,
                   student.middle_name,
                   student.last_name,
                   student.suffix_id,
                   student.birth_date,
                   student.status
              FROM students student
            """;

    private static final String ENROLLMENT_SELECT = """
            SELECT membership.class_list_id,
                   membership.membership_uuid,
                   membership.class_id,
                   membership.student_id,
                   membership.academic_year_id,
                   membership.enrollment_status,
                   membership.enrollment_source,
                   membership.enrolled_at,
                   membership.ended_at,
                   membership.status_reason,
                   membership.status_changed_by_user_id
              FROM class_lists membership
              JOIN classes class_row ON class_row.class_id = membership.class_id
              JOIN sections section_row ON section_row.section_id = class_row.section_id
              JOIN students student ON student.student_id = membership.student_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public V3SchoolSetupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<V3SchoolProfileResponse> findSchoolProfile(String schoolId) {
        return jdbcTemplate.query("""
                SELECT school.school_id,
                       school.school_name,
                       school.contact_number,
                       school.email,
                       address.country_code,
                       address.region_code,
                       address.region_name,
                       address.province_code,
                       address.province_name,
                       address.city_municipality_code,
                       address.city_municipality_name,
                       address.barangay_code,
                       address.barangay_name,
                       address.address_line,
                       address.postal_code,
                       address.address_source
                  FROM school_profiles school
                  JOIN addresses address ON address.address_id = school.address_id
                 WHERE school.school_id = ?
                """, this::mapSchoolProfile, schoolId).stream().findFirst();
    }

    public int updateSchoolProfile(
            String schoolId,
            String schoolName,
            String contactNumber,
            String email
    ) {
        return jdbcTemplate.update("""
                UPDATE school_profiles
                   SET school_name = ?, contact_number = ?, email = ?
                 WHERE school_id = ?
                """, schoolName, contactNumber, email, schoolId);
    }

    public int updateSchoolAddress(String schoolId, V3SchoolProfileUpdateRequest.Address address) {
        return jdbcTemplate.update("""
                UPDATE addresses address
                JOIN school_profiles school ON school.address_id = address.address_id
                   SET address.country_code = 'PH',
                       address.region_code = ?,
                       address.region_name = ?,
                       address.province_code = ?,
                       address.province_name = ?,
                       address.city_municipality_code = ?,
                       address.city_municipality_name = ?,
                       address.barangay_code = ?,
                       address.barangay_name = ?,
                       address.address_line = ?,
                       address.postal_code = ?,
                       address.address_source = ?
                 WHERE school.school_id = ?
                """,
                address.regionCode(), address.regionName(),
                address.provinceCode(), address.provinceName(),
                address.cityMunicipalityCode(), address.cityMunicipalityName(),
                address.barangayCode(), address.barangayName(),
                address.addressLine(), address.postalCode(), address.addressSource(),
                schoolId
        );
    }

    public List<V3SchoolSetupReferenceDataResponse.AcademicYearOption> listAcademicYears(String schoolId) {
        return jdbcTemplate.query("""
                SELECT academic_year_id, curriculum_id, year_name, start_date, end_date, status
                  FROM academic_years
                 WHERE school_id = ?
                 ORDER BY start_date DESC, academic_year_id DESC
                """, (rs, rowNum) -> new V3SchoolSetupReferenceDataResponse.AcademicYearOption(
                rs.getInt("academic_year_id"),
                rs.getInt("curriculum_id"),
                rs.getString("year_name"),
                rs.getObject("start_date", java.time.LocalDate.class),
                rs.getObject("end_date", java.time.LocalDate.class),
                rs.getString("status")
        ), schoolId);
    }

    public List<V3SchoolSetupReferenceDataResponse.TermPeriodOption> listTermPeriods(String schoolId) {
        return jdbcTemplate.query("""
                SELECT term.term_period_id, term.academic_year_id, term.term_name, term.term_order,
                       term.start_at, term.end_at, term.status, term.activation_mode
                  FROM term_periods term
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = term.academic_year_id
                 WHERE academic_year.school_id = ?
                 ORDER BY academic_year_id DESC, term_order
                """, (rs, rowNum) -> new V3SchoolSetupReferenceDataResponse.TermPeriodOption(
                rs.getInt("term_period_id"),
                rs.getInt("academic_year_id"),
                rs.getString("term_name"),
                rs.getInt("term_order"),
                requiredInstant(rs, "start_at"),
                requiredInstant(rs, "end_at"),
                rs.getString("status"),
                rs.getString("activation_mode")
        ), schoolId);
    }

    public List<V3SchoolSetupReferenceDataResponse.GradeLevelOption> listGradeLevels() {
        return jdbcTemplate.query("""
                SELECT grade_level_id, grade_level_name
                  FROM grade_levels
                 ORDER BY grade_level_id
                """, (rs, rowNum) -> new V3SchoolSetupReferenceDataResponse.GradeLevelOption(
                rs.getInt("grade_level_id"), rs.getString("grade_level_name")
        ));
    }

    public List<V3SchoolSetupReferenceDataResponse.SubjectOption> listSubjects() {
        return jdbcTemplate.query("""
                SELECT subject_id, subject_code, subject_name
                  FROM subjects
                 ORDER BY subject_name
                """, (rs, rowNum) -> new V3SchoolSetupReferenceDataResponse.SubjectOption(
                rs.getInt("subject_id"), rs.getString("subject_code"), rs.getString("subject_name")
        ));
    }

    public List<V3SchoolSetupReferenceDataResponse.TeacherOption> listActiveTeachers(String schoolId) {
        return jdbcTemplate.query("""
                SELECT teacher.user_id,
                       CONCAT_WS(' ', teacher.first_name, NULLIF(teacher.middle_name, ''),
                                 teacher.last_name, NULLIF(suffix.suffix_name, '')) AS full_name,
                       teacher.email
                  FROM users teacher
                  JOIN roles role ON role.role_id = teacher.role_id AND role.role_name = 'teacher'
                  JOIN statuses status
                    ON status.status_id = teacher.status_id AND status.status_name = 'active'
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = teacher.suffix_id
                 WHERE teacher.school_id = ?
                   AND teacher.email_verified_at IS NOT NULL
                 ORDER BY teacher.last_name, teacher.first_name, teacher.user_id
                """, (rs, rowNum) -> new V3SchoolSetupReferenceDataResponse.TeacherOption(
                rs.getLong("user_id"), rs.getString("full_name"), rs.getString("email")
        ), schoolId);
    }

    public List<V3SchoolSetupReferenceDataResponse.GenderOption> listGenders() {
        return jdbcTemplate.query("""
                SELECT gender_id, gender_name
                  FROM genders
                 ORDER BY gender_id
                """, (rs, rowNum) -> new V3SchoolSetupReferenceDataResponse.GenderOption(
                rs.getInt("gender_id"), rs.getString("gender_name")
        ));
    }

    public List<V3SchoolSetupReferenceDataResponse.SuffixOption> listActiveSuffixes() {
        return jdbcTemplate.query("""
                SELECT suffix_id, suffix_name
                  FROM suffixes
                 WHERE is_active = 1
                 ORDER BY display_order, suffix_id
                """, (rs, rowNum) -> new V3SchoolSetupReferenceDataResponse.SuffixOption(
                rs.getInt("suffix_id"), rs.getString("suffix_name")
        ));
    }

    public boolean genderExists(int genderId) {
        return exists("SELECT 1 FROM genders WHERE gender_id = ?", genderId);
    }

    public boolean activeSuffixExists(int suffixId) {
        return exists("SELECT 1 FROM suffixes WHERE suffix_id = ? AND is_active = 1", suffixId);
    }

    public boolean academicYearExists(String schoolId, int academicYearId) {
        return exists("""
                SELECT 1
                  FROM academic_years
                 WHERE school_id = ? AND academic_year_id = ?
                """, schoolId, academicYearId);
    }

    public boolean gradeLevelExists(int gradeLevelId) {
        return exists("SELECT 1 FROM grade_levels WHERE grade_level_id = ?", gradeLevelId);
    }

    public boolean subjectExists(int subjectId) {
        return exists("SELECT 1 FROM subjects WHERE subject_id = ?", subjectId);
    }

    public boolean activeTeacherExists(String schoolId, long teacherUserId) {
        return exists("""
                SELECT 1
                  FROM users teacher
                  JOIN roles role ON role.role_id = teacher.role_id
                  JOIN statuses status ON status.status_id = teacher.status_id
                 WHERE teacher.user_id = ?
                   AND teacher.school_id = ?
                   AND role.role_name = 'teacher'
                   AND status.status_name = 'active'
                   AND teacher.email_verified_at IS NOT NULL
                """, teacherUserId, schoolId);
    }

    public Optional<Integer> findSectionId(String schoolId, int gradeLevelId, String sectionName) {
        return jdbcTemplate.query("""
                SELECT section_id
                  FROM sections
                 WHERE school_id = ?
                   AND grade_level_id = ?
                   AND section_name = ?
                """, (rs, rowNum) -> rs.getInt("section_id"),
                schoolId, gradeLevelId, sectionName).stream().findFirst();
    }

    public int insertSection(String schoolId, int gradeLevelId, String sectionName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sections (school_id, grade_level_id, section_name)
                    VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, schoolId);
            statement.setInt(2, gradeLevelId);
            statement.setString(3, sectionName);
            return statement;
        }, keyHolder);
        return requiredGeneratedNumber(keyHolder).intValue();
    }

    public Optional<ClassContext> findClassByContext(
            String schoolId,
            int academicYearId,
            int gradeLevelId,
            String sectionName
    ) {
        return jdbcTemplate.query(CLASS_SELECT + """
                 WHERE section_row.school_id = ?
                   AND class_row.academic_year_id = ?
                   AND section_row.grade_level_id = ?
                   AND section_row.section_name = ?
                 GROUP BY class_row.class_id, section_row.school_id, class_row.academic_year_id,
                          academic_year.year_name, section_row.section_id,
                          section_row.grade_level_id, grade_level.grade_level_name,
                          section_row.section_name, class_row.status
                """, this::mapClassContext,
                schoolId, academicYearId, gradeLevelId, sectionName).stream().findFirst();
    }

    public Optional<ClassContext> findClass(String schoolId, long classId) {
        return jdbcTemplate.query(CLASS_SELECT + """
                 WHERE section_row.school_id = ? AND class_row.class_id = ?
                 GROUP BY class_row.class_id, section_row.school_id, class_row.academic_year_id,
                          academic_year.year_name, section_row.section_id,
                          section_row.grade_level_id, grade_level.grade_level_name,
                          section_row.section_name, class_row.status
                """, this::mapClassContext, schoolId, classId).stream().findFirst();
    }

    public long insertClass(int academicYearId, int sectionId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO classes (academic_year_id, section_id, status)
                    VALUES (?, ?, 'active')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, academicYearId);
            statement.setInt(2, sectionId);
            return statement;
        }, keyHolder);
        return requiredGeneratedNumber(keyHolder).longValue();
    }

    public List<V3ClassResponse> listClasses(
            String schoolId,
            Integer academicYearId,
            Integer gradeLevelId
    ) {
        StringBuilder sql = new StringBuilder(CLASS_SELECT).append(" WHERE section_row.school_id = ? ");
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(schoolId);
        if (academicYearId != null) {
            sql.append(" AND class_row.academic_year_id = ? ");
            parameters.add(academicYearId);
        }
        if (gradeLevelId != null) {
            sql.append(" AND section_row.grade_level_id = ? ");
            parameters.add(gradeLevelId);
        }
        sql.append("""
                 GROUP BY class_row.class_id, section_row.school_id, class_row.academic_year_id,
                          academic_year.year_name, section_row.section_id,
                          section_row.grade_level_id, grade_level.grade_level_name,
                          section_row.section_name, class_row.status
                 ORDER BY academic_year.start_date DESC, grade_level.grade_level_name,
                          section_row.section_name
                """);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toClassResponse(mapClassContext(rs, rowNum), false),
                parameters.toArray());
    }

    public Optional<AssignmentContext> findExactAssignment(
            String schoolId,
            long classId,
            long teacherUserId,
            int subjectId
    ) {
        return jdbcTemplate.query(ASSIGNMENT_SELECT + """
                 WHERE section_row.school_id = ?
                   AND assignment.class_id = ?
                   AND assignment.user_id = ?
                   AND assignment.subject_id = ?
                """, this::mapAssignmentContext,
                schoolId, classId, teacherUserId, subjectId).stream().findFirst();
    }

    public Optional<AssignmentContext> findAssignment(String schoolId, long classAssignmentId) {
        return jdbcTemplate.query(ASSIGNMENT_SELECT + """
                 WHERE section_row.school_id = ?
                   AND assignment.class_assignment_id = ?
                """, this::mapAssignmentContext, schoolId, classAssignmentId).stream().findFirst();
    }

    public Optional<AssignmentContext> lockAssignment(String schoolId, long classAssignmentId) {
        return jdbcTemplate.query(ASSIGNMENT_SELECT + """
                 WHERE section_row.school_id = ?
                   AND assignment.class_assignment_id = ?
                 FOR UPDATE
                """, this::mapAssignmentContext, schoolId, classAssignmentId).stream().findFirst();
    }

    public long insertAssignment(long classId, long teacherUserId, int subjectId, String assignmentRole) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO class_assignments
                        (class_id, user_id, subject_id, assignment_role, status)
                    VALUES (?, ?, ?, ?, 'active')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, classId);
            statement.setLong(2, teacherUserId);
            statement.setInt(3, subjectId);
            statement.setString(4, assignmentRole);
            return statement;
        }, keyHolder);
        return requiredGeneratedNumber(keyHolder).longValue();
    }

    public int reactivateAssignment(
            long classAssignmentId,
            long principalUserId,
            String assignmentRole,
            String reason,
            Instant now
    ) {
        return jdbcTemplate.update("""
                UPDATE class_assignments
                   SET assignment_role = ?, status = 'active', assigned_at = ?,
                       ended_at = NULL, status_changed_by_user_id = ?, status_reason = ?
                 WHERE class_assignment_id = ? AND status IN ('archived', 'completed')
                """, assignmentRole, Timestamp.from(now), principalUserId, reason, classAssignmentId);
    }

    public int archiveAssignment(
            long classAssignmentId,
            long principalUserId,
            String reason,
            Instant now
    ) {
        return jdbcTemplate.update("""
                UPDATE class_assignments
                   SET status = 'archived', ended_at = ?, status_changed_by_user_id = ?,
                       status_reason = ?
                 WHERE class_assignment_id = ? AND status = 'active'
                """, Timestamp.from(now), principalUserId, reason, classAssignmentId);
    }

    public List<V3ClassAssignmentResponse> listAssignments(String schoolId, Integer academicYearId) {
        String sql = ASSIGNMENT_SELECT + """
                 WHERE section_row.school_id = ?
                """ + (academicYearId == null ? "" : " AND class_row.academic_year_id = ? ") + """
                 ORDER BY academic_year.start_date DESC, grade_level.grade_level_name,
                          section_row.section_name, subject.subject_name, teacher.last_name,
                          teacher.first_name
                """;
        Object[] parameters = academicYearId == null
                ? new Object[]{schoolId}
                : new Object[]{schoolId, academicYearId};
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> toAssignmentResponse(mapAssignmentContext(rs, rowNum), false),
                parameters);
    }

    public boolean teacherOwnsActiveClass(String schoolId, long teacherUserId, long classId) {
        return exists("""
                SELECT 1
                  FROM class_assignments assignment
                  JOIN classes class_row ON class_row.class_id = assignment.class_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                 WHERE assignment.class_id = ?
                   AND assignment.user_id = ?
                   AND assignment.status = 'active'
                   AND class_row.status = 'active'
                   AND section_row.school_id = ?
                   AND teacher.school_id = ?
                """, classId, teacherUserId, schoolId, schoolId);
    }

    public Optional<StudentContext> lockStudentByLrn(String studentLrn) {
        return jdbcTemplate.query(STUDENT_SELECT + """
                 WHERE student.student_lrn = ?
                 FOR UPDATE
                """, this::mapStudentContext, studentLrn).stream().findFirst();
    }

    public Optional<StudentContext> lockStudent(String schoolId, long studentId) {
        return jdbcTemplate.query(STUDENT_SELECT + """
                 WHERE student.school_id = ? AND student.student_id = ?
                 FOR UPDATE
                """, this::mapStudentContext, schoolId, studentId).stream().findFirst();
    }

    public Optional<EnrollmentContext> lockActiveEnrollment(
            String schoolId,
            long studentId,
            int academicYearId
    ) {
        return jdbcTemplate.query(ENROLLMENT_SELECT + """
                 WHERE section_row.school_id = ?
                   AND student.school_id = ?
                   AND membership.student_id = ?
                   AND membership.academic_year_id = ?
                   AND membership.enrollment_status = 'enrolled'
                 FOR UPDATE
                """, this::mapEnrollmentContext,
                schoolId, schoolId, studentId, academicYearId).stream().findFirst();
    }

    public Optional<EnrollmentContext> lockMembership(
            String schoolId,
            long studentId,
            long classId
    ) {
        return jdbcTemplate.query(ENROLLMENT_SELECT + """
                 WHERE section_row.school_id = ?
                   AND student.school_id = ?
                   AND membership.student_id = ?
                   AND membership.class_id = ?
                 FOR UPDATE
                """, this::mapEnrollmentContext,
                schoolId, schoolId, studentId, classId).stream().findFirst();
    }

    public Optional<EnrollmentContext> lockMembership(String schoolId, long classListId) {
        return jdbcTemplate.query(ENROLLMENT_SELECT + """
                 WHERE section_row.school_id = ?
                   AND student.school_id = ?
                   AND membership.class_list_id = ?
                 FOR UPDATE
                """, this::mapEnrollmentContext,
                schoolId, schoolId, classListId).stream().findFirst();
    }

    public long insertBlankManualAddress() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> connection.prepareStatement("""
                INSERT INTO addresses (country_code, address_source)
                VALUES ('PH', 'manual')
                """, Statement.RETURN_GENERATED_KEYS), keyHolder);
        return requiredGeneratedNumber(keyHolder).longValue();
    }

    public long insertStudent(
            String schoolId,
            long addressId,
            int genderId,
            String studentLrn,
            String firstName,
            String middleName,
            String lastName,
            Integer suffixId,
            LocalDate birthDate
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO students
                        (school_id, address_id, gender_id, student_lrn, first_name,
                         middle_name, last_name, suffix_id, birth_date, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, schoolId);
            statement.setLong(2, addressId);
            statement.setInt(3, genderId);
            statement.setString(4, studentLrn);
            statement.setString(5, firstName);
            statement.setString(6, middleName);
            statement.setString(7, lastName);
            statement.setObject(8, suffixId);
            statement.setObject(9, birthDate);
            return statement;
        }, keyHolder);
        return requiredGeneratedNumber(keyHolder).longValue();
    }

    public int updateStudentProfile(
            long studentId,
            int genderId,
            String firstName,
            String middleName,
            String lastName,
            Integer suffixId,
            LocalDate birthDate
    ) {
        return jdbcTemplate.update("""
                UPDATE students
                   SET gender_id = ?, first_name = ?, middle_name = ?, last_name = ?,
                       suffix_id = ?, birth_date = ?
                 WHERE student_id = ?
                """, genderId, firstName, middleName, lastName, suffixId, birthDate, studentId);
    }

    public long insertManualMembership(
            String membershipUuid,
            long classId,
            long studentId,
            int academicYearId,
            long principalUserId,
            String reason
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO class_lists
                        (membership_uuid, class_id, student_id, academic_year_id,
                         enrollment_status, enrollment_source, status_reason,
                         status_changed_by_user_id)
                    VALUES (?, ?, ?, ?, 'enrolled', 'manual', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, membershipUuid);
            statement.setLong(2, classId);
            statement.setLong(3, studentId);
            statement.setInt(4, academicYearId);
            statement.setString(5, reason);
            statement.setLong(6, principalUserId);
            return statement;
        }, keyHolder);
        return requiredGeneratedNumber(keyHolder).longValue();
    }

    public int updateMembershipStatus(
            long classListId,
            String previousStatus,
            String newStatus,
            long principalUserId,
            String reason,
            Instant now
    ) {
        return jdbcTemplate.update("""
                UPDATE class_lists
                   SET enrollment_status = ?,
                       enrolled_at = CASE WHEN ? = 'enrolled' THEN ? ELSE enrolled_at END,
                       ended_at = CASE WHEN ? = 'enrolled' THEN NULL ELSE COALESCE(ended_at, ?) END,
                       status_reason = ?,
                       status_changed_by_user_id = ?
                 WHERE class_list_id = ? AND enrollment_status = ?
                """,
                newStatus,
                newStatus, Timestamp.from(now),
                newStatus, Timestamp.from(now),
                reason, principalUserId, classListId, previousStatus
        );
    }

    public List<V3StudentRosterEntryResponse> listRoster(
            String schoolId,
            long classId,
            String enrollmentStatus
    ) {
        String sql = """
                SELECT membership.class_list_id,
                       membership.membership_uuid,
                       student.student_id,
                       student.student_lrn,
                       student.first_name,
                       student.middle_name,
                       student.last_name,
                       suffix.suffix_name,
                       CONCAT_WS(' ', student.first_name, NULLIF(student.middle_name, ''),
                                 student.last_name, NULLIF(suffix.suffix_name, '')) AS full_name,
                       gender.gender_name,
                       student.birth_date,
                       student.status AS student_status,
                       membership.enrollment_status,
                       membership.enrollment_source,
                       membership.enrolled_at,
                       membership.ended_at,
                       membership.status_reason,
                       membership.status_changed_by_user_id,
                       membership.updated_at
                  FROM class_lists membership
                  JOIN classes class_row ON class_row.class_id = membership.class_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                  JOIN students student ON student.student_id = membership.student_id
                  JOIN genders gender ON gender.gender_id = student.gender_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = student.suffix_id
                 WHERE section_row.school_id = ?
                   AND student.school_id = ?
                   AND membership.class_id = ?
                """ + (enrollmentStatus == null ? "" : " AND membership.enrollment_status = ? ") + """
                 ORDER BY student.last_name, student.first_name, student.middle_name, student.student_id
                """;
        Object[] parameters = enrollmentStatus == null
                ? new Object[]{schoolId, schoolId, classId}
                : new Object[]{schoolId, schoolId, classId, enrollmentStatus};
        return jdbcTemplate.query(sql, this::mapRosterEntry, parameters);
    }

    public Optional<V3StudentRosterEntryResponse> findRosterEntry(String schoolId, long classListId) {
        return jdbcTemplate.query("""
                SELECT membership.class_list_id,
                       membership.membership_uuid,
                       student.student_id,
                       student.student_lrn,
                       student.first_name,
                       student.middle_name,
                       student.last_name,
                       suffix.suffix_name,
                       CONCAT_WS(' ', student.first_name, NULLIF(student.middle_name, ''),
                                 student.last_name, NULLIF(suffix.suffix_name, '')) AS full_name,
                       gender.gender_name,
                       student.birth_date,
                       student.status AS student_status,
                       membership.enrollment_status,
                       membership.enrollment_source,
                       membership.enrolled_at,
                       membership.ended_at,
                       membership.status_reason,
                       membership.status_changed_by_user_id,
                       membership.updated_at
                  FROM class_lists membership
                  JOIN classes class_row ON class_row.class_id = membership.class_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                  JOIN students student ON student.student_id = membership.student_id
                  JOIN genders gender ON gender.gender_id = student.gender_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = student.suffix_id
                 WHERE section_row.school_id = ?
                   AND student.school_id = ?
                   AND membership.class_list_id = ?
                """, this::mapRosterEntry, schoolId, schoolId, classListId).stream().findFirst();
    }

    private V3SchoolProfileResponse mapSchoolProfile(ResultSet rs, int rowNum) throws SQLException {
        return new V3SchoolProfileResponse(
                rs.getString("school_id"),
                rs.getString("school_name"),
                rs.getString("contact_number"),
                rs.getString("email"),
                new V3SchoolProfileResponse.Address(
                        rs.getString("country_code"),
                        rs.getString("region_code"),
                        rs.getString("region_name"),
                        rs.getString("province_code"),
                        rs.getString("province_name"),
                        rs.getString("city_municipality_code"),
                        rs.getString("city_municipality_name"),
                        rs.getString("barangay_code"),
                        rs.getString("barangay_name"),
                        rs.getString("address_line"),
                        rs.getString("postal_code"),
                        rs.getString("address_source")
                )
        );
    }

    private ClassContext mapClassContext(ResultSet rs, int rowNum) throws SQLException {
        return new ClassContext(
                rs.getLong("class_id"),
                rs.getString("school_id"),
                rs.getInt("academic_year_id"),
                rs.getString("academic_year_name"),
                rs.getInt("section_id"),
                rs.getInt("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getString("section_name"),
                rs.getString("status"),
                rs.getInt("enrolled_student_count")
        );
    }

    private AssignmentContext mapAssignmentContext(ResultSet rs, int rowNum) throws SQLException {
        return new AssignmentContext(
                rs.getLong("class_assignment_id"),
                rs.getString("school_id"),
                rs.getLong("class_id"),
                rs.getLong("teacher_user_id"),
                rs.getString("teacher_name"),
                rs.getInt("subject_id"),
                rs.getString("subject_name"),
                rs.getInt("academic_year_id"),
                rs.getString("academic_year_name"),
                rs.getInt("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getInt("section_id"),
                rs.getString("section_name"),
                rs.getString("assignment_role"),
                rs.getString("status"),
                requiredInstant(rs, "assigned_at"),
                nullableInstant(rs, "ended_at"),
                rs.getString("status_reason")
        );
    }

    private StudentContext mapStudentContext(ResultSet rs, int rowNum) throws SQLException {
        return new StudentContext(
                rs.getLong("student_id"),
                rs.getString("school_id"),
                rs.getLong("address_id"),
                rs.getInt("gender_id"),
                rs.getString("student_lrn"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                rs.getString("last_name"),
                rs.getObject("suffix_id", Integer.class),
                rs.getObject("birth_date", LocalDate.class),
                rs.getString("status")
        );
    }

    private EnrollmentContext mapEnrollmentContext(ResultSet rs, int rowNum) throws SQLException {
        return new EnrollmentContext(
                rs.getLong("class_list_id"),
                rs.getString("membership_uuid"),
                rs.getLong("class_id"),
                rs.getLong("student_id"),
                rs.getInt("academic_year_id"),
                rs.getString("enrollment_status"),
                rs.getString("enrollment_source"),
                requiredInstant(rs, "enrolled_at"),
                nullableInstant(rs, "ended_at"),
                rs.getString("status_reason"),
                rs.getObject("status_changed_by_user_id", Long.class)
        );
    }

    private V3StudentRosterEntryResponse mapRosterEntry(ResultSet rs, int rowNum) throws SQLException {
        return new V3StudentRosterEntryResponse(
                rs.getLong("class_list_id"),
                rs.getString("membership_uuid"),
                rs.getLong("student_id"),
                rs.getString("student_lrn"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                rs.getString("last_name"),
                rs.getString("suffix_name"),
                rs.getString("full_name"),
                rs.getString("gender_name"),
                rs.getObject("birth_date", java.time.LocalDate.class),
                rs.getString("student_status"),
                rs.getString("enrollment_status"),
                rs.getString("enrollment_source"),
                requiredInstant(rs, "enrolled_at"),
                nullableInstant(rs, "ended_at"),
                rs.getString("status_reason"),
                rs.getObject("status_changed_by_user_id", Long.class),
                requiredInstant(rs, "updated_at")
        );
    }

    public V3ClassResponse toClassResponse(ClassContext context, boolean created) {
        return new V3ClassResponse(
                context.classId(), context.academicYearId(), context.academicYearName(),
                context.sectionId(), context.gradeLevelId(), context.gradeLevelName(),
                context.sectionName(), context.status(), context.enrolledStudentCount(), created
        );
    }

    public V3ClassAssignmentResponse toAssignmentResponse(AssignmentContext context, boolean created) {
        return new V3ClassAssignmentResponse(
                context.classAssignmentId(), context.classId(), context.teacherUserId(),
                context.teacherName(), context.subjectId(), context.subjectName(),
                context.academicYearId(), context.academicYearName(), context.gradeLevelId(),
                context.gradeLevelName(), context.sectionId(), context.sectionName(),
                context.assignmentRole(), context.status(), context.assignedAt(), context.endedAt(),
                context.statusReason(), created
        );
    }

    private boolean exists(String sql, Object... parameters) {
        return !jdbcTemplate.query(sql, (rs, rowNum) -> Boolean.TRUE, parameters).isEmpty();
    }

    private Number requiredGeneratedNumber(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The V3 database did not return a generated identifier.");
        }
        return key;
    }

    private Instant requiredInstant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
