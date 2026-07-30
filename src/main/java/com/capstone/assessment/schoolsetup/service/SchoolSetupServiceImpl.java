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
    public List<SectionDto> getAvailableSectionsForAssignment(Long gradeLevelId, Long academicYearId, Long subjectId) {
        validateAvailableSectionRequest(gradeLevelId, academicYearId, subjectId);
        return schoolSetupRepository.findAvailableSectionsForAssignment(gradeLevelId, academicYearId, subjectId);
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
        throw new BadRequestException("Sections must be created through SF1 import.");
    }

    @Override
    @Transactional
    public Long createClassAssignment(CreateClassAssignmentRequest request) {
        validateCreateClassAssignmentRequest(request);
        Long resolvedGradeLevelId = resolveGradeLevelId(request);

        if (!schoolSetupRepository.sectionMatchesGradeLevelAndAcademicYear(
                request.sectionId(),
                resolvedGradeLevelId,
                request.academicYearId()
        )) {
            throw new BadRequestException("Selected section does not match the selected grade level and academic year.");
        }

        if (!schoolSetupRepository.sectionHasEnrolledStudents(request.sectionId(), request.academicYearId())) {
            throw new BadRequestException("Selected section has no imported students for this academic year.");
        }

        if (schoolSetupRepository.classSectionSubjectAssignmentExists(
                request.academicYearId(),
                request.subjectId(),
                request.sectionId()
        )) {
            throw new BadRequestException("Section is already assigned for this subject and academic year.");
        }

        return schoolSetupRepository.createClassAssignment(request);
    }

    private Long resolveGradeLevelId(CreateClassAssignmentRequest request) {
        if (request.gradeLevelId() != null) {
            return request.gradeLevelId();
        }

        return schoolSetupRepository.findGradeLevelIdBySectionId(request.sectionId())
                .orElseThrow(() -> new BadRequestException("Selected section was not found."));
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

    private void validateAvailableSectionRequest(Long gradeLevelId, Long academicYearId, Long subjectId) {
        if (gradeLevelId == null) {
            throw new BadRequestException("Grade level ID is required.");
        }

        if (academicYearId == null) {
            throw new BadRequestException("Academic year ID is required.");
        }

        if (subjectId == null) {
            throw new BadRequestException("Subject ID is required.");
        }
    }
}
