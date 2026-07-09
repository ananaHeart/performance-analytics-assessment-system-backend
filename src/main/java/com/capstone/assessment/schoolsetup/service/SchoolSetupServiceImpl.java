package com.capstone.assessment.schoolsetup.service;

import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.schoolsetup.dto.ClassAssignmentDto;
import com.capstone.assessment.schoolsetup.dto.CreateClassAssignmentRequest;
import com.capstone.assessment.schoolsetup.dto.CreateSectionRequest;
import com.capstone.assessment.schoolsetup.dto.GradeLevelDto;
import com.capstone.assessment.schoolsetup.dto.SectionDto;
import com.capstone.assessment.schoolsetup.dto.StudentDto;
import com.capstone.assessment.schoolsetup.dto.SubjectDto;
import com.capstone.assessment.schoolsetup.dto.TeacherDto;
import com.capstone.assessment.schoolsetup.repository.SchoolSetupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SchoolSetupServiceImpl implements SchoolSetupService {

    private final SchoolSetupRepository schoolSetupRepository;

    public SchoolSetupServiceImpl(SchoolSetupRepository schoolSetupRepository) {
        this.schoolSetupRepository = schoolSetupRepository;
    }

    @Override
    public List<GradeLevelDto> getGradeLevels() {
        return schoolSetupRepository.findAllGradeLevels();
    }

    @Override
    public List<SubjectDto> getSubjects() {
        return schoolSetupRepository.findAllSubjects();
    }

    @Override
    public List<SectionDto> getSections() {
        return schoolSetupRepository.findAllSections();
    }

    @Override
    public List<TeacherDto> getTeachers() {
        return schoolSetupRepository.findAllTeachers();
    }

    @Override
    public List<StudentDto> getStudents() {
        return schoolSetupRepository.findAllStudents();
    }

    @Override
    public List<ClassAssignmentDto> getClassAssignments() {
        return schoolSetupRepository.findAllClassAssignments();
    }

    @Override
    @Transactional
    public Long createSection(CreateSectionRequest request) {
        validateCreateSectionRequest(request);

        if (schoolSetupRepository.sectionExists(request.gradeLevelId(), request.sectionName().trim())) {
            throw new BadRequestException("Section already exists for this grade level.");
        }

        return schoolSetupRepository.createSection(
                new CreateSectionRequest(request.gradeLevelId(), request.sectionName().trim())
        );
    }

    @Override
    @Transactional
    public Long createClassAssignment(CreateClassAssignmentRequest request) {
        validateCreateClassAssignmentRequest(request);

        if (schoolSetupRepository.classAssignmentExists(
                request.academicYearId(),
                request.teacherId(),
                request.subjectId(),
                request.sectionId()
        )) {
            throw new BadRequestException("Class assignment already exists.");
        }

        return schoolSetupRepository.createClassAssignment(request);
    }

    private void validateCreateSectionRequest(CreateSectionRequest request) {
        if (request == null) {
            throw new BadRequestException("Create section request must not be null.");
        }

        if (request.gradeLevelId() == null) {
            throw new BadRequestException("Grade level ID is required.");
        }

        if (request.sectionName() == null || request.sectionName().isBlank()) {
            throw new BadRequestException("Section name is required.");
        }
    }

    private void validateCreateClassAssignmentRequest(CreateClassAssignmentRequest request) {
        if (request == null) {
            throw new BadRequestException("Create class assignment request must not be null.");
        }

        if (request.academicYearId() == null) {
            throw new BadRequestException("Academic year ID is required.");
        }

        if (request.teacherId() == null) {
            throw new BadRequestException("Teacher ID is required.");
        }

        if (request.subjectId() == null) {
            throw new BadRequestException("Subject ID is required.");
        }

        if (request.sectionId() == null) {
            throw new BadRequestException("Section ID is required.");
        }
    }
}
