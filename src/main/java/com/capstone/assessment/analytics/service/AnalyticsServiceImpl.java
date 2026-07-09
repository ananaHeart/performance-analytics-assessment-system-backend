package com.capstone.assessment.analytics.service;

import com.capstone.assessment.analytics.dto.AssessmentTrendDto;
import com.capstone.assessment.analytics.dto.InterventionStudentDto;
import com.capstone.assessment.analytics.dto.ItemAnalysisDto;
import com.capstone.assessment.analytics.dto.LmsAffectedStudentsDto;
import com.capstone.assessment.analytics.dto.LmsDto;
import com.capstone.assessment.analytics.dto.SchoolLmsDto;
import com.capstone.assessment.analytics.dto.StudentSkillMasteryDto;
import com.capstone.assessment.analytics.dto.SyncActivityDto;
import com.capstone.assessment.analytics.dto.TeacherInterventionRecommendationDto;
import com.capstone.assessment.analytics.repository.AnalyticsRepository;
import com.capstone.assessment.common.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final double EXPECTED_MASTERY_RATE = 75.0;

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsServiceImpl(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Override
    public List<ItemAnalysisDto> getItemAnalysis(Long testId) {
        validateTestId(testId);

        // Analytics is computed from synced item-level records stored in the backend.
        return analyticsRepository.getItemAnalysis(testId);
    }

    @Override
    public List<LmsDto> getLmsByTest(Long testId) {
        validateTestId(testId);

        // LMS is derived from backend-synced item results, not from frontend or mobile computation.
        return analyticsRepository.getLmsByTest(testId);
    }

    @Override
    public List<LmsAffectedStudentsDto> getLmsAffectedStudents(Long testId) {
        validateTestId(testId);

        // This identifies students affected by least mastered competencies for teacher remediation planning only.
        return analyticsRepository.getLmsAffectedStudents(testId);
    }

    @Override
    public List<InterventionStudentDto> getStudentsForIntervention(Long testId, Long competencyId) {
        validateTestId(testId);

        if (competencyId == null) {
            throw new BadRequestException("Competency ID is required.");
        }

        // Intervention lists are based on synced item-level performance per competency.
        return analyticsRepository.getStudentsForIntervention(testId, competencyId);
    }

    @Override
    public List<TeacherInterventionRecommendationDto> getTeacherInterventionRecommendations(Long testId) {
        validateTestId(testId);

        return analyticsRepository.getLmsByTest(testId).stream()
                .map(lms -> {
                    List<InterventionStudentDto> affectedStudents =
                            analyticsRepository.getStudentsForIntervention(testId, lms.competencyId());
                    return buildTeacherRecommendation(lms, affectedStudents);
                })
                .filter(recommendation ->
                        recommendation.masteryRate() < EXPECTED_MASTERY_RATE
                                || recommendation.affectedLearnersCount() > 0)
                .toList();
    }

    @Override
    public List<SchoolLmsDto> getSchoolWideLms(Long gradeLevelId, Long sectionId, Long subjectId, Long teacherId) {
        // School-wide LMS is aggregated from synced backend item-level records.
        return analyticsRepository.getSchoolWideLms(gradeLevelId, sectionId, subjectId, teacherId);
    }

    @Override
    public List<StudentSkillMasteryDto> getStudentSkillMastery(Long studentId, Long classId) {
        if (studentId == null) {
            throw new BadRequestException("Student ID is required.");
        }

        if (classId == null) {
            throw new BadRequestException("Class ID is required.");
        }

        return analyticsRepository.getStudentSkillMastery(studentId, classId);
    }

    @Override
    public List<AssessmentTrendDto> getAssessmentTrends(Long classId) {
        if (classId == null) {
            throw new BadRequestException("Class ID is required.");
        }

        // Trend data comes from synchronized assessment records stored in the backend.
        return analyticsRepository.getAssessmentTrends(classId);
    }

    @Override
    public List<SyncActivityDto> getSyncActivity(Long teacherId) {
        if (teacherId == null) {
            throw new BadRequestException("Teacher ID is required.");
        }

        return analyticsRepository.getSyncActivity(teacherId);
    }

    private TeacherInterventionRecommendationDto buildTeacherRecommendation(
            LmsDto lms,
            List<InterventionStudentDto> affectedStudents
    ) {
        int affectedLearnersCount = affectedStudents.size();
        String action = getRecommendedAction(lms.status(), lms.competencyName());
        String targetGroup = affectedLearnersCount > 0
                ? "Affected learners"
                : "Whole class";
        String followUpActivity = getFollowUpActivity(lms.status());
        String recommendation = getRecommendationText(lms.status(), lms.competencyName());

        return new TeacherInterventionRecommendationDto(
                lms.competencyId(),
                lms.competencyName(),
                lms.masteryRate(),
                lms.status(),
                affectedLearnersCount,
                action,
                targetGroup,
                followUpActivity,
                recommendation,
                affectedStudents
        );
    }

    private String getRecommendedAction(String status, String competencyName) {
        if ("Maintain".equals(status)) {
            return "Maintain " + competencyName;
        }

        if ("Review".equals(status)) {
            return "Review " + competencyName;
        }

        if ("Reteach".equals(status)) {
            return "Reteach " + competencyName;
        }

        return "Priority Intervention for " + competencyName;
    }

    private String getRecommendationText(String status, String competencyName) {
        if ("Maintain".equals(status)) {
            return "Continue monitoring " + competencyName
                    + ". No immediate intervention is needed, but include short review questions in the next assessment.";
        }

        if ("Review".equals(status)) {
            return "Review " + competencyName
                    + " with the affected learners using guided examples and short practice exercises. "
                    + "After the review, give a quick follow-up activity based on the missed items.";
        }

        if ("Reteach".equals(status)) {
            return "Reteach " + competencyName
                    + " using simple examples and guided practice. "
                    + "Give the affected learners another short activity to check their understanding.";
        }

        return "Provide focused support for " + competencyName
                + ". Work with the affected learners in a small group, review the missed items, "
                + "and give a short follow-up assessment.";
    }

    private String getFollowUpActivity(String status) {
        if ("Maintain".equals(status)) {
            return "Include short review questions in the next assessment.";
        }

        if ("Review".equals(status)) {
            return "Quick follow-up activity based on missed items.";
        }

        if ("Reteach".equals(status)) {
            return "Short activity to check understanding.";
        }

        return "Short follow-up assessment.";
    }

    private void validateTestId(Long testId) {
        if (testId == null) {
            throw new BadRequestException("Test ID is required.");
        }
    }
}
