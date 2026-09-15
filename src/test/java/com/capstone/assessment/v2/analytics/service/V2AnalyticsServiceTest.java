package com.capstone.assessment.v2.analytics.service;

import com.capstone.assessment.v2.analytics.config.V2AnalyticsProperties;
import com.capstone.assessment.v2.analytics.dto.V2AssessmentTrendResponse;
import com.capstone.assessment.v2.analytics.dto.V2GradeMasteryResponse;
import com.capstone.assessment.v2.analytics.dto.V2LmsResponse;
import com.capstone.assessment.v2.analytics.dto.V2SchoolAnalyticsResponse;
import com.capstone.assessment.v2.analytics.dto.V2SyncActivityResponse;
import com.capstone.assessment.v2.analytics.dto.V2TestPartResultResponse;
import com.capstone.assessment.v2.analytics.model.V2AnalyticsTestContext;
import com.capstone.assessment.v2.analytics.repository.V2AnalyticsRepository;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V2AnalyticsServiceTest {

    @Test
    void teacherReadsOwnedAssessmentMasteryWithRuleStatus() {
        V2AnalyticsRepository repository = mock(V2AnalyticsRepository.class);
        V2AnalyticsService service = new V2AnalyticsService(repository, new V2AnalyticsProperties());
        V2AuthenticatedUser teacher = teacher(9001L, "school-1");
        when(repository.findTestContext(1006L)).thenReturn(Optional.of(context(9001L, "school-1")));
        when(repository.listLms(1006L)).thenReturn(List.of(new V2LmsResponse(
                55L,
                44L,
                "Chemical Reactions",
                new BigDecimal("28.00"),
                new BigDecimal("40.00"),
                new BigDecimal("70.00"),
                null,
                8,
                4
        )));

        List<V2LmsResponse> response = service.getLms(teacher, 1006L);

        assertEquals(1, response.size());
        assertEquals("Review", response.get(0).status());
        assertEquals(8, response.get(0).respondentCount());
    }

    @Test
    void teacherCannotReadAnotherTeachersAssessment() {
        V2AnalyticsRepository repository = mock(V2AnalyticsRepository.class);
        V2AnalyticsService service = new V2AnalyticsService(repository, new V2AnalyticsProperties());
        when(repository.findTestContext(1006L)).thenReturn(Optional.of(context(9002L, "school-1")));

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> service.getLms(teacher(9001L, "school-1"), 1006L)
        );

        assertEquals("ASSESSMENT_ACCESS_DENIED", exception.getCode());
        verify(repository, never()).listLms(1006L);
    }

    @Test
    void principalCanReadAssessmentInsideOwnSchool() {
        V2AnalyticsRepository repository = mock(V2AnalyticsRepository.class);
        V2AnalyticsService service = new V2AnalyticsService(repository, new V2AnalyticsProperties());
        V2AuthenticatedUser principal = new V2AuthenticatedUser(
                8001L,
                "school-1",
                "principal@example.com",
                "principal",
                "active",
                "session-1"
        );
        when(repository.findTestContext(1006L)).thenReturn(Optional.of(context(9001L, "school-1")));
        when(repository.listLms(1006L)).thenReturn(List.of());

        assertEquals(List.of(), service.getLms(principal, 1006L));
    }

    @Test
    void partResultUsesPerformanceStatusAndRejectsWrongPart() {
        V2AnalyticsRepository repository = mock(V2AnalyticsRepository.class);
        V2AnalyticsService service = new V2AnalyticsService(repository, new V2AnalyticsProperties());
        V2AuthenticatedUser teacher = teacher(9001L, "school-1");
        when(repository.findTestContext(1006L)).thenReturn(Optional.of(context(9001L, "school-1")));
        when(repository.testPartBelongsToTest(1006L, 2001L)).thenReturn(true);
        when(repository.listTestPartResults(1006L, 2001L)).thenReturn(List.of(new V2TestPartResultResponse(
                3001L,
                "Dela Cruz, Juan",
                "100000000001",
                1006L,
                2001L,
                new BigDecimal("3.00"),
                new BigDecimal("5.00"),
                new BigDecimal("60.00"),
                null,
                Instant.parse("2026-08-24T04:00:00Z"),
                Instant.parse("2026-08-24T04:01:00Z")
        )));

        List<V2TestPartResultResponse> response = service.getTestPartResults(teacher, 1006L, 2001L);
        assertEquals("Developing", response.get(0).performance());

        when(repository.testPartBelongsToTest(1006L, 9999L)).thenReturn(false);
        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> service.getTestPartResults(teacher, 1006L, 9999L)
        );
        assertEquals("TEST_PART_NOT_FOUND", exception.getCode());
    }

    @Test
    void teacherSyncActivityIsAlwaysScopedToAuthenticatedTeacher() {
        V2AnalyticsRepository repository = mock(V2AnalyticsRepository.class);
        V2AnalyticsService service = new V2AnalyticsService(repository, new V2AnalyticsProperties());
        V2AuthenticatedUser teacher = teacher(9001L, "school-1");
        V2SyncActivityResponse activity = new V2SyncActivityResponse(
                77L,
                "4c66ff15-3c07-428c-939d-25213dcd4514",
                9001L,
                "Teacher One",
                1006L,
                "English Quiz 2",
                910002L,
                "Grade 7",
                "Rizal",
                "English",
                "success",
                8,
                0,
                0,
                Instant.parse("2026-08-24T04:00:00Z"),
                Instant.parse("2026-08-24T04:01:00Z"),
                Instant.parse("2026-08-24T04:01:00Z")
        );
        when(repository.listSyncActivity("school-1", null, null, 9001L, null, null))
                .thenReturn(List.of(activity));

        List<V2SyncActivityResponse> response = service.getSyncActivity(
                teacher, null, null, null, null, null
        );

        assertEquals(1, response.size());
        assertEquals(8, response.get(0).successfulResults());
        verify(repository).listSyncActivity("school-1", null, null, 9001L, null, null);
    }

    @Test
    void teacherCannotRequestAnotherTeachersSyncActivity() {
        V2AnalyticsRepository repository = mock(V2AnalyticsRepository.class);
        V2AnalyticsService service = new V2AnalyticsService(repository, new V2AnalyticsProperties());

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> service.getSyncActivity(teacher(9001L, "school-1"), null, null, 9002L, null, null)
        );

        assertEquals("SYNC_ACTIVITY_ACCESS_DENIED", exception.getCode());
        verify(repository, never()).listSyncActivity("school-1", null, null, 9002L, null, null);
    }

    @Test
    void principalOverviewReturnsVerifiedSchoolAggregates() {
        V2AnalyticsRepository repository = mock(V2AnalyticsRepository.class);
        V2AnalyticsService service = new V2AnalyticsService(repository, new V2AnalyticsProperties());
        V2AuthenticatedUser principal = principal(8001L, "school-1");
        V2LmsResponse lms = new V2LmsResponse(
                55L,
                44L,
                "Chemical Reactions",
                new BigDecimal("28.00"),
                new BigDecimal("40.00"),
                new BigDecimal("70.00"),
                null,
                8,
                4
        );
        when(repository.teacherBelongsToSchool(9001L, "school-1")).thenReturn(true);
        when(repository.countSchoolAssessments("school-1", 7L, 22L, 9001L, 1L, 910002L)).thenReturn(2);
        when(repository.listSchoolLms("school-1", 7L, 22L, 9001L, 1L, 910002L)).thenReturn(List.of(lms));
        when(repository.listGradeMastery("school-1", 7L, 22L, 9001L, 1L, 910002L)).thenReturn(List.of(
                new V2GradeMasteryResponse(7L, "Grade 7", new BigDecimal("28"), new BigDecimal("40"), new BigDecimal("70"), 8)
        ));
        when(repository.listAssessmentTrends("school-1", 7L, 22L, 9001L, 1L, 910002L)).thenReturn(List.of(
                new V2AssessmentTrendResponse(1006L, "English Quiz 2", java.time.LocalDate.of(2026, 8, 24), new BigDecimal("70"), 8)
        ));

        V2SchoolAnalyticsResponse response = service.getSchoolOverview(
                principal, 7L, 22L, 9001L, 1L, 910002L
        );

        assertEquals(2, response.totalAssessments());
        assertEquals("Review", response.lms().get(0).status());
        assertEquals(1, response.gradeLevels().size());
        assertEquals(1, response.trends().size());
    }

    @Test
    void teacherCannotReadPrincipalSchoolOverview() {
        V2AnalyticsRepository repository = mock(V2AnalyticsRepository.class);
        V2AnalyticsService service = new V2AnalyticsService(repository, new V2AnalyticsProperties());

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> service.getSchoolOverview(teacher(9001L, "school-1"), null, null, null, null, null)
        );

        assertEquals("PRINCIPAL_ACCESS_REQUIRED", exception.getCode());
    }

    private V2AuthenticatedUser teacher(long userId, String schoolId) {
        return new V2AuthenticatedUser(
                userId,
                schoolId,
                "teacher@example.com",
                "teacher",
                "active",
                "session-1"
        );
    }

    private V2AuthenticatedUser principal(long userId, String schoolId) {
        return new V2AuthenticatedUser(
                userId,
                schoolId,
                "principal@example.com",
                "principal",
                "active",
                "session-1"
        );
    }

    private V2AnalyticsTestContext context(long teacherUserId, String schoolId) {
        return new V2AnalyticsTestContext(1006L, teacherUserId, schoolId, "active");
    }
}
