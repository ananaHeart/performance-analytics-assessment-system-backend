package com.capstone.assessment.schoolsetup.service;

import com.capstone.assessment.schoolsetup.dto.ClassAssignmentDto;
import com.capstone.assessment.schoolsetup.dto.CreateClassAssignmentRequest;
import com.capstone.assessment.schoolsetup.dto.CreateSectionRequest;
import com.capstone.assessment.schoolsetup.dto.GradeLevelDto;
import com.capstone.assessment.schoolsetup.dto.SectionDto;
import com.capstone.assessment.schoolsetup.dto.StudentDto;
import com.capstone.assessment.schoolsetup.dto.SubjectDto;
import com.capstone.assessment.schoolsetup.dto.TeacherDto;

import java.util.List;

public interface SchoolSetupService {

    List<GradeLevelDto> getGradeLevels();

    List<SubjectDto> getSubjects();

    List<SectionDto> getSections();

    List<TeacherDto> getTeachers();

    List<StudentDto> getStudents();

    List<ClassAssignmentDto> getClassAssignments();

    Long createSection(CreateSectionRequest request);

    Long createClassAssignment(CreateClassAssignmentRequest request);
}
