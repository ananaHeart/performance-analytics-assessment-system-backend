package com.capstone.assessment.v2.teacherroster.service;

import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.importexport.dto.V2StudentRecordResponse;
import com.capstone.assessment.v2.teacherroster.repository.V2TeacherRosterRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Profile("v2")
@Service
public class V2TeacherRosterService {

    private final V2TeacherRosterRepository repository;

    public V2TeacherRosterService(V2TeacherRosterRepository repository) {
        this.repository = repository;
    }

    public List<V2StudentRecordResponse> listStudents(
            V2AuthenticatedUser principal,
            long classId
    ) {
        if (principal == null || !"teacher".equalsIgnoreCase(principal.role())) {
            throw new BadRequestException("An authenticated teacher account is required.");
        }
        if (principal.schoolId() == null || principal.schoolId().isBlank()) {
            throw new BadRequestException("The teacher account is not linked to a school.");
        }

        return repository.listStudents(principal.userId(), principal.schoolId(), classId);
    }
}
