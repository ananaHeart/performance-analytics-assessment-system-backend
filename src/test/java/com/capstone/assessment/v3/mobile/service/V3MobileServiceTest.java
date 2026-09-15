package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.config.V3MobileReleaseProperties;
import com.capstone.assessment.v3.mobile.dto.V3AnswerSheetManifestResponse;
import com.capstone.assessment.v3.mobile.dto.V3MobileDownloadResponse;
import com.capstone.assessment.v3.mobile.dto.V3MobileReferenceDataResponse;
import com.capstone.assessment.v3.mobile.repository.V3MobileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V3MobileServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final V3AuthenticatedUser TEACHER = new V3AuthenticatedUser(
            42L,
            "SCHOOL-001",
            "teacher@example.com",
            "teacher",
            "active",
            "session-uuid"
    );

    private final V3MobileRepository repository = mock(V3MobileRepository.class);
    private V3MobileService service;

    @BeforeEach
    void setUp() {
        service = new V3MobileService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void principalCannotAccessMobileContract() {
        V3AuthenticatedUser principal = new V3AuthenticatedUser(
                10L,
                "SCHOOL-001",
                "principal@example.com",
                "principal",
                "active",
                "principal-session"
        );

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.getReferenceData(principal)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("MOBILE_TEACHER_REQUIRED", exception.getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void referenceDataPublishesValidatedCapabilityAndKeepsUploadUnavailable() {
        when(repository.findQuestionTypes()).thenReturn(List.of(
                new V3MobileReferenceDataResponse.QuestionTypeCapability(
                        1,
                        "multiple_choice",
                        "Multiple Choice",
                        "omr",
                        "automatic",
                        true,
                        false,
                        false,
                        false,
                        true,
                        false
                )
        ));
        when(repository.findPaperSizes()).thenReturn(List.of(
                new V3MobileReferenceDataResponse.PaperSizeCapability(
                        1,
                        "A4",
                        "A4",
                        new BigDecimal("595.276"),
                        new BigDecimal("841.890"),
                        true
                )
        ));
        when(repository.findActiveTemplates()).thenReturn(List.of(
                new V3MobileRepository.TemplateRow(
                        1L,
                        "OMR-A4-10-MC-CTX-V2",
                        "Validated A4 10-item Multiple Choice",
                        "2",
                        "multiple_choice",
                        "A4",
                        "portrait",
                        10,
                        10,
                        4,
                        2,
                        "2.0.0",
                        "pdf_bottom_left",
                        new BigDecimal("100.00"),
                        "a".repeat(64),
                        true
                )
        ));
        when(repository.findTemplateRegions(1L)).thenReturn(List.of());

        V3MobileReferenceDataResponse response = service.getReferenceData(TEACHER);

        assertEquals("3.0", response.contractVersion());
        assertEquals(NOW, response.serverTime());
        assertTrue(response.omrTemplates().get(0).physicallyValidated());
        assertEquals(5, response.syncPolicy().minimumAnswerSheetQuestions());
        assertEquals("upsert", response.syncPolicy().syncAction());
        assertFalse(response.syncPolicy().scanPageUploadAvailable());
        assertTrue(response.syncPolicy().scanPageUploadAvailabilityReason().contains("not enabled"));
        assertTrue(response.statuses().get("syncs").contains("partial_success"));
    }

    @Test
    void referenceDataPublishesUploadCapabilityOnlyForEnabledReleaseProfile() {
        when(repository.findQuestionTypes()).thenReturn(List.of());
        when(repository.findPaperSizes()).thenReturn(List.of());
        when(repository.findActiveTemplates()).thenReturn(List.of());
        V3MobileReleaseProperties properties = new V3MobileReleaseProperties();
        properties.getRelease().setProfileEnabled(true);
        properties.getRelease().setHttpEnabled(true);
        service = new V3MobileService(repository, Clock.fixed(NOW, ZoneOffset.UTC), properties);

        V3MobileReferenceDataResponse response = service.getReferenceData(TEACHER);

        assertTrue(response.syncPolicy().scanPageUploadAvailable());
        assertTrue(response.syncPolicy().scanPageUploadAvailabilityReason().contains("enabled"));
    }

    @Test
    void downloadDerivesCaptureAvailabilityFromScheduleAndLateRule() {
        when(repository.findTestAssignments(TEACHER.userId(), TEACHER.schoolId())).thenReturn(List.of(
                assignment("future", NOW.plusSeconds(60), NOW.plusSeconds(3600), "planned", false),
                assignment("open", NOW.minusSeconds(60), NOW.plusSeconds(3600), "open", false),
                assignment("closed", NOW.minusSeconds(3600), NOW.minusSeconds(60), "closed", false),
                assignment("late", NOW.minusSeconds(3600), NOW.minusSeconds(60), "closed", true),
                assignment("archived", null, null, "archived", true)
        ));

        V3MobileDownloadResponse response = service.download(TEACHER);

        assertEquals(5, response.testAssignments().size());
        assertAvailability(response, "future", false, "scheduled");
        assertAvailability(response, "open", true, "open");
        assertAvailability(response, "closed", false, "closed");
        assertAvailability(response, "late", true, "late_allowed");
        assertAvailability(response, "archived", false, "archived");
        verify(repository).findClassAssignments(TEACHER.userId(), TEACHER.schoolId());
        verify(repository).findStudents(TEACHER.userId(), TEACHER.schoolId());
    }

    @Test
    void incompleteManifestIsRejectedBeforeReturningCoordinates() {
        when(repository.findManifestHeader(
                "assignment-uuid",
                "sheet-uuid",
                TEACHER.userId(),
                TEACHER.schoolId()
        )).thenReturn(Optional.of(manifestHeader(2)));
        when(repository.findManifestPages(900L)).thenReturn(List.of(manifestPage()));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.getManifest(TEACHER, "assignment-uuid", "sheet-uuid")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("ANSWER_SHEET_MANIFEST_INCOMPLETE", exception.getCode());
        verify(repository, never()).findManifestRegions(901L);
    }

    @Test
    void manifestMapsTrueFalseDisplayKeysToStoredABValues() throws Exception {
        when(repository.findManifestHeader(
                "assignment-uuid",
                "sheet-uuid",
                TEACHER.userId(),
                TEACHER.schoolId()
        )).thenReturn(Optional.of(manifestHeader(1)));
        when(repository.findManifestPages(900L)).thenReturn(List.of(manifestPage()));
        when(repository.findManifestRegions(901L)).thenReturn(List.of(
                new V3MobileRepository.ManifestRegionRow(
                        "region-uuid",
                        "OBJECTIVE_SLOT_01",
                        1001L,
                        "question-uuid",
                        501L,
                        1,
                        1,
                        "true_false",
                        "objective_bubbles",
                        "none",
                        null,
                        null,
                        new ObjectMapper().readTree("""
                                {
                                  "rectangle": {"x": 72.6, "y": 597.934, "width": 60, "height": 12.8},
                                  "option_keys": ["T", "F"],
                                  "bubble_centers_pt": [[79, 604.334], [103, 604.334]]
                                }
                                """),
                        "b".repeat(64),
                        null,
                        null,
                        null,
                        null
                )
        ));

        V3AnswerSheetManifestResponse response = service.getManifest(
                TEACHER,
                "assignment-uuid",
                "sheet-uuid"
        );

        List<V3AnswerSheetManifestResponse.OptionCoordinate> options =
                response.pages().get(0).regions().get(0).options();
        assertEquals("T", options.get(0).key());
        assertEquals("A", options.get(0).storedValue());
        assertEquals("F", options.get(1).key());
        assertEquals("B", options.get(1).storedValue());
    }

    private V3MobileRepository.TestAssignmentRow assignment(
            String uuid,
            Instant openAt,
            Instant closeAt,
            String status,
            boolean allowLateCapture
    ) {
        return new V3MobileRepository.TestAssignmentRow(
                uuid.hashCode(),
                uuid,
                100L,
                200L,
                openAt,
                closeAt,
                status,
                allowLateCapture
        );
    }

    private void assertAvailability(
            V3MobileDownloadResponse response,
            String assignmentUuid,
            boolean allowed,
            String availability
    ) {
        V3MobileDownloadResponse.TestAssignment assignment = response.testAssignments().stream()
                .filter(item -> assignmentUuid.equals(item.assignmentUuid()))
                .findFirst()
                .orElseThrow();
        assertEquals(allowed, assignment.captureAllowedNow());
        assertEquals(availability, assignment.captureAvailability());
    }

    private V3MobileRepository.ManifestHeaderRow manifestHeader(int totalPages) {
        return new V3MobileRepository.ManifestHeaderRow(
                900L,
                "sheet-uuid",
                1,
                800L,
                "assignment-uuid",
                "A4",
                new BigDecimal("595.276"),
                new BigDecimal("841.890"),
                1,
                10,
                totalPages,
                "c".repeat(64),
                "3.0.0",
                NOW.minusSeconds(300)
        );
    }

    private V3MobileRepository.ManifestPageRow manifestPage() {
        return new V3MobileRepository.ManifestPageRow(
                901L,
                "page-uuid",
                1,
                1,
                "OMR-A4-10-MC-CTX-V2",
                "2",
                "d".repeat(64),
                "portrait",
                "pdf_bottom_left",
                2,
                "{\"v\":2}",
                "e".repeat(64),
                "f".repeat(64)
        );
    }
}
