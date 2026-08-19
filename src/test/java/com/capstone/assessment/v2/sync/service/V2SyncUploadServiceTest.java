package com.capstone.assessment.v2.sync.service;

import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.sync.dto.V2SyncAnswerUploadRequest;
import com.capstone.assessment.v2.sync.dto.V2SyncResultUploadRequest;
import com.capstone.assessment.v2.sync.dto.V2SyncUploadRequest;
import com.capstone.assessment.v2.sync.dto.V2SyncUploadResponse;
import com.capstone.assessment.v2.sync.model.V2SyncQuestionScoringRow;
import com.capstone.assessment.v2.sync.model.V2SyncTestContext;
import com.capstone.assessment.v2.sync.repository.V2SyncUploadRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2SyncUploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T02:00:00Z");
    private static final V2AuthenticatedUser TEACHER = new V2AuthenticatedUser(
            20L,
            "SCHOOL-001",
            "teacher@example.com",
            "teacher",
            "active",
            "session"
    );

    @Mock
    private V2SyncUploadRepository uploadRepository;

    private V2SyncUploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new V2SyncUploadService(
                uploadRepository,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void teacherUploadsManualAnswersAndBackendComputesScore() {
        V2SyncUploadRequest request = uploadRequest();
        when(uploadRepository.findAuthorizedActiveTest(101L, 20L, "SCHOOL-001"))
                .thenReturn(Optional.of(new V2SyncTestContext(101L, 301L, 20L, "SCHOOL-001", "active")));
        when(uploadRepository.listScoringRows(101L)).thenReturn(List.of(
                new V2SyncQuestionScoringRow(9001L, "A", BigDecimal.ONE),
                new V2SyncQuestionScoringRow(9002L, "B", new BigDecimal("2.00"))
        ));
        when(uploadRepository.findSyncIdByUuid(request.syncUuid())).thenReturn(Optional.empty());
        when(uploadRepository.insertSync(request.syncUuid(), 20L, 101L, "mobile-1", "in_progress", NOW))
                .thenReturn(8001L);
        when(uploadRepository.findSyncItemId(8001L, request.results().get(0).resultUuid())).thenReturn(Optional.empty());
        when(uploadRepository.insertSyncItem(8001L, request.results().get(0).resultUuid(), "create"))
                .thenReturn(9001L);
        when(uploadRepository.classListBelongsToClass(501L, 301L, "SCHOOL-001")).thenReturn(true);
        when(uploadRepository.findTestResultIdByUuid(request.results().get(0).resultUuid()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(7001L))
                .thenReturn(Optional.of(7001L));
        when(uploadRepository.insertTestResult(
                eq(request.results().get(0).resultUuid()),
                eq(101L),
                eq(501L),
                eq(1),
                argThat(score -> score.compareTo(BigDecimal.ONE) == 0),
                argThat(max -> max.compareTo(new BigDecimal("3.00")) == 0),
                eq(2),
                any(Instant.class)
        )).thenReturn(7001L);
        when(uploadRepository.findAnswerIdByUuid("11111111-1111-4111-8111-111111111111")).thenReturn(Optional.empty());
        when(uploadRepository.findAnswerIdByUuid("22222222-2222-4222-8222-222222222222")).thenReturn(Optional.empty());

        V2SyncUploadResponse response = uploadService.upload(TEACHER, request);

        assertEquals("success", response.status());
        assertEquals(8001L, response.syncId());
        assertEquals(1, response.items().size());
        assertEquals("success", response.items().get(0).status());
        assertEquals(7001L, response.items().get(0).testResultId());
        verify(uploadRepository).updateSyncItem(9001L, 7001L, "create", "success", null, null, NOW);
        verify(uploadRepository).updateSyncStatus(8001L, "success", NOW, null);
    }

    @Test
    void principalCannotUploadTeacherMobileResults() {
        V2AuthenticatedUser principal = new V2AuthenticatedUser(
                10L,
                "SCHOOL-001",
                "principal@example.com",
                "principal",
                "active",
                "session"
        );

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> uploadService.upload(principal, uploadRequest())
        );

        assertEquals("FORBIDDEN", exception.getCode());
    }

    private V2SyncUploadRequest uploadRequest() {
        OffsetDateTime checkedAt = OffsetDateTime.parse("2026-08-11T14:28:00+08:00");
        return new V2SyncUploadRequest(
                "2.0",
                "0a8d6ad4-c259-42b1-bc1d-1587e098ab31",
                "mobile-1",
                checkedAt,
                101L,
                List.of(new V2SyncResultUploadRequest(
                        "2aa96d8f-9184-4145-b32a-2f2b90847835",
                        "create",
                        501L,
                        1,
                        checkedAt,
                        null,
                        List.of(
                                new V2SyncAnswerUploadRequest(
                                        "11111111-1111-4111-8111-111111111111",
                                        9001L,
                                        "A",
                                        "answered",
                                        "manual",
                                        checkedAt,
                                        null
                                ),
                                new V2SyncAnswerUploadRequest(
                                        "22222222-2222-4222-8222-222222222222",
                                        9002L,
                                        "C",
                                        "answered",
                                        "manual",
                                        checkedAt,
                                        null
                                )
                        )
                ))
        );
    }
}
