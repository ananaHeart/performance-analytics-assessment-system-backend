package com.capstone.assessment.v3.report.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.report.dto.V3StudentPerformanceProfileResponse;
import com.capstone.assessment.v3.report.repository.V3ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/** Exercises the real history/competency SQL through the service using synthetic,
 * in-memory data only. No Spring context or application database is opened. */
class V3StudentProfileIsolationTest {
    private JdbcTemplate jdbc;
    private V3ReportService service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:profile-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        for (String statement : FIXTURE.split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement);
        }
        V3ReportRepository repository = spy(new V3ReportRepository(jdbc));
        // Only display lookups and the rule definition are stubbed. Roster authorization
        // and all three result queries execute real SQL against the in-memory fixture.
        doReturn(Optional.of(new V3ReportRepository.StudentIdentity("TEST-LRN", "Test Learner")))
                .when(repository).findStudentIdentity(77L, "SCHOOL-001");
        doReturn(Optional.empty()).when(repository).findCurrentClassContext(77L, "SCHOOL-001");
        doReturn(Optional.empty()).when(repository).findSchool("SCHOOL-001");
        doReturn(Optional.empty()).when(repository).findUserFullName(anyLong());
        doReturn(Optional.of("""
                {"bands":[
                  {"minimum_percentage":0,"maximum_percentage":80,
                   "minimum_inclusive":true,"maximum_inclusive":false,
                   "status":"reteach","label":"Reteach",
                   "recommendation_template":"Review {competency_name}."},
                  {"minimum_percentage":80,"maximum_percentage":100,
                   "minimum_inclusive":true,"maximum_inclusive":true,
                   "status":"maintain","label":"Maintain",
                   "recommendation_template":"Maintain {competency_name}."}]}
                """)).when(repository).findActiveRuleDefinition(anyString(), eq("intervention"));
        service = new V3ReportService(repository, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void scienceTeacherSeesOnlyScienceAcrossTheStudentsVisibleEnrollments() {
        var profile = profile(11L, "teacher");

        assertEquals(2, profile.assessmentResults().size());
        assertTrue(profile.assessmentResults().stream().allMatch(row -> "Science".equals(row.subjectName())));
        assertEquals(List.of("Scientific inquiry"), competencyNames(profile));
        // Two Science assessments: (4 + 6) / (10 + 10), never the English score or another learner.
        assertEquals(0, new BigDecimal("50").compareTo(profile.competencyPerformance().get(0).masteryPercentage()));
        assertEquals(List.of("Scientific inquiry"), interventionNames(profile));
    }

    @Test
    void englishTeacherSeesOnlyEnglishForTheSameStudentAndClass() {
        var profile = profile(22L, "teacher");

        assertEquals(1, profile.assessmentResults().size());
        assertEquals("English", profile.assessmentResults().get(0).subjectName());
        assertEquals(List.of("Reading comprehension"), competencyNames(profile));
        assertEquals(0, new BigDecimal("10").compareTo(profile.competencyPerformance().get(0).masteryPercentage()));
        assertEquals(List.of("Reading comprehension"), interventionNames(profile));
    }

    @Test
    void teacherWithNoResultsInTheirOwnAssignmentDoesNotFallBackToOtherTeachersData() {
        var profile = profile(33L, "teacher");

        assertEquals("empty", profile.dataStatus());
        assertTrue(profile.assessmentResults().isEmpty());
        assertTrue(profile.competencyPerformance().isEmpty());
        assertTrue(profile.interventions().isEmpty());
    }

    @Test
    void principalRetainsBothSubjectsWithinTheAuthorizedStudentScope() {
        var profile = profile(10L, "principal");

        assertEquals(3, profile.assessmentResults().size());
        assertEquals(List.of("Reading comprehension", "Scientific inquiry"), competencyNames(profile));
        assertEquals(List.of("Reading comprehension", "Scientific inquiry"), interventionNames(profile));
    }

    @Test
    void teacherAssignedToBothSubjectsCanSeeBothWithoutUsingAnAccountMajorAsAFilter() {
        jdbc.update("UPDATE class_assignments SET user_id = 11 WHERE class_assignment_id = 202");

        var profile = profile(11L, "teacher");

        assertEquals(3, profile.assessmentResults().size());
        assertEquals(List.of("Reading comprehension", "Scientific inquiry"), competencyNames(profile));
    }

    @Test
    void teachersSharingTheSameClassSubjectAndCompetencyStillSeeOnlyTheirOwnRecords() {
        jdbc.update("UPDATE class_assignments SET subject_id = 1 WHERE class_assignment_id = 202");
        jdbc.update("UPDATE tests SET test_name = 'Other teacher Science' WHERE test_id = 102");
        jdbc.update("UPDATE part_skill_mappings SET skill_id = 301 WHERE test_part_id = 602");

        var firstTeacher = profile(11L, "teacher");
        var secondTeacher = profile(22L, "teacher");
        var principal = profile(10L, "principal");

        assertEquals(List.of(101L, 103L), firstTeacher.assessmentResults().stream()
                .map(row -> row.testId()).sorted().toList());
        assertEquals(List.of(102L), secondTeacher.assessmentResults().stream().map(row -> row.testId()).toList());
        assertTrue(secondTeacher.assessmentResults().stream().allMatch(row -> "Science".equals(row.subjectName())));
        assertEquals(List.of("Scientific inquiry"), competencyNames(firstTeacher));
        assertEquals(List.of("Scientific inquiry"), competencyNames(secondTeacher));
        assertEquals(0, new BigDecimal("50").compareTo(firstTeacher.competencyPerformance().get(0).masteryPercentage()));
        assertEquals(0, new BigDecimal("10").compareTo(secondTeacher.competencyPerformance().get(0).masteryPercentage()));
        assertEquals(List.of("Scientific inquiry"), interventionNames(firstTeacher));
        assertEquals(List.of("Scientific inquiry"), interventionNames(secondTeacher));
        assertEquals(3, principal.assessmentResults().size());
    }

    @Test
    void teacherWithoutAnyAssignmentCannotReadTheStudentEvenWithinTheSameSchool() {
        var exception = assertThrows(V3AuthException.class, () -> profile(44L, "teacher"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("STUDENT_NOT_FOUND", exception.getCode());
    }

    @Test
    void principalCannotReadAStudentInAnotherSchool() {
        var exception = assertThrows(V3AuthException.class, () -> service.getStudentPerformanceProfile(
                new V3AuthenticatedUser(10L, "SCHOOL-002", "principal@example.invalid",
                        "principal", "active", "other-school-session"), 77L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("STUDENT_NOT_FOUND", exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"student", "admin", "staff"})
    void otherRolesCannotGainPrincipalReportAccess(String role) {
        var exception = assertThrows(V3AuthException.class, () -> profile(11L, role));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("ACTIVE_SCHOOL_ACCOUNT_REQUIRED", exception.getCode());
    }

    @Test
    void unauthenticatedRequestCannotReadTheStudentReport() {
        var exception = assertThrows(V3AuthException.class,
                () -> service.getStudentPerformanceProfile(null, 77L));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void inactivePrincipalCannotReadTheStudentReport() {
        var exception = assertThrows(V3AuthException.class, () -> service.getStudentPerformanceProfile(
                new V3AuthenticatedUser(10L, "SCHOOL-001", "principal@example.invalid",
                        "principal", "inactive", "inactive-session"), 77L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    private V3StudentPerformanceProfileResponse profile(long userId, String role) {
        return service.getStudentPerformanceProfile(new V3AuthenticatedUser(
                userId, "SCHOOL-001", "test@example.invalid", role, "active", "test-session-" + userId), 77L);
    }

    private List<String> competencyNames(V3StudentPerformanceProfileResponse profile) {
        return profile.competencyPerformance().stream().map(row -> row.skillName()).sorted().toList();
    }

    private List<String> interventionNames(V3StudentPerformanceProfileResponse profile) {
        return profile.interventions().stream().map(row -> row.skillName()).sorted().toList();
    }

    private static final String FIXTURE = """
            CREATE TABLE students (student_id BIGINT PRIMARY KEY, school_id VARCHAR(30));
            CREATE TABLE class_lists (class_list_id BIGINT PRIMARY KEY, student_id BIGINT, class_id BIGINT);
            CREATE TABLE classes (class_id BIGINT PRIMARY KEY, academic_year_id INT, section_id INT);
            CREATE TABLE academic_years (academic_year_id INT PRIMARY KEY, school_id VARCHAR(30));
            CREATE TABLE sections (section_id INT PRIMARY KEY, school_id VARCHAR(30));
            CREATE TABLE class_assignments (class_assignment_id BIGINT PRIMARY KEY, class_id BIGINT, user_id BIGINT, subject_id INT);
            CREATE TABLE subjects (subject_id INT PRIMARY KEY, subject_name VARCHAR(100));
            CREATE TABLE tests (test_id BIGINT PRIMARY KEY, test_name VARCHAR(100), term_period_id INT);
            CREATE TABLE term_periods (term_period_id INT PRIMARY KEY, term_name VARCHAR(50));
            CREATE TABLE test_assignments (test_assignment_id BIGINT PRIMARY KEY, test_id BIGINT, class_assignment_id BIGINT,
                open_at TIMESTAMP, close_at TIMESTAMP);
            CREATE TABLE test_results (test_result_id BIGINT PRIMARY KEY, test_assignment_id BIGINT, class_list_id BIGINT,
                result_status VARCHAR(30), total_score DECIMAL(10,2), max_score DECIMAL(10,2), percentage_snapshot DECIMAL(10,2),
                performance_status VARCHAR(30), finalized_at TIMESTAMP, verification_completed_at TIMESTAMP,
                submitted_at TIMESTAMP, attempt_number INT);
            CREATE TABLE test_parts (test_part_id BIGINT PRIMARY KEY, test_id BIGINT);
            CREATE TABLE part_skill_mappings (test_part_id BIGINT, skill_id BIGINT, start_item_number INT, end_item_number INT);
            CREATE TABLE skills (skill_id BIGINT PRIMARY KEY, competency_id BIGINT);
            CREATE TABLE competency_tags (competency_id BIGINT PRIMARY KEY, competency_name VARCHAR(100));
            CREATE TABLE questions (question_id BIGINT PRIMARY KEY, test_part_id BIGINT, item_number INT, maximum_points DECIMAL(10,2));
            CREATE TABLE student_answers (test_result_id BIGINT, question_id BIGINT, points_earned DECIMAL(10,2));

            INSERT INTO students VALUES (77,'SCHOOL-001'),(78,'SCHOOL-001');
            INSERT INTO academic_years VALUES (1,'SCHOOL-001');
            INSERT INTO sections VALUES (10,'SCHOOL-001');
            INSERT INTO classes VALUES (100,1,10),(200,1,10);
            INSERT INTO class_lists VALUES (501,77,100),(502,77,200),(503,78,100);
            INSERT INTO subjects VALUES (1,'Science'),(2,'English');
            INSERT INTO class_assignments VALUES (201,100,11,1),(202,100,22,2),(203,200,11,1),(204,100,33,1);
            INSERT INTO term_periods VALUES (1,'Term 1'),(2,'Term 2');
            INSERT INTO tests VALUES (101,'Science A',1),(102,'English A',1),(103,'Science B',2);
            INSERT INTO test_assignments (test_assignment_id,test_id,class_assignment_id) VALUES (401,101,201),(402,102,202),(403,103,203);
            INSERT INTO test_parts VALUES (601,101),(602,102),(603,103);
            INSERT INTO skills VALUES (301,701),(302,702);
            INSERT INTO competency_tags VALUES (701,'Scientific inquiry'),(702,'Reading comprehension');
            INSERT INTO part_skill_mappings VALUES (601,301,1,1),(602,302,1,1),(603,301,1,1);
            INSERT INTO questions VALUES (801,601,1,10),(802,602,1,10),(803,603,1,10);
            INSERT INTO test_results
                (test_result_id,test_assignment_id,class_list_id,result_status,total_score,max_score,
                 percentage_snapshot,performance_status,finalized_at,attempt_number)
            VALUES (901,401,501,'finalized',4,10,40,'reteach','2026-09-01 00:00:00',1),
                   (902,402,501,'finalized',1,10,10,'priority_intervention','2026-09-02 00:00:00',1),
                   (903,403,502,'finalized',6,10,60,'review','2026-09-03 00:00:00',1),
                   (904,401,503,'finalized',9,10,90,'maintain','2026-09-01 00:00:00',1);
            INSERT INTO student_answers VALUES (901,801,4),(902,802,1),(903,803,6),(904,801,9);
            """;
}
