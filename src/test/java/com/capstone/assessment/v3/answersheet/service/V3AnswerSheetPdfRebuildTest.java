package com.capstone.assessment.v3.answersheet.service;

import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.AssignmentContext;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.GenerationPlan;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Question;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.StoredPage;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.StoredVersion;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Template;
import com.capstone.assessment.v3.answersheet.repository.V3AnswerSheetRepository;
import com.capstone.assessment.v3.answersheet.repository.V3AnswerSheetRepository.QuestionContentRow;
import com.capstone.assessment.v3.answersheet.repository.V3AnswerSheetRepository.QuestionOptionRow;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Render Free wipes stored answer-sheet PDFs on restart. These tests generate a sheet, drop its file,
 * and download it again: the rebuilt sheet must keep every stored page identity, or be refused.
 */
class V3AnswerSheetPdfRebuildTest {

    private static final V3AuthenticatedUser TEACHER = new V3AuthenticatedUser(
            42L, "SCHOOL-001", "teacher@example.com", "teacher", "active", "session");
    // Sub-second, like a real clock; answer_sheet_versions.generated_at keeps whole seconds only.
    private static final Instant GENERATED_AT = Instant.parse("2026-09-01T00:00:00.123456789Z");
    private static final long VERSION_ID = 77L;
    private static final String STORAGE_KEY = "sheet.pdf";

    private final V3AnswerSheetRepository repository = mock(V3AnswerSheetRepository.class);
    private final V3AnswerSheetPdfRenderer renderer = mock(V3AnswerSheetPdfRenderer.class);
    private final V3AnswerSheetFileStorage storage = mock(V3AnswerSheetFileStorage.class);
    private final AtomicReference<String> sheetUuid = new AtomicReference<>();
    private final AtomicReference<String> manifestHash = new AtomicReference<>();
    private V3AnswerSheetService service;

    @BeforeEach
    void setUp() {
        service = new V3AnswerSheetService(repository, renderer, storage, mock(V3AuditService.class),
                Clock.fixed(GENERATED_AT, ZoneOffset.UTC));
        when(repository.findActivePaperSize("A4")).thenReturn(Optional.of(V3AnswerSheetTestFixtures.a4()));
        when(repository.findReadyVersion(anyLong(), anyInt(), anyLong(), anyString())).thenReturn(Optional.empty());
        when(repository.nextGenerationNumber(anyLong(), anyInt())).thenReturn(1);
        when(storage.store(anyString(), any())).thenReturn(STORAGE_KEY);
        when(repository.findOwnedVersion(VERSION_ID, TEACHER.userId(), TEACHER.schoolId()))
                .thenAnswer(invocation -> Optional.of(storedVersion()));
    }

    @Test
    void wipedMixedSheetIsRebuiltWithTheSamePagesAndFooter() throws Exception {
        List<StoredPage> pages = generateMixedSheet();
        when(storage.exists(STORAGE_KEY)).thenReturn(false);
        when(repository.listPages(VERSION_ID)).thenReturn(pages);

        byte[] pdf = service.getPdf(TEACHER, VERSION_ID).bytes();

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(pages.size());
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Geometry " + manifestHash.get().substring(0, 12));
        }
        verify(storage, never()).read(any());
    }

    @Test
    void rebuildIsRefusedWhenAPageNoLongerMatchesWhatWasPrinted() {
        List<StoredPage> pages = new ArrayList<>(generateMixedSheet());
        StoredPage first = pages.get(0);
        pages.set(0, new StoredPage(first.pageUuid(), first.pageNumber(), first.qrPayloadHash(),
                "f".repeat(64), first.templateCode()));
        when(storage.exists(STORAGE_KEY)).thenReturn(false);
        when(repository.listPages(VERSION_ID)).thenReturn(pages);

        assertThatThrownBy(() -> service.getPdf(TEACHER, VERSION_ID))
                .isInstanceOfSatisfying(V3AuthException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ANSWER_SHEET_PDF_NOT_FOUND"));
    }

    @Test
    void rebuildIsRefusedAfterTheAssessmentWasEdited() {
        List<StoredPage> pages = generateMixedSheet();
        AssignmentContext edited = withVersion(mixedAssignment(), 2);
        when(repository.findOwnedAssignment(5001L, TEACHER.userId(), TEACHER.schoolId()))
                .thenReturn(Optional.of(edited));
        when(storage.exists(STORAGE_KEY)).thenReturn(false);
        when(repository.listPages(VERSION_ID)).thenReturn(pages);

        assertThatThrownBy(() -> service.getPdf(TEACHER, VERSION_ID))
                .isInstanceOfSatisfying(V3AuthException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ANSWER_SHEET_PDF_NOT_FOUND"));
    }

    @Test
    void wipedValidatedTemplateSheetIsRebuiltFromTheSamePlan() {
        AssignmentContext assignment = V3AnswerSheetTestFixtures.activeAssignment();
        when(repository.lockOwnedAssignment(5001L, TEACHER.userId(), TEACHER.schoolId()))
                .thenReturn(Optional.of(assignment));
        when(repository.findOwnedAssignment(5001L, TEACHER.userId(), TEACHER.schoolId()))
                .thenReturn(Optional.of(assignment));
        when(repository.findQuestions(assignment.testId())).thenReturn(V3AnswerSheetTestFixtures.tenValidQuestions());
        when(repository.findValidatedTemplate("A4"))
                .thenReturn(Optional.of(V3AnswerSheetTestFixtures.validatedTemplate()));
        when(repository.insertGeneratingVersion(anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyString(), anyLong())).thenAnswer(invocation -> {
                    sheetUuid.set(invocation.getArgument(0));
                    manifestHash.set(invocation.getArgument(6));
                    return VERSION_ID;
                });
        when(renderer.render(any())).thenReturn("%PDF-legacy".getBytes(StandardCharsets.US_ASCII));

        service.generate(TEACHER, 5001L, "A4", null);
        ArgumentCaptor<GenerationPlan> original = ArgumentCaptor.forClass(GenerationPlan.class);
        verify(renderer).render(original.capture());
        GenerationPlan printed = original.getValue();
        when(repository.listPages(VERSION_ID)).thenReturn(List.of(new StoredPage(printed.pageUuid(), 1,
                printed.qrPayloadHash(), printed.pageGeometryHash(), V3AnswerSheetService.VALIDATED_TEMPLATE_CODE)));
        when(storage.exists(STORAGE_KEY)).thenReturn(false);

        service.getPdf(TEACHER, VERSION_ID);

        ArgumentCaptor<GenerationPlan> rebuilt = ArgumentCaptor.forClass(GenerationPlan.class);
        verify(renderer, times(2)).render(rebuilt.capture());
        GenerationPlan again = rebuilt.getAllValues().get(1);
        assertThat(again.answerSheetUuid()).isEqualTo(printed.answerSheetUuid());
        assertThat(again.pageUuid()).isEqualTo(printed.pageUuid());
        assertThat(again.qrPayload()).isEqualTo(printed.qrPayload());
        assertThat(again.pageGeometryHash()).isEqualTo(printed.pageGeometryHash());
        assertThat(again.manifestHash()).isEqualTo(printed.manifestHash());
    }

    /** Generates a five-question mixed-template sheet and returns the pages it stored. */
    private List<StoredPage> generateMixedSheet() {
        AssignmentContext assignment = mixedAssignment();
        List<Question> questions = V3AnswerSheetTestFixtures.tenValidQuestions().subList(0, 5);
        when(repository.lockOwnedAssignment(5001L, TEACHER.userId(), TEACHER.schoolId()))
                .thenReturn(Optional.of(assignment));
        when(repository.findOwnedAssignment(5001L, TEACHER.userId(), TEACHER.schoolId()))
                .thenReturn(Optional.of(assignment));
        when(repository.findQuestions(assignment.testId())).thenReturn(questions);
        when(repository.findTemplateByCode(V3AnswerSheetService.MIXED_DYNAMIC_TEMPLATE_CODE, "A4"))
                .thenReturn(Optional.of(mixedTemplate()));
        when(repository.findQuestionContents(assignment.testId())).thenReturn(contentRows(questions));
        when(repository.findQuestionOptions(assignment.testId())).thenReturn(optionRows(questions));
        when(repository.insertGeneratingVersion(anyString(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyString(), anyLong(), anyInt(), anyInt())).thenAnswer(invocation -> {
                    sheetUuid.set(invocation.getArgument(0));
                    manifestHash.set(invocation.getArgument(6));
                    return VERSION_ID;
                });

        service.generate(TEACHER, 5001L, "A4", null);

        ArgumentCaptor<String> pageUuid = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> qrHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> geometryHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> pageNumber = ArgumentCaptor.forClass(Integer.class);
        verify(repository, atLeastOnce()).insertPage(pageUuid.capture(), eq(VERSION_ID), anyLong(), anyString(),
                qrHash.capture(), geometryHash.capture(), pageNumber.capture(), anyInt());
        List<StoredPage> pages = new ArrayList<>();
        for (int index = 0; index < pageUuid.getAllValues().size(); index++) {
            pages.add(new StoredPage(pageUuid.getAllValues().get(index), pageNumber.getAllValues().get(index),
                    qrHash.getAllValues().get(index), geometryHash.getAllValues().get(index),
                    V3AnswerSheetService.MIXED_DYNAMIC_TEMPLATE_CODE));
        }
        return List.copyOf(pages);
    }

    private StoredVersion storedVersion() {
        return new StoredVersion(VERSION_ID, sheetUuid.get(), 5001L, "00000000-0000-4000-8000-000000005001", "A4",
                1, 1, 5, 1, 1, manifestHash.get(), "ready", STORAGE_KEY, "e".repeat(64), 1L,
                GENERATED_AT.truncatedTo(ChronoUnit.SECONDS));
    }

    private static AssignmentContext mixedAssignment() {
        AssignmentContext base = V3AnswerSheetTestFixtures.activeAssignment();
        return new AssignmentContext(base.testAssignmentId(), base.assignmentUuid(), base.testId(), base.testUuid(),
                base.testVersionNumber(), base.testName(), base.testStatus(), 5, base.assignmentStatus(),
                base.classAssignmentStatus(), base.gradeLevelName(), base.sectionName(), base.subjectName());
    }

    private static AssignmentContext withVersion(AssignmentContext base, int testVersionNumber) {
        return new AssignmentContext(base.testAssignmentId(), base.assignmentUuid(), base.testId(), base.testUuid(),
                testVersionNumber, base.testName(), base.testStatus(), base.totalItemsSnapshot(),
                base.assignmentStatus(), base.classAssignmentStatus(), base.gradeLevelName(), base.sectionName(),
                base.subjectName());
    }

    private static Template mixedTemplate() {
        Template base = V3AnswerSheetTestFixtures.validatedTemplate();
        return new Template(base.omrTemplateId(), V3AnswerSheetService.MIXED_DYNAMIC_TEMPLATE_CODE, base.name(),
                base.version(), base.paperSizeId(), base.paperSizeCode(), base.pageWidthPoints(),
                base.pageHeightPoints(), base.orientation(), 5, 100, base.optionCount(), base.qrPayloadVersion(),
                base.minimumScannerVersion(), base.coordinateOrigin(), base.requiredPrintScalePercent(),
                base.geometryHash(), base.regions());
    }

    private static List<QuestionContentRow> contentRows(List<Question> questions) {
        return questions.stream().map(question -> new QuestionContentRow(question.questionId(),
                question.questionUuid(), question.testPartId(), question.partOrder(), "Part I",
                "Choose the best answer.", question.partItemNumber(), question.globalItemNumber(),
                question.questionType(), "Question " + question.globalItemNumber() + "?", null, null, false, 1.0))
                .toList();
    }

    private static List<QuestionOptionRow> optionRows(List<Question> questions) {
        List<QuestionOptionRow> rows = new ArrayList<>();
        for (Question question : questions) {
            for (int order = 0; order < 4; order++) {
                String key = List.of("A", "B", "C", "D").get(order);
                rows.add(new QuestionOptionRow(question.questionId(), key, "Option " + key, order + 1));
            }
        }
        return rows;
    }
}
