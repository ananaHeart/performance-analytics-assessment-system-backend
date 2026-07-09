package com.capstone.assessment.importexport.service;

import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.common.exception.ResourceNotFoundException;
import com.capstone.assessment.importexport.dto.ManualStudentRequest;
import com.capstone.assessment.importexport.dto.ManualStudentResponse;
import com.capstone.assessment.importexport.dto.ManualStudentUpdateRequest;
import com.capstone.assessment.importexport.repository.ManualStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ManualStudentServiceImpl implements ManualStudentService {

    private final ManualStudentRepository manualStudentRepository;

    public ManualStudentServiceImpl(ManualStudentRepository manualStudentRepository) {
        this.manualStudentRepository = manualStudentRepository;
    }

    @Override
    public List<ManualStudentResponse> getStudents() {
        return manualStudentRepository.findAllStudents();
    }

    @Override
    @Transactional
    public ManualStudentResponse createStudent(ManualStudentRequest request) {
        validateCreateRequest(request);
        validateReferenceData(request.sectionId(), request.academicYearId());

        String normalizedLrn = request.studentLrn().trim();
        if (manualStudentRepository.studentExistsByLrn(normalizedLrn)) {
            throw new BadRequestException("Student LRN already exists.");
        }

        ManualStudentRequest normalizedRequest = new ManualStudentRequest(
                normalizedLrn,
                request.firstName().trim(),
                request.lastName().trim(),
                request.gender().trim(),
                request.sectionId(),
                request.academicYearId()
        );

        Long studentId = manualStudentRepository.createStudent(normalizedRequest);
        manualStudentRepository.enrollStudent(studentId, normalizedRequest.sectionId(), normalizedRequest.academicYearId());

        return manualStudentRepository.findStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
    }

    @Override
    @Transactional
    public ManualStudentResponse updateStudent(Long studentId, ManualStudentUpdateRequest request) {
        if (studentId == null) {
            throw new BadRequestException("Student ID is required.");
        }

        validateUpdateRequest(request);

        ManualStudentResponse existingStudent = manualStudentRepository.findStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));

        validateReferenceData(request.sectionId(), request.academicYearId());

        String normalizedLrn = request.studentLrn().trim();
        if (!normalizedLrn.equals(existingStudent.studentLrn())
                && manualStudentRepository.studentExistsByLrn(normalizedLrn)) {
            throw new BadRequestException("Student LRN already exists.");
        }

        ManualStudentUpdateRequest normalizedRequest = new ManualStudentUpdateRequest(
                normalizedLrn,
                request.firstName().trim(),
                request.lastName().trim(),
                request.gender().trim(),
                request.sectionId(),
                request.academicYearId()
        );

        manualStudentRepository.updateStudent(studentId, normalizedRequest);

        return manualStudentRepository.findStudentById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
    }

    private void validateCreateRequest(ManualStudentRequest request) {
        if (request == null) {
            throw new BadRequestException("Manual student request must not be null.");
        }

        validateCommonFields(
                request.studentLrn(),
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.sectionId(),
                request.academicYearId()
        );
    }

    private void validateUpdateRequest(ManualStudentUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Manual student update request must not be null.");
        }

        validateCommonFields(
                request.studentLrn(),
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.sectionId(),
                request.academicYearId()
        );
    }

    private void validateCommonFields(
            String studentLrn,
            String firstName,
            String lastName,
            String gender,
            Long sectionId,
            Long academicYearId
    ) {
        if (studentLrn == null || studentLrn.isBlank()) {
            throw new BadRequestException("Student LRN is required.");
        }

        if (firstName == null || firstName.isBlank()) {
            throw new BadRequestException("First name is required.");
        }

        if (lastName == null || lastName.isBlank()) {
            throw new BadRequestException("Last name is required.");
        }

        if (gender == null || gender.isBlank()) {
            throw new BadRequestException("Gender is required.");
        }

        if (sectionId == null) {
            throw new BadRequestException("Section ID is required.");
        }

        if (academicYearId == null) {
            throw new BadRequestException("Academic year ID is required.");
        }
    }

    private void validateReferenceData(Long sectionId, Long academicYearId) {
        if (!manualStudentRepository.sectionExists(sectionId)) {
            throw new ResourceNotFoundException("Section not found.");
        }

        if (!manualStudentRepository.academicYearExists(academicYearId)) {
            throw new ResourceNotFoundException("Academic year not found.");
        }
    }
}
