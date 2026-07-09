package com.capstone.assessment.gradingperiod.service;

import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.common.exception.ResourceNotFoundException;
import com.capstone.assessment.gradingperiod.dto.CreateGradingPeriodRequest;
import com.capstone.assessment.gradingperiod.dto.GradingPeriodDto;
import com.capstone.assessment.gradingperiod.repository.GradingPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class GradingPeriodServiceImpl implements GradingPeriodService {

    private final GradingPeriodRepository gradingPeriodRepository;

    public GradingPeriodServiceImpl(GradingPeriodRepository gradingPeriodRepository) {
        this.gradingPeriodRepository = gradingPeriodRepository;
    }

    @Override
    public List<GradingPeriodDto> getByAcademicYear(Long academicYearId) {
        if (academicYearId == null) {
            throw new BadRequestException("Academic year ID is required.");
        }

        if (!gradingPeriodRepository.academicYearExists(academicYearId)) {
            throw new ResourceNotFoundException("Academic year not found.");
        }

        return gradingPeriodRepository.findByAcademicYearId(academicYearId);
    }

    @Override
    @Transactional
    public Long create(CreateGradingPeriodRequest request) {
        validateCreateRequest(request);

        if (!gradingPeriodRepository.academicYearExists(request.academicYearId())) {
            throw new ResourceNotFoundException("Academic year not found.");
        }

        String normalizedPeriodName = request.periodName().trim();
        String normalizedStatus = normalizeStatus(request.status());

        if (gradingPeriodRepository.periodNameExists(request.academicYearId(), normalizedPeriodName)) {
            throw new BadRequestException("Grading period name already exists for this academic year.");
        }

        if (gradingPeriodRepository.periodOrderExists(request.academicYearId(), request.periodOrder())) {
            throw new BadRequestException("Grading period order already exists for this academic year.");
        }

        return gradingPeriodRepository.create(new CreateGradingPeriodRequest(
                request.academicYearId(),
                normalizedPeriodName,
                request.periodOrder(),
                request.startDate(),
                request.endDate(),
                normalizedStatus
        ));
    }

    private void validateCreateRequest(CreateGradingPeriodRequest request) {
        if (request == null) {
            throw new BadRequestException("Create grading period request must not be null.");
        }

        if (request.academicYearId() == null) {
            throw new BadRequestException("Academic year ID is required.");
        }

        if (request.periodName() == null || request.periodName().isBlank()) {
            throw new BadRequestException("Period name is required.");
        }

        if (request.periodOrder() == null) {
            throw new BadRequestException("Period order is required.");
        }

        if (request.periodOrder() <= 0) {
            throw new BadRequestException("Period order must be greater than 0.");
        }

        if (request.startDate() == null) {
            throw new BadRequestException("Start date is required.");
        }

        if (request.endDate() == null) {
            throw new BadRequestException("End date is required.");
        }

        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("End date must be on or after start date.");
        }

        normalizeStatus(request.status());
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Active";
        }

        String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
        if ("active".equals(normalizedStatus)) {
            return "Active";
        }

        if ("completed".equals(normalizedStatus)) {
            return "Completed";
        }

        throw new BadRequestException("Status must be either Active or Completed.");
    }
}
