package com.capstone.assessment.assessmentsetup.service;

import com.capstone.assessment.assessmentsetup.dto.AssessmentDetailDto;
import com.capstone.assessment.assessmentsetup.dto.AssessmentDto;
import com.capstone.assessment.assessmentsetup.dto.CompetencyOptionDto;
import com.capstone.assessment.assessmentsetup.dto.CreateAssessmentRequest;
import com.capstone.assessment.assessmentsetup.dto.CreateTestPartRequest;

import java.util.List;

public interface AssessmentSetupService {

    List<AssessmentDto> getAssessmentsByTeacher(Long teacherId);

    AssessmentDetailDto getAssessmentDetail(Long testId);

    Long createAssessment(CreateAssessmentRequest request);

    Long createTestPart(Long testId, CreateTestPartRequest request);

    List<CompetencyOptionDto> getCompetenciesForClass(Long classId);
}
