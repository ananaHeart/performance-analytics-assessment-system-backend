package com.capstone.assessment.gradingperiod.service;

import com.capstone.assessment.gradingperiod.dto.CreateGradingPeriodRequest;
import com.capstone.assessment.gradingperiod.dto.GradingPeriodDto;

import java.util.List;

public interface GradingPeriodService {

    List<GradingPeriodDto> getByAcademicYear(Long academicYearId);

    Long create(CreateGradingPeriodRequest request);
}
