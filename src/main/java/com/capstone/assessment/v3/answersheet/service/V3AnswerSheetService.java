package com.capstone.assessment.v3.answersheet.service;

import com.capstone.assessment.v3.answersheet.dto.V3AnswerSheetEligibilityResponse;
import com.capstone.assessment.v3.answersheet.dto.V3AnswerSheetEligibilityResponse.Blocker;
import com.capstone.assessment.v3.answersheet.dto.V3AnswerSheetReferenceDataResponse;
import com.capstone.assessment.v3.answersheet.dto.V3AnswerSheetVersionResponse;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.AssignmentContext;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.GenerationPlan;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.PaperSize;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Question;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.StoredVersion;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.Template;
import com.capstone.assessment.v3.answersheet.model.V3AnswerSheetModels.TemplateRegion;
import com.capstone.assessment.v3.answersheet.repository.V3AnswerSheetRepository;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Profile("v3")
@Service
public class V3AnswerSheetService {

    static final int MINIMUM_QUESTION_COUNT = 5;
    static final String VALIDATED_TEMPLATE_CODE = "OMR-A4-10-MC-CTX-V2";
    static final String LEGACY_FIXED_TEMPLATE_VERSION = "2";
    private static final List<String> FIXED_OPTIONS = List.of("A", "B", "C", "D");

    private final V3AnswerSheetRepository repository;
    private final V3AnswerSheetPdfRenderer pdfRenderer;
    private final V3AnswerSheetFileStorage fileStorage;
    private final V3AuditService auditService;
    private final Clock clock;

    @Autowired
    public V3AnswerSheetService(
            V3AnswerSheetRepository repository,
            V3AnswerSheetPdfRenderer pdfRenderer,
            V3AnswerSheetFileStorage fileStorage,
            V3AuditService auditService
    ) {
        this(repository, pdfRenderer, fileStorage, auditService, Clock.systemUTC());
    }

    V3AnswerSheetService(
            V3AnswerSheetRepository repository,
            V3AnswerSheetPdfRenderer pdfRenderer,
            V3AnswerSheetFileStorage fileStorage,
            V3AuditService auditService,
            Clock clock
    ) {
        this.repository = repository;
        this.pdfRenderer = pdfRenderer;
        this.fileStorage = fileStorage;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public V3AnswerSheetReferenceDataResponse getReferenceData(V3AuthenticatedUser user) {
        requireActiveTeacher(user);
        List<V3AnswerSheetReferenceDataResponse.PaperSizeCapability> paperSizes = repository
                .findActivePaperSizes()
                .stream()
                .map(paperSize -> {
                    Template template = repository.findValidatedTemplate(paperSize.code()).orElse(null);
                    return new V3AnswerSheetReferenceDataResponse.PaperSizeCapability(
                            paperSize.code(),
                            paperSize.name(),
                            paperSize.widthPoints(),
                            paperSize.heightPoints(),
                            template != null,
                            template == null ? null : template.code(),
                            template == null ? null : template.version(),
                            template == null ? null : template.minimumScannerVersion()
                    );
                })
                .toList();
        return new V3AnswerSheetReferenceDataResponse(
                MINIMUM_QUESTION_COUNT,
                V3DynamicLayout.CODE,
                List.of("multiple_choice"),
                paperSizes,
                "Dynamic A4 A-D multiple choice; minimum 5 items. Physical scanner acceptance is separate from backend eligibility."
        );
    }

    @Transactional(readOnly = true)
    public V3AnswerSheetEligibilityResponse getEligibility(
            V3AuthenticatedUser user,
            long testAssignmentId,
            String paperSizeCode
    ) {
        requireActiveTeacher(user);
        String normalizedPaperSize = normalizePaperSize(paperSizeCode);
        AssignmentContext assignment = requireOwnedAssignment(user, testAssignmentId, false);
        return evaluate(assignment, normalizedPaperSize).response();
    }

    @Transactional
    public V3AnswerSheetVersionResponse generate(
            V3AuthenticatedUser user,
            long testAssignmentId,
            String paperSizeCode,
            V3RequestMetadata requestMetadata
    ) {
        requireActiveTeacher(user);
        String normalizedPaperSize = normalizePaperSize(paperSizeCode);
        AssignmentContext assignment = requireOwnedAssignment(user, testAssignmentId, true);
        EligibilityEvaluation evaluation = evaluate(assignment, normalizedPaperSize);
        if (!evaluation.response().eligible()) {
            throw new V3AuthException(
                    "ANSWER_SHEET_NOT_ELIGIBLE",
                    "The assessment is not eligible for the selected Bubble Answer Sheet template.",
                    HttpStatus.CONFLICT,
                    Map.of("blockers", evaluation.response().blockers())
            );
        }

        if (V3DynamicLayout.CODE.equals(evaluation.template().code())) {
            return generateDynamic(user, assignment, evaluation, requestMetadata);
        }

        String answerSheetUuid = UUID.randomUUID().toString();
        String pageUuid = UUID.randomUUID().toString();
        int generationNumber = repository.nextGenerationNumber(
                assignment.testAssignmentId(),
                evaluation.paperSize().paperSizeId()
        );
        Instant generatedAt = clock.instant();
        String qrPayload = legacyFixedQrPayload(assignment.testId());
        String qrPayloadHash = sha256(qrPayload);
        String pageGeometryHash = pageGeometryHash(
                evaluation.template(),
                evaluation.questions(),
                pageUuid
        );
        String manifestHash = manifestHash(
                assignment,
                evaluation.paperSize(),
                evaluation.template(),
                evaluation.questions(),
                answerSheetUuid,
                pageUuid,
                generationNumber,
                pageGeometryHash
        );
        GenerationPlan plan = new GenerationPlan(
                assignment,
                evaluation.paperSize(),
                evaluation.template(),
                evaluation.questions(),
                answerSheetUuid,
                pageUuid,
                generationNumber,
                qrPayload,
                qrPayloadHash,
                pageGeometryHash,
                manifestHash,
                generatedAt
        );
        byte[] pdfBytes = pdfRenderer.render(plan);
        String pdfContentHash = sha256(pdfBytes);

        long answerSheetVersionId = repository.insertGeneratingVersion(
                answerSheetUuid,
                assignment.testAssignmentId(),
                evaluation.paperSize().paperSizeId(),
                generationNumber,
                assignment.testVersionNumber(),
                evaluation.questions().size(),
                manifestHash,
                user.userId()
        );
        long answerSheetPageId = repository.insertPage(
                pageUuid,
                answerSheetVersionId,
                evaluation.template().omrTemplateId(),
                qrPayload,
                qrPayloadHash,
                pageGeometryHash
        );

        List<TemplateRegion> objectiveRegions = objectiveRegions(evaluation.template());
        for (int index = 0; index < evaluation.questions().size(); index++) {
            repository.insertObjectiveRegion(
                    UUID.randomUUID().toString(),
                    answerSheetVersionId,
                    answerSheetPageId,
                    objectiveRegions.get(index),
                    evaluation.questions().get(index)
            );
        }

        String storageKey = fileStorage.store(answerSheetUuid, pdfBytes);
        registerRollbackCleanup(storageKey);
        repository.markVersionReady(
                answerSheetVersionId,
                storageKey,
                pdfContentHash,
                pdfBytes.length,
                generatedAt
        );
        auditService.record(
                user.userId(),
                "GENERATE_ANSWER_SHEET",
                "answer_sheet_versions",
                Long.toString(answerSheetVersionId),
                "success",
                requestMetadata,
                Map.of(
                        "testAssignmentId", testAssignmentId,
                        "answerSheetUuid", answerSheetUuid,
                        "paperSizeCode", normalizedPaperSize,
                        "templateCode", evaluation.template().code(),
                        "generationNumber", generationNumber,
                        "totalQuestions", evaluation.questions().size(),
                        "totalPages", 1
                ),
                generatedAt
        );

        StoredVersion stored = repository.findOwnedVersion(
                        answerSheetVersionId,
                        user.userId(),
                        user.schoolId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "The generated answer-sheet version could not be reloaded."
                ));
        return toResponse(stored);
    }

    private V3AnswerSheetVersionResponse generateDynamic(V3AuthenticatedUser user, AssignmentContext assignment,
            EligibilityEvaluation evaluation, V3RequestMetadata metadata) {
        String sheetUuid = UUID.randomUUID().toString();
        int count = V3DynamicLayout.pages(evaluation.questions().size());
        int generation = repository.nextGenerationNumber(assignment.testAssignmentId(), evaluation.paperSize().paperSizeId());
        Instant now = clock.instant();
        List<GenerationPlan> pages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String pageUuid = UUID.randomUUID().toString();
            List<Question> questions = List.copyOf(evaluation.questions().subList(index * V3DynamicLayout.PAGE_CAPACITY,
                    Math.min((index + 1) * V3DynamicLayout.PAGE_CAPACITY, evaluation.questions().size())));
            String geometryHash = pageGeometryHash(evaluation.template(), questions, pageUuid);
            String qr = V3DynamicLayout.qr(sheetUuid, pageUuid, assignment.assignmentUuid(), index + 1, count, geometryHash);
            pages.add(new GenerationPlan(assignment, evaluation.paperSize(), evaluation.template(), questions,
                    sheetUuid, pageUuid, generation, qr, sha256(qr), geometryHash, "", now));
        }
        String manifestHash = sha256("dynamic-manifest-v2\n" + sheetUuid + "\n" + assignment.assignmentUuid()
                + "\n" + assignment.testVersionNumber() + "\n" + generation + "\n"
                + pages.stream().map(p -> p.pageUuid() + ":" + p.pageGeometryHash() + ":" + p.qrPayloadHash()).collect(Collectors.joining("\n")));
        byte[] pdf = pdfRenderer.renderPages(pages);
        long versionId = repository.insertGeneratingVersion(sheetUuid, assignment.testAssignmentId(),
                evaluation.paperSize().paperSizeId(), generation, assignment.testVersionNumber(),
                evaluation.questions().size(), manifestHash, user.userId(), count, V3DynamicLayout.MANIFEST_VERSION);
        List<TemplateRegion> slots = objectiveRegions(evaluation.template());
        for (int index = 0; index < pages.size(); index++) {
            GenerationPlan page = pages.get(index);
            long pageId = repository.insertPage(page.pageUuid(), versionId, evaluation.template().omrTemplateId(),
                    page.qrPayload(), page.qrPayloadHash(), page.pageGeometryHash(), index + 1, count);
            for (int item = 0; item < page.questions().size(); item++)
                repository.insertObjectiveRegion(UUID.randomUUID().toString(), versionId, pageId, slots.get(item), page.questions().get(item));
        }
        String storageKey = fileStorage.store(sheetUuid, pdf);
        registerRollbackCleanup(storageKey);
        repository.markVersionReady(versionId, storageKey, sha256(pdf), pdf.length, now);
        auditService.record(user.userId(), "GENERATE_ANSWER_SHEET", "answer_sheet_versions", Long.toString(versionId),
                "success", metadata, Map.of("testAssignmentId", assignment.testAssignmentId(), "answerSheetUuid", sheetUuid,
                        "templateCode", V3DynamicLayout.CODE, "totalPages", count, "totalQuestions", evaluation.questions().size()), now);
        return toResponse(repository.findOwnedVersion(versionId, user.userId(), user.schoolId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public V3AnswerSheetVersionResponse getVersion(
            V3AuthenticatedUser user,
            long answerSheetVersionId
    ) {
        requireActiveTeacher(user);
        return toResponse(requireOwnedVersion(user, answerSheetVersionId));
    }

    @Transactional(readOnly = true)
    public PdfDownload getPdf(V3AuthenticatedUser user, long answerSheetVersionId) {
        requireActiveTeacher(user);
        StoredVersion version = requireOwnedVersion(user, answerSheetVersionId);
        if (!"ready".equals(version.generationStatus()) || version.pdfStorageKey() == null) {
            throw new V3AuthException(
                    "ANSWER_SHEET_PDF_NOT_READY",
                    "The answer-sheet PDF is not ready for download.",
                    HttpStatus.CONFLICT
            );
        }
        byte[] bytes = fileStorage.read(version.pdfStorageKey());
        String actualHash = sha256(bytes);
        if (!actualHash.equals(version.pdfContentHash()) || bytes.length != version.pdfFileSizeBytes()) {
            throw new V3AuthException(
                    "ANSWER_SHEET_PDF_INTEGRITY_FAILED",
                    "The stored answer-sheet PDF failed its integrity check.",
                    HttpStatus.CONFLICT
            );
        }
        return new PdfDownload(
                "SMART-Bubble-Answer-Sheet-%d.pdf".formatted(answerSheetVersionId),
                bytes
        );
    }

    private EligibilityEvaluation evaluate(AssignmentContext assignment, String paperSizeCode) {
        PaperSize paperSize = repository.findActivePaperSize(paperSizeCode)
                .orElseThrow(() -> new V3AuthException(
                        "PAPER_SIZE_NOT_FOUND",
                        "The selected paper size is not active or does not exist.",
                        HttpStatus.BAD_REQUEST,
                        Map.of("paperSizeCode", paperSizeCode)
                ));
        List<Question> questions = repository.findQuestions(assignment.testId());
        Template template = repository.findValidatedTemplate(paperSizeCode).orElse(null);
        List<Blocker> blockers = new ArrayList<>();

        if (!"active".equalsIgnoreCase(assignment.classAssignmentStatus())) {
            blockers.add(blocker("CLASS_ASSIGNMENT_INACTIVE",
                    "The teacher's class assignment is not active."));
        }
        if (!"active".equalsIgnoreCase(assignment.testStatus())) {
            blockers.add(blocker("ASSESSMENT_NOT_ACTIVE",
                    "Only an active assessment can generate a new answer sheet."));
        }
        if (!List.of("planned", "open").contains(assignment.assignmentStatus().toLowerCase(Locale.ROOT))) {
            blockers.add(blocker("TEST_ASSIGNMENT_NOT_OPEN_FOR_GENERATION",
                    "The test assignment must be planned or open."));
        }
        if (questions.size() < MINIMUM_QUESTION_COUNT) {
            blockers.add(blocker("MINIMUM_QUESTION_COUNT_NOT_MET",
                    "At least five questions are required."));
        }
        if (assignment.totalItemsSnapshot() != questions.size()) {
            blockers.add(blocker("TEST_ITEM_SNAPSHOT_MISMATCH",
                    "The test total_items snapshot does not match the stored questions."));
        }
        addPartSnapshotBlockers(questions, blockers);

        if (template == null) {
            blockers.add(blocker("NO_PHYSICALLY_VALIDATED_TEMPLATE",
                    "No physically validated active template exists for this paper size."));
        } else {
            if (template.minimumItemCount() == null
                    || template.maximumItemCount() == null
                    || questions.size() < template.minimumItemCount()
                    || questions.size() > template.maximumItemCount()) {
                blockers.add(blocker("UNSUPPORTED_TEMPLATE_ITEM_COUNT",
                        "The assessment exceeds the selected template item capacity."));
            }
            addTemplateGeometryBlockers(template, paperSize, blockers);
        }

        for (Question question : questions) {
            if (!"multiple_choice".equals(question.questionType())) {
                blockers.add(blocker("UNSUPPORTED_QUESTION_TYPE",
                        "Current physical validation supports multiple_choice only."));
                break;
            }
        }
        for (Question question : questions) {
            if (question.activeOptionCount() != 4 || !FIXED_OPTIONS.equals(question.activeOptionKeys())) {
                blockers.add(blocker("UNSUPPORTED_QUESTION_OPTIONS",
                        "Every question must contain exactly the active options A, B, C, and D."));
                break;
            }
        }
        for (Question question : questions) {
            if (question.answerKeyCount() != 1 || question.validOptionAnswerKeyCount() != 1) {
                blockers.add(blocker("INCOMPLETE_ANSWER_KEYS",
                        "Every question must have exactly one valid option answer key."));
                break;
            }
        }
        for (Question question : questions) {
            if (question.skillMappingCount() < 1) {
                blockers.add(blocker("INCOMPLETE_SKILL_MAPPINGS",
                        "Every question must be covered by a part skill mapping."));
                break;
            }
        }

        Map<String, Integer> typeCounts = questions.stream().collect(Collectors.toMap(
                Question::questionType,
                ignored -> 1,
                Integer::sum,
                LinkedHashMap::new
        ));
        V3AnswerSheetEligibilityResponse response = new V3AnswerSheetEligibilityResponse(
                assignment.testAssignmentId(),
                assignment.assignmentUuid(),
                paperSizeCode,
                questions.size(),
                Map.copyOf(typeCounts),
                blockers.isEmpty(),
                template == null ? null : template.code(),
                template == null ? null : template.version(),
                List.copyOf(blockers)
        );
        return new EligibilityEvaluation(response, paperSize, template, questions);
    }

    private void addPartSnapshotBlockers(List<Question> questions, List<Blocker> blockers) {
        Map<Long, List<Question>> byPart = questions.stream()
                .collect(Collectors.groupingBy(Question::testPartId, LinkedHashMap::new, Collectors.toList()));
        for (List<Question> partQuestions : byPart.values()) {
            int expected = partQuestions.get(0).partItemCountSnapshot();
            if (expected != partQuestions.size()) {
                blockers.add(blocker("TEST_PART_ITEM_SNAPSHOT_MISMATCH",
                        "A test part number_of_items snapshot does not match its stored questions."));
                return;
            }
        }
    }

    private void addTemplateGeometryBlockers(
            Template template,
            PaperSize paperSize,
            List<Blocker> blockers
    ) {
        long markerCount = template.regions().stream()
                .filter(region -> "registration_marker".equals(region.type()))
                .count();
        long qrCount = template.regions().stream()
                .filter(region -> "page_identity".equals(region.type()))
                .count();
        List<TemplateRegion> objectiveRegions = objectiveRegions(template);
        boolean pageMatches = template.pageWidthPoints().compareTo(paperSize.widthPoints()) == 0
                && template.pageHeightPoints().compareTo(paperSize.heightPoints()) == 0;
        boolean regionsInsidePage = template.regions().stream().allMatch(region ->
                region.xPoints().signum() >= 0
                        && region.yPoints().signum() >= 0
                        && region.xPoints().add(region.widthPoints()).compareTo(paperSize.widthPoints()) <= 0
                        && region.yPoints().add(region.heightPoints()).compareTo(paperSize.heightPoints()) <= 0
        );
        boolean objectiveGeometryValid = objectiveRegions.stream().allMatch(this::validObjectiveGeometry);
        if (markerCount != 4
                || qrCount != 1
                || objectiveRegions.size() != (V3DynamicLayout.CODE.equals(template.code()) ? V3DynamicLayout.PAGE_CAPACITY : 10)
                || !pageMatches
                || !regionsInsidePage
                || !objectiveGeometryValid
                || template.requiredPrintScalePercent().compareTo(java.math.BigDecimal.valueOf(100)) != 0) {
            blockers.add(blocker("VALIDATED_TEMPLATE_GEOMETRY_INVALID",
                    "The stored validated-template geometry is incomplete or inconsistent."));
        }
    }

    private boolean validObjectiveGeometry(TemplateRegion region) {
        JsonNode keys = region.geometry().get("option_keys");
        JsonNode centers = region.geometry().get("bubble_centers_pt");
        JsonNode radius = region.geometry().get("bubble_radius_pt");
        if (keys == null || !keys.isArray() || keys.size() != 4
                || centers == null || !centers.isArray() || centers.size() != 4
                || radius == null || !radius.isNumber() || radius.doubleValue() <= 0) {
            return false;
        }
        for (int index = 0; index < FIXED_OPTIONS.size(); index++) {
            JsonNode center = centers.get(index);
            if (!FIXED_OPTIONS.get(index).equals(keys.get(index).asText())
                    || center == null || !center.isArray() || center.size() != 2
                    || !center.get(0).isNumber() || !center.get(1).isNumber()) {
                return false;
            }
        }
        return true;
    }

    private AssignmentContext requireOwnedAssignment(
            V3AuthenticatedUser user,
            long testAssignmentId,
            boolean lock
    ) {
        return (lock
                ? repository.lockOwnedAssignment(testAssignmentId, user.userId(), user.schoolId())
                : repository.findOwnedAssignment(testAssignmentId, user.userId(), user.schoolId()))
                .orElseThrow(() -> new V3AuthException(
                        "TEST_ASSIGNMENT_NOT_FOUND",
                        "No teacher-owned test assignment was found.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private StoredVersion requireOwnedVersion(V3AuthenticatedUser user, long answerSheetVersionId) {
        return repository.findOwnedVersion(answerSheetVersionId, user.userId(), user.schoolId())
                .orElseThrow(() -> new V3AuthException(
                        "ANSWER_SHEET_VERSION_NOT_FOUND",
                        "No teacher-owned answer-sheet version was found.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private void requireActiveTeacher(V3AuthenticatedUser user) {
        if (user == null) {
            throw new V3AuthException(
                    "AUTHENTICATION_REQUIRED",
                    "Authentication is required.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        if (!"teacher".equalsIgnoreCase(user.role())) {
            throw new V3AuthException(
                    "ANSWER_SHEET_TEACHER_REQUIRED",
                    "Only authenticated teachers may manage answer sheets.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (!"active".equalsIgnoreCase(user.status())
                || user.schoolId() == null
                || user.schoolId().isBlank()) {
            throw new V3AuthException(
                    "ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active teacher account associated with a school is required.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private V3AnswerSheetVersionResponse toResponse(StoredVersion stored) {
        return new V3AnswerSheetVersionResponse(
                stored.answerSheetVersionId(),
                stored.answerSheetUuid(),
                stored.testAssignmentId(),
                stored.assignmentUuid(),
                stored.paperSizeCode(),
                stored.generationNumber(),
                stored.testVersionNumber(),
                stored.totalQuestions(),
                stored.totalPages(),
                stored.manifestVersion(),
                stored.manifestHash(),
                stored.generationStatus(),
                stored.pdfContentHash(),
                stored.pdfFileSizeBytes(),
                stored.generatedAt(),
                "/api/v3/answer-sheet-versions/%d/pdf".formatted(stored.answerSheetVersionId())
        );
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    fileStorage.deleteQuietly(storageKey);
                }
            }
        });
    }

    private List<TemplateRegion> objectiveRegions(Template template) {
        return template.regions().stream()
                .filter(region -> "objective_bubbles".equals(region.type()))
                .sorted(Comparator.comparingInt(TemplateRegion::order))
                .toList();
    }

    private String normalizePaperSize(String value) {
        if (value == null || value.isBlank()) {
            return "A4";
        }
        return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    static String legacyFixedQrPayload(long testId) {
        return "{\"v\":2,\"tv\":\"%s\",\"t\":%d,\"q\":\"MC\",\"n\":10}"
                .formatted(VALIDATED_TEMPLATE_CODE, testId);
    }

    private String pageGeometryHash(Template template, List<Question> questions, String pageUuid) {
        String canonical = "page=" + pageUuid
                + "\ntemplate=" + template.code()
                + "\ntemplateVersion=" + template.version()
                + "\ntemplateGeometry=" + template.geometryHash()
                + "\nquestions=" + questions.stream()
                .map(Question::questionUuid)
                .collect(Collectors.joining(","))
                + "\nregions=" + objectiveRegions(template).stream()
                .map(region -> region.code() + ":" + region.geometryHash())
                .collect(Collectors.joining(","));
        return sha256(canonical);
    }

    private String manifestHash(
            AssignmentContext assignment,
            PaperSize paperSize,
            Template template,
            List<Question> questions,
            String answerSheetUuid,
            String pageUuid,
            int generationNumber,
            String pageGeometryHash
    ) {
        String canonical = "answerSheet=" + answerSheetUuid
                + "\nassignment=" + assignment.assignmentUuid()
                + "\ntestVersion=" + assignment.testVersionNumber()
                + "\npaper=" + paperSize.code()
                + "\ngeneration=" + generationNumber
                + "\npage=" + pageUuid
                + "\npageGeometry=" + pageGeometryHash
                + "\ntemplate=" + template.code()
                + "\nquestions=" + questions.stream()
                .map(Question::questionUuid)
                .collect(Collectors.joining(","));
        return sha256(canonical);
    }

    private String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private Blocker blocker(String code, String message) {
        return new Blocker(code, message);
    }

    public record PdfDownload(String filename, byte[] bytes) {
    }

    private record EligibilityEvaluation(
            V3AnswerSheetEligibilityResponse response,
            PaperSize paperSize,
            Template template,
            List<Question> questions
    ) {
    }
}
