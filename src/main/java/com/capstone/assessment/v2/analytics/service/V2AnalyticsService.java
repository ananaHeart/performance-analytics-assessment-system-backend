package com.capstone.assessment.v2.analytics.service;

import com.capstone.assessment.v2.analytics.config.V2AnalyticsProperties;
import com.capstone.assessment.v2.analytics.dto.V2ItemAnalysisResponse;
import com.capstone.assessment.v2.analytics.dto.V2LmsResponse;
import com.capstone.assessment.v2.analytics.dto.V2SchoolAnalyticsResponse;
import com.capstone.assessment.v2.analytics.dto.V2SyncActivityResponse;
import com.capstone.assessment.v2.analytics.dto.V2TestPartResultResponse;
import com.capstone.assessment.v2.analytics.model.V2AnalyticsTestContext;
import com.capstone.assessment.v2.analytics.repository.V2AnalyticsRepository;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Profile("v2")
@Service
public class V2AnalyticsService {

    private final V2AnalyticsRepository analyticsRepository;
    private final V2AnalyticsProperties analyticsProperties;

    public V2AnalyticsService(
            V2AnalyticsRepository analyticsRepository,
            V2AnalyticsProperties analyticsProperties
    ) {
        this.analyticsRepository = analyticsRepository;
        this.analyticsProperties = analyticsProperties;
    }

    public List<V2LmsResponse> getLms(V2AuthenticatedUser principal, long testId) {
        authorize(principal, testId);
        return analyticsRepository.listLms(testId).stream()
                .map(row -> new V2LmsResponse(
                        row.skillId(),
                        row.competencyId(),
                        row.competencyName(),
                        row.earnedPoints(),
                        row.possiblePoints(),
                        row.masteryRate(),
                        interventionStatus(row.masteryRate()),
                        row.respondentCount(),
                        row.affectedStudents()
                ))
                .toList();
    }

    public List<V2ItemAnalysisResponse> getItemAnalysis(V2AuthenticatedUser principal, long testId) {
        authorize(principal, testId);
        return analyticsRepository.listItemAnalysis(testId).stream()
                .map(row -> new V2ItemAnalysisResponse(
                        row.questionId(),
                        row.testPartId(),
                        row.itemNumber(),
                        row.skillId(),
                        row.competencyId(),
                        row.competencyName(),
                        row.correctResponses(),
                        row.totalResponses(),
                        row.correctnessPercentage(),
                        difficultyLevel(row.correctnessPercentage())
                ))
                .toList();
    }

    public List<V2TestPartResultResponse> getTestPartResults(
            V2AuthenticatedUser principal,
            long testId,
            long testPartId
    ) {
        authorize(principal, testId);
        if (!analyticsRepository.testPartBelongsToTest(testId, testPartId)) {
            throw error("TEST_PART_NOT_FOUND", "Test part does not belong to this assessment.", HttpStatus.NOT_FOUND);
        }
        return analyticsRepository.listTestPartResults(testId, testPartId).stream()
                .map(row -> new V2TestPartResultResponse(
                        row.studentId(),
                        row.studentName(),
                        row.studentLrn(),
                        row.testId(),
                        row.testPartId(),
                        row.partScore(),
                        row.maxScore(),
                        row.percentage(),
                        performanceStatus(row.percentage()),
                        row.checkedAt(),
                        row.syncedAt()
                ))
                .toList();
    }

    public List<V2SyncActivityResponse> getSyncActivity(
            V2AuthenticatedUser principal,
            Long gradeLevelId,
            Long sectionId,
            Long teacherUserId,
            Long subjectId,
            Long classId
    ) {
        String role = requireAnalyticsRole(principal);
        Long effectiveTeacherUserId = teacherUserId;

        if ("teacher".equals(role)) {
            if (teacherUserId != null && !teacherUserId.equals(principal.userId())) {
                throw error("SYNC_ACTIVITY_ACCESS_DENIED", "Teacher may only view their own sync activity.", HttpStatus.FORBIDDEN);
            }
            effectiveTeacherUserId = principal.userId();
        } else if (teacherUserId != null
                && !analyticsRepository.teacherBelongsToSchool(teacherUserId, principal.schoolId())) {
            throw error("TEACHER_NOT_FOUND", "Teacher was not found in the Principal's school.", HttpStatus.NOT_FOUND);
        }

        return analyticsRepository.listSyncActivity(
                principal.schoolId(),
                gradeLevelId,
                sectionId,
                effectiveTeacherUserId,
                subjectId,
                classId
        );
    }

    public V2SchoolAnalyticsResponse getSchoolOverview(
            V2AuthenticatedUser principal,
            Long gradeLevelId,
            Long sectionId,
            Long teacherUserId,
            Long subjectId,
            Long classId
    ) {
        requirePrincipal(principal);
        if (teacherUserId != null
                && !analyticsRepository.teacherBelongsToSchool(teacherUserId, principal.schoolId())) {
            throw error("TEACHER_NOT_FOUND", "Teacher was not found in the Principal's school.", HttpStatus.NOT_FOUND);
        }

        List<V2LmsResponse> lms = analyticsRepository.listSchoolLms(
                        principal.schoolId(),
                        gradeLevelId,
                        sectionId,
                        teacherUserId,
                        subjectId,
                        classId
                ).stream()
                .map(row -> new V2LmsResponse(
                        row.skillId(),
                        row.competencyId(),
                        row.competencyName(),
                        row.earnedPoints(),
                        row.possiblePoints(),
                        row.masteryRate(),
                        interventionStatus(row.masteryRate()),
                        row.respondentCount(),
                        row.affectedStudents()
                ))
                .toList();

        return new V2SchoolAnalyticsResponse(
                analyticsRepository.countSchoolAssessments(
                        principal.schoolId(),
                        gradeLevelId,
                        sectionId,
                        teacherUserId,
                        subjectId,
                        classId
                ),
                lms,
                analyticsRepository.listGradeMastery(
                        principal.schoolId(),
                        gradeLevelId,
                        sectionId,
                        teacherUserId,
                        subjectId,
                        classId
                ),
                analyticsRepository.listAssessmentTrends(
                        principal.schoolId(),
                        gradeLevelId,
                        sectionId,
                        teacherUserId,
                        subjectId,
                        classId
                )
        );
    }

    private void authorize(V2AuthenticatedUser principal, long testId) {
        String role = requireAnalyticsRole(principal);

        V2AnalyticsTestContext context = analyticsRepository.findTestContext(testId)
                .orElseThrow(() -> error("ASSESSMENT_NOT_FOUND", "Assessment not found.", HttpStatus.NOT_FOUND));
        if (principal.schoolId() == null || !principal.schoolId().equals(context.schoolId())) {
            throw error("ASSESSMENT_ACCESS_DENIED", "Assessment belongs to another school.", HttpStatus.FORBIDDEN);
        }
        if ("teacher".equals(role) && principal.userId() != context.teacherUserId()) {
            throw error("ASSESSMENT_ACCESS_DENIED", "Teacher does not own this assessment.", HttpStatus.FORBIDDEN);
        }
    }

    private String requireAnalyticsRole(V2AuthenticatedUser principal) {
        if (principal == null) {
            throw error("AUTHENTICATION_REQUIRED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        String role = principal.role() == null ? "" : principal.role().toLowerCase(Locale.ROOT);
        if (!"teacher".equals(role) && !"principal".equals(role)) {
            throw error("FORBIDDEN", "Teacher or Principal access is required.", HttpStatus.FORBIDDEN);
        }
        if (principal.schoolId() == null || principal.schoolId().isBlank()) {
            throw error("SCHOOL_CONTEXT_REQUIRED", "Authenticated user has no school context.", HttpStatus.FORBIDDEN);
        }
        return role;
    }

    private void requirePrincipal(V2AuthenticatedUser principal) {
        String role = requireAnalyticsRole(principal);
        if (!"principal".equals(role)) {
            throw error("PRINCIPAL_ACCESS_REQUIRED", "Principal access is required.", HttpStatus.FORBIDDEN);
        }
    }

    private String interventionStatus(BigDecimal rate) {
        double value = rate == null ? 0 : rate.doubleValue();
        if (value >= analyticsProperties.getMaintainThreshold()) {
            return "Maintain";
        }
        if (value >= analyticsProperties.getReviewThreshold()) {
            return "Review";
        }
        if (value >= analyticsProperties.getReteachThreshold()) {
            return "Reteach";
        }
        return "Priority Intervention";
    }

    private String performanceStatus(BigDecimal rate) {
        double value = rate == null ? 0 : rate.doubleValue();
        if (value >= analyticsProperties.getMaintainThreshold()) {
            return "Mastered";
        }
        if (value >= analyticsProperties.getReviewThreshold()) {
            return "Developing";
        }
        return "Needs Support";
    }

    private String difficultyLevel(BigDecimal correctnessPercentage) {
        double value = correctnessPercentage == null ? 0 : correctnessPercentage.doubleValue();
        if (value >= analyticsProperties.getEasyItemThreshold()) {
            return "Easy";
        }
        if (value >= analyticsProperties.getModerateItemThreshold()) {
            return "Moderate";
        }
        return "Difficult";
    }

    private V2AuthException error(String code, String message, HttpStatus status) {
        return new V2AuthException(code, message, status);
    }
}
