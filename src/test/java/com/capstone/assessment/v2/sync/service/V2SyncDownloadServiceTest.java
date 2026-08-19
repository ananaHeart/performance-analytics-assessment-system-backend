package com.capstone.assessment.v2.sync.service;

import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.sync.dto.V2SyncAnswerKeyDto;
import com.capstone.assessment.v2.sync.dto.V2SyncClassAssignmentDto;
import com.capstone.assessment.v2.sync.dto.V2SyncClassDto;
import com.capstone.assessment.v2.sync.dto.V2SyncClassListDto;
import com.capstone.assessment.v2.sync.dto.V2SyncDownloadResponse;
import com.capstone.assessment.v2.sync.dto.V2SyncQuestionDto;
import com.capstone.assessment.v2.sync.dto.V2SyncQuestionMappingDto;
import com.capstone.assessment.v2.sync.dto.V2SyncSkillDto;
import com.capstone.assessment.v2.sync.dto.V2SyncStudentDto;
import com.capstone.assessment.v2.sync.dto.V2SyncTestDto;
import com.capstone.assessment.v2.sync.dto.V2SyncTestPartDto;
import com.capstone.assessment.v2.sync.repository.V2SyncDownloadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class V2SyncDownloadServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T07:00:00Z");
    private static final V2AuthenticatedUser TEACHER = new V2AuthenticatedUser(
            20L,
            "SCHOOL-001",
            "teacher@example.com",
            "teacher",
            "active",
            "teacher-session"
    );

    @Mock
    private V2SyncDownloadRepository syncDownloadRepository;

    private V2SyncDownloadService syncDownloadService;

    @BeforeEach
    void setUp() {
        syncDownloadService = new V2SyncDownloadService(
                syncDownloadRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void teacherDownloadsOnlyScopedActiveAssessmentData() {
        when(syncDownloadRepository.listActiveAssignments(20L, "SCHOOL-001"))
                .thenReturn(List.of(assignment()));
        when(syncDownloadRepository.listActiveTests(List.of(500L))).thenReturn(List.of(test()));
        when(syncDownloadRepository.listTestParts(List.of(900L))).thenReturn(List.of(part()));
        when(syncDownloadRepository.listQuestions(List.of(1001L))).thenReturn(List.of(question()));
        when(syncDownloadRepository.listQuestionMappings(List.of(2001L))).thenReturn(List.of(mapping()));
        when(syncDownloadRepository.listClasses(List.of(700L))).thenReturn(List.of(clazz()));
        when(syncDownloadRepository.listClassLists(List.of(700L), "SCHOOL-001")).thenReturn(List.of(classList()));
        when(syncDownloadRepository.listStudents(List.of(700L), "SCHOOL-001")).thenReturn(List.of(student()));
        when(syncDownloadRepository.listAnswerKeys(List.of(2001L))).thenReturn(List.of(answerKey()));
        when(syncDownloadRepository.listSkills(List.of(3001L))).thenReturn(List.of(skill()));

        V2SyncDownloadResponse response = syncDownloadService.download(TEACHER);

        assertEquals("2.0", response.contractVersion());
        assertEquals(NOW, response.generatedAt());
        assertEquals(20L, response.user().userId());
        assertEquals(1, response.classAssignments().size());
        assertEquals(1, response.classes().size());
        assertEquals(1, response.classLists().size());
        assertEquals(1, response.students().size());
        assertEquals(1, response.tests().size());
        assertEquals(1, response.testParts().size());
        assertEquals(1, response.questions().size());
        assertEquals(1, response.answerKeys().size());
        assertEquals(1, response.skills().size());
        assertEquals(1, response.questionMappings().size());
    }

    @Test
    void principalCannotDownloadTeacherMobilePayload() {
        V2AuthenticatedUser principal = new V2AuthenticatedUser(
                10L,
                "SCHOOL-001",
                "principal@example.com",
                "principal",
                "active",
                "principal-session"
        );

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> syncDownloadService.download(principal)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        verify(syncDownloadRepository, never()).listActiveAssignments(anyLong(), anyString());
    }

    @Test
    void teacherWithNoAssignmentsReceivesEmptyPayload() {
        when(syncDownloadRepository.listActiveAssignments(20L, "SCHOOL-001")).thenReturn(List.of());

        V2SyncDownloadResponse response = syncDownloadService.download(TEACHER);

        assertEquals(0, response.classAssignments().size());
        assertEquals(0, response.tests().size());
        assertEquals(0, response.questions().size());
        verify(syncDownloadRepository).listClasses(List.of());
        verify(syncDownloadRepository).listActiveTests(List.of());
    }

    private V2SyncClassAssignmentDto assignment() {
        return new V2SyncClassAssignmentDto(
                500L, 700L, 1, "2026-2027", 1, "Grade 7",
                10, "Rizal", 3, "Computer", "primary", "active"
        );
    }

    private V2SyncClassDto clazz() {
        return new V2SyncClassDto(700L, 1, "2026-2027", 1, "Grade 7", 10, "Rizal", "active");
    }

    private V2SyncClassListDto classList() {
        return new V2SyncClassListDto(800L, 700L, 300L);
    }

    private V2SyncStudentDto student() {
        return new V2SyncStudentDto(300L, "SCHOOL-001", "123456789012", "Ana", null, "Santos", null, "active");
    }

    private V2SyncTestDto test() {
        return new V2SyncTestDto(
                900L, 500L, 11, "First Quarter", "Quiz 1", "quiz",
                LocalDate.of(2026, 8, 20), null, 1, "active"
        );
    }

    private V2SyncTestPartDto part() {
        return new V2SyncTestPartDto(1001L, 900L, 1, "Part I", "multiple_choice", 1, BigDecimal.ONE);
    }

    private V2SyncQuestionDto question() {
        return new V2SyncQuestionDto(2001L, 1001L, 1, "Question 1", "A", "B", "C", "D", null);
    }

    private V2SyncAnswerKeyDto answerKey() {
        return new V2SyncAnswerKeyDto(2001L, "A");
    }

    private V2SyncQuestionMappingDto mapping() {
        return new V2SyncQuestionMappingDto(2001L, 3001L);
    }

    private V2SyncSkillDto skill() {
        return new V2SyncSkillDto(3001L, 4001L, "Skill A", 501, "Root A", 11, 1, 3);
    }
}
