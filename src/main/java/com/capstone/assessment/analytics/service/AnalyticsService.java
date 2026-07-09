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

import java.util.List;

public interface AnalyticsService {

    List<ItemAnalysisDto> getItemAnalysis(Long testId);

    List<LmsDto> getLmsByTest(Long testId);

    List<LmsAffectedStudentsDto> getLmsAffectedStudents(Long testId);

    List<InterventionStudentDto> getStudentsForIntervention(Long testId, Long competencyId);

    List<TeacherInterventionRecommendationDto> getTeacherInterventionRecommendations(Long testId);

    List<SchoolLmsDto> getSchoolWideLms(Long gradeLevelId, Long sectionId, Long subjectId, Long teacherId);

    List<StudentSkillMasteryDto> getStudentSkillMastery(Long studentId, Long classId);

    List<AssessmentTrendDto> getAssessmentTrends(Long classId);

    List<SyncActivityDto> getSyncActivity(Long teacherId);
}
