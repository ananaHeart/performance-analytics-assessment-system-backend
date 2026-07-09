package com.capstone.assessment.importexport.service;

import com.capstone.assessment.importexport.dto.ManualStudentRequest;
import com.capstone.assessment.importexport.dto.ManualStudentResponse;
import com.capstone.assessment.importexport.dto.ManualStudentUpdateRequest;

import java.util.List;

public interface ManualStudentService {

    List<ManualStudentResponse> getStudents();

    ManualStudentResponse createStudent(ManualStudentRequest request);

    ManualStudentResponse updateStudent(Long studentId, ManualStudentUpdateRequest request);
}
