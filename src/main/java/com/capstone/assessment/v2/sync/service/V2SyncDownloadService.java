package com.capstone.assessment.v2.sync.service;

import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.sync.dto.V2SyncClassAssignmentDto;
import com.capstone.assessment.v2.sync.dto.V2SyncDownloadResponse;
import com.capstone.assessment.v2.sync.dto.V2SyncQuestionMappingDto;
import com.capstone.assessment.v2.sync.dto.V2SyncTestDto;
import com.capstone.assessment.v2.sync.dto.V2SyncTestPartDto;
import com.capstone.assessment.v2.sync.dto.V2SyncUserDto;
import com.capstone.assessment.v2.sync.repository.V2SyncDownloadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Profile("v2")
@Service
public class V2SyncDownloadService {

    private static final String CONTRACT_VERSION = "2.0";
    private static final String TEACHER_ROLE = "teacher";

    private final V2SyncDownloadRepository syncDownloadRepository;
    private final Clock clock;

    @Autowired
    public V2SyncDownloadService(V2SyncDownloadRepository syncDownloadRepository) {
        this(syncDownloadRepository, Clock.systemUTC());
    }

    V2SyncDownloadService(V2SyncDownloadRepository syncDownloadRepository, Clock clock) {
        this.syncDownloadRepository = syncDownloadRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public V2SyncDownloadResponse download(V2AuthenticatedUser principal) {
        requireTeacher(principal);

        List<V2SyncClassAssignmentDto> assignments = syncDownloadRepository.listActiveAssignments(
                principal.userId(),
                principal.schoolId()
        );
        List<Long> classAssignmentIds = assignments.stream()
                .map(V2SyncClassAssignmentDto::classAssignmentId)
                .toList();
        List<Long> classIds = assignments.stream()
                .map(V2SyncClassAssignmentDto::classId)
                .distinct()
                .toList();

        List<V2SyncTestDto> tests = syncDownloadRepository.listActiveTests(classAssignmentIds);
        List<Long> testIds = tests.stream()
                .map(V2SyncTestDto::testId)
                .toList();
        List<V2SyncTestPartDto> testParts = syncDownloadRepository.listTestParts(testIds);
        List<Long> testPartIds = testParts.stream()
                .map(V2SyncTestPartDto::testPartId)
                .toList();
        var questions = syncDownloadRepository.listQuestions(testPartIds);
        List<Long> questionIds = questions.stream()
                .map(com.capstone.assessment.v2.sync.dto.V2SyncQuestionDto::questionId)
                .toList();
        List<V2SyncQuestionMappingDto> questionMappings = syncDownloadRepository.listQuestionMappings(questionIds);
        List<Long> skillIds = questionMappings.stream()
                .map(V2SyncQuestionMappingDto::skillId)
                .distinct()
                .toList();

        return new V2SyncDownloadResponse(
                CONTRACT_VERSION,
                clock.instant(),
                new V2SyncUserDto(
                        principal.userId(),
                        principal.schoolId(),
                        principal.email(),
                        principal.role(),
                        principal.status()
                ),
                assignments,
                syncDownloadRepository.listClasses(classIds),
                syncDownloadRepository.listClassLists(classIds, principal.schoolId()),
                syncDownloadRepository.listStudents(classIds, principal.schoolId()),
                tests,
                testParts,
                questions,
                syncDownloadRepository.listAnswerKeys(questionIds),
                syncDownloadRepository.listSkills(skillIds),
                questionMappings
        );
    }

    private void requireTeacher(V2AuthenticatedUser principal) {
        if (principal == null || !TEACHER_ROLE.equalsIgnoreCase(principal.role())) {
            throw new V2AuthException("FORBIDDEN", "Teacher access is required.", HttpStatus.FORBIDDEN);
        }
        if (principal.schoolId() == null || principal.schoolId().isBlank()) {
            throw new V2AuthException("SCHOOL_CONTEXT_REQUIRED", "School context is required.", HttpStatus.FORBIDDEN);
        }
    }
}
