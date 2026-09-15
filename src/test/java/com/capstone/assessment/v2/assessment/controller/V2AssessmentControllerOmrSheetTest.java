package com.capstone.assessment.v2.assessment.controller;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.assessment.service.V2AssessmentService;
import com.capstone.assessment.v2.assessment.service.V2OmrSheetPrintService;
import com.capstone.assessment.v2.assessment.service.V2QuestionnairePdfService;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V2AssessmentControllerOmrSheetTest {

    @Test
    void returnsInlinePdfFromExistingOmrSheetEndpoint() {
        V2AssessmentService assessmentService = mock(V2AssessmentService.class);
        V2OmrSheetPrintService printService = mock(V2OmrSheetPrintService.class);
        V2QuestionnairePdfService questionnairePdfService = mock(V2QuestionnairePdfService.class);
        V2AssessmentController controller = new V2AssessmentController(
                assessmentService,
                printService,
                questionnairePdfService
        );
        V2AuthenticatedUser principal = new V2AuthenticatedUser(
                9001L, "school-1", "teacher@example.com", "teacher", "active", "session-1"
        );
        V2AssessmentResponse assessment = mock(V2AssessmentResponse.class);
        byte[] pdf = "%PDF-test".getBytes();

        when(assessmentService.getAssessment(principal, 101L)).thenReturn(assessment);
        when(printService.renderSheet(assessment)).thenReturn(pdf);

        ResponseEntity<byte[]> response = controller.printOmrSheet(principal, 101L);

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("inline", response.getHeaders().getContentDisposition().getType());
        assertEquals("bubble-answer-sheet-101.pdf",
                response.getHeaders().getContentDisposition().getFilename());
        assertArrayEquals(pdf, response.getBody());
        verify(printService).renderSheet(assessment);
    }

    @Test
    void returnsDownloadablePdfFromQuestionnaireEndpoint() {
        V2AssessmentService assessmentService = mock(V2AssessmentService.class);
        V2OmrSheetPrintService omrSheetPrintService = mock(V2OmrSheetPrintService.class);
        V2QuestionnairePdfService questionnairePdfService = mock(V2QuestionnairePdfService.class);
        V2AssessmentController controller = new V2AssessmentController(
                assessmentService,
                omrSheetPrintService,
                questionnairePdfService
        );
        V2AuthenticatedUser principal = new V2AuthenticatedUser(
                9001L, "school-1", "teacher@example.com", "teacher", "active", "session-1"
        );
        V2AssessmentResponse assessment = mock(V2AssessmentResponse.class);
        byte[] pdf = "%PDF-questionnaire".getBytes();

        when(assessmentService.getAssessment(principal, 102L)).thenReturn(assessment);
        when(questionnairePdfService.renderQuestionnaire(assessment)).thenReturn(pdf);

        ResponseEntity<byte[]> response = controller.downloadQuestionnaire(principal, 102L);

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("attachment", response.getHeaders().getContentDisposition().getType());
        assertEquals("test-questionnaire-102.pdf",
                response.getHeaders().getContentDisposition().getFilename());
        assertArrayEquals(pdf, response.getBody());
        verify(questionnairePdfService).renderQuestionnaire(assessment);
    }
}
