package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.config.V3MobileReleaseProperties;
import com.capstone.assessment.v3.mobile.dto.V3AnswerSheetManifestResponse;
import com.capstone.assessment.v3.mobile.dto.V3MobileDownloadResponse;
import com.capstone.assessment.v3.mobile.dto.V3MobileReferenceDataResponse;
import com.capstone.assessment.v3.mobile.repository.V3MobileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Profile("v3")
@Service
public class V3MobileService {

    static final String CONTRACT_VERSION = "3.0";

    private static final int MAXIMUM_QR_PAYLOAD_BYTES = 256;
    private static final int MINIMUM_ANSWER_SHEET_QUESTIONS = 5;
    private static final String SCAN_PAGE_UPLOAD_REASON =
            "Production scan upload is not enabled; durable receipts and recovery require the reviewed ledger migration "
                    + "and deployment validation before release.";

    private final V3MobileRepository repository;
    private final Clock clock;
    private final V3MobileReleaseProperties releaseProperties;

    @Autowired
    public V3MobileService(V3MobileRepository repository, V3MobileReleaseProperties releaseProperties) {
        this(repository, Clock.systemUTC(), releaseProperties);
    }

    V3MobileService(V3MobileRepository repository, Clock clock) {
        this(repository, clock, new V3MobileReleaseProperties());
    }

    V3MobileService(V3MobileRepository repository, Clock clock, V3MobileReleaseProperties releaseProperties) {
        this.repository = repository;
        this.clock = clock;
        this.releaseProperties = releaseProperties;
    }

    @Transactional(readOnly = true)
    public V3MobileReferenceDataResponse getReferenceData(V3AuthenticatedUser user) {
        requireActiveTeacher(user);

        List<V3MobileReferenceDataResponse.OmrTemplateCapability> templates = repository.findActiveTemplates()
                .stream()
                .map(template -> new V3MobileReferenceDataResponse.OmrTemplateCapability(
                        template.omrTemplateId(),
                        template.code(),
                        template.name(),
                        template.version(),
                        template.questionType(),
                        template.paperSize(),
                        template.orientation(),
                        template.minimumItemCount(),
                        template.maximumItemCount(),
                        template.optionCount(),
                        template.qrPayloadVersion(),
                        template.minimumScannerVersion(),
                        template.coordinateOrigin(),
                        template.requiredPrintScalePercent(),
                        template.geometryHash(),
                        template.physicallyValidated(),
                        repository.findTemplateRegions(template.omrTemplateId())
                ))
                .toList();

        return new V3MobileReferenceDataResponse(
                CONTRACT_VERSION,
                clock.instant(),
                "full_snapshot",
                repository.findQuestionTypes(),
                repository.findPaperSizes(),
                templates,
                statuses(),
                new V3MobileReferenceDataResponse.SyncPolicy(
                        "upsert",
                        true,
                        List.of("syncUuid", "resultUuid", "scanUuid", "scanPageUuid", "answerUuid"),
                        "replayed",
                        "IDEMPOTENCY_KEY_REUSE",
                        releaseProperties.isWriteApiEnabled(),
                        releaseProperties.isWriteApiEnabled()
                                ? "Upload is enabled for the reviewed Mobile release profile."
                                : SCAN_PAGE_UPLOAD_REASON,
                        MAXIMUM_QR_PAYLOAD_BYTES,
                        MINIMUM_ANSWER_SHEET_QUESTIONS
                )
        );
    }

    @Transactional(readOnly = true)
    public V3MobileDownloadResponse download(V3AuthenticatedUser user) {
        requireActiveTeacher(user);
        Instant generatedAt = clock.instant();

        return new V3MobileDownloadResponse(
                CONTRACT_VERSION,
                "full_snapshot",
                generatedAt,
                new V3MobileDownloadResponse.Teacher(user.userId(), user.schoolId(), user.email()),
                repository.findClassAssignments(user.userId(), user.schoolId()),
                repository.findClassAssignmentSchedules(user.userId(), user.schoolId()),
                repository.findClassLists(user.userId(), user.schoolId()),
                repository.findStudents(user.userId(), user.schoolId()),
                repository.findTermPeriods(user.userId(), user.schoolId()),
                repository.findTestAssignments(user.userId(), user.schoolId())
                        .stream()
                        .map(row -> toTestAssignment(row, generatedAt))
                        .toList(),
                repository.findTests(user.userId(), user.schoolId()),
                repository.findTestParts(user.userId(), user.schoolId()),
                repository.findQuestions(user.userId(), user.schoolId()),
                repository.findQuestionOptions(user.userId(), user.schoolId()),
                repository.findPartSkillMappings(user.userId(), user.schoolId()),
                repository.findSkills(user.userId(), user.schoolId()),
                repository.findAnswerSheets(user.userId(), user.schoolId())
        );
    }

    @Transactional(readOnly = true)
    public V3AnswerSheetManifestResponse getManifest(
            V3AuthenticatedUser user,
            String assignmentUuid,
            String answerSheetUuid
    ) {
        requireActiveTeacher(user);
        V3MobileRepository.ManifestHeaderRow header = repository.findManifestHeader(
                        assignmentUuid,
                        answerSheetUuid,
                        user.userId(),
                        user.schoolId()
                )
                .orElseThrow(() -> new V3AuthException(
                        "ANSWER_SHEET_MANIFEST_NOT_FOUND",
                        "No ready answer-sheet manifest exists for this teacher and assignment.",
                        HttpStatus.NOT_FOUND
                ));

        List<V3MobileRepository.ManifestPageRow> pageRows = repository.findManifestPages(
                header.answerSheetVersionId()
        );
        if (pageRows.size() != header.totalPages()) {
            throw new V3AuthException(
                    "ANSWER_SHEET_MANIFEST_INCOMPLETE",
                    "The stored answer-sheet manifest does not contain every expected page.",
                    HttpStatus.CONFLICT,
                    Map.of("expectedPages", header.totalPages(), "storedPages", pageRows.size())
            );
        }

        List<V3AnswerSheetManifestResponse.Page> pages = pageRows.stream()
                .map(page -> toManifestPage(header, page))
                .toList();
        String orientation = pageRows.get(0).orientation();

        if (header.manifestVersion() == 2) {
            int expectedPages = com.capstone.assessment.v3.answersheet.service.V3DynamicLayout.pages(header.totalQuestions());
            boolean valid = header.totalPages() == expectedPages
                    && pages.stream().flatMap(p -> p.regions().stream()).map(r -> r.questionUuid()).distinct().count() == header.totalQuestions();
            for (int index = 0; index < pages.size(); index++) {
                var page = pages.get(index);
                valid &= page.pageNumber() == index + 1
                        && page.regions().size() == com.capstone.assessment.v3.answersheet.service.V3DynamicLayout.pageQuestions(header.totalQuestions(), index + 1)
                        && com.capstone.assessment.v3.answersheet.service.V3DynamicLayout.CODE.equals(page.template().code())
                        && "3".equals(page.template().version()) && page.qr().payloadVersion() == 3
                        && page.qr().payload().equals(com.capstone.assessment.v3.answersheet.service.V3DynamicLayout.qr(
                            header.answerSheetUuid(), page.pageUuid(), header.assignmentUuid(), index + 1, expectedPages, page.pageGeometryHash()))
                        && page.qr().payloadHash().equals(com.capstone.assessment.v3.answersheet.service.V3DynamicLayout.sha256(page.qr().payload()));
            }
            if (!valid) throw new V3AuthException("ANSWER_SHEET_MANIFEST_INCONSISTENT",
                    "Dynamic manifest pages, QR identities and question coverage must match.", HttpStatus.CONFLICT);
        }

        return new V3AnswerSheetManifestResponse(
                CONTRACT_VERSION,
                header.manifestVersion(),
                header.answerSheetUuid(),
                new V3AnswerSheetManifestResponse.TestAssignmentIdentity(
                        header.testAssignmentId(),
                        header.assignmentUuid()
                ),
                new V3AnswerSheetManifestResponse.PaperSize(
                        header.paperSizeCode(),
                        header.widthPoints(),
                        header.heightPoints(),
                        orientation
                ),
                header.testVersionNumber(),
                header.totalQuestions(),
                header.totalPages(),
                header.manifestHash(),
                header.requiredScannerVersion(),
                header.generatedAt(),
                pages
        );
    }

    private V3MobileDownloadResponse.TestAssignment toTestAssignment(
            V3MobileRepository.TestAssignmentRow row,
            Instant now
    ) {
        CaptureAvailability availability = captureAvailability(row, now);
        return new V3MobileDownloadResponse.TestAssignment(
                row.testAssignmentId(),
                row.assignmentUuid(),
                row.testId(),
                row.classAssignmentId(),
                row.openAt(),
                row.closeAt(),
                row.assignmentStatus(),
                row.allowLateCapture(),
                availability.allowed(),
                availability.code()
        );
    }

    private CaptureAvailability captureAvailability(
            V3MobileRepository.TestAssignmentRow row,
            Instant now
    ) {
        String status = row.assignmentStatus().toLowerCase(Locale.ROOT);
        if ("archived".equals(status)) {
            return new CaptureAvailability(false, "archived");
        }
        if (row.openAt() != null && now.isBefore(row.openAt())) {
            return new CaptureAvailability(false, "scheduled");
        }
        boolean pastClose = row.closeAt() != null && now.isAfter(row.closeAt());
        if (pastClose || "closed".equals(status)) {
            return row.allowLateCapture()
                    ? new CaptureAvailability(true, "late_allowed")
                    : new CaptureAvailability(false, "closed");
        }
        if ("open".equals(status)) {
            return new CaptureAvailability(true, "open");
        }
        return new CaptureAvailability(false, "planned");
    }

    private V3AnswerSheetManifestResponse.Page toManifestPage(
            V3MobileRepository.ManifestHeaderRow header,
            V3MobileRepository.ManifestPageRow page
    ) {
        if (page.totalPages() != header.totalPages()) {
            throw new V3AuthException(
                    "ANSWER_SHEET_MANIFEST_INCONSISTENT",
                    "A stored page has a total-page count that conflicts with its manifest.",
                    HttpStatus.CONFLICT,
                    Map.of("pageNumber", page.pageNumber())
            );
        }

        List<V3AnswerSheetManifestResponse.Region> regions = repository.findManifestRegions(
                        page.answerSheetPageId()
                )
                .stream()
                .map(this::toManifestRegion)
                .toList();

        return new V3AnswerSheetManifestResponse.Page(
                page.pageUuid(),
                page.pageNumber(),
                page.totalPages(),
                new V3AnswerSheetManifestResponse.Template(
                        page.templateCode(),
                        page.templateVersion(),
                        page.templateGeometryHash()
                ),
                new V3AnswerSheetManifestResponse.Qr(
                        page.qrPayloadVersion(),
                        page.qrPayload(),
                        page.qrPayloadHash(),
                        "M"
                ),
                new V3AnswerSheetManifestResponse.CoordinateSpace(
                        "pt",
                        page.coordinateOrigin(),
                        header.widthPoints(),
                        header.heightPoints()
                ),
                regions,
                page.pageGeometryHash(),
                page.qrPayloadVersion()==3 ? repository.pageTemplateRegions(page.answerSheetPageId()) : List.of()
        );
    }

    private V3AnswerSheetManifestResponse.Region toManifestRegion(
            V3MobileRepository.ManifestRegionRow row
    ) {
        V3AnswerSheetManifestResponse.Rectangle rectangle = rectangle(row);
        return new V3AnswerSheetManifestResponse.Region(
                row.regionUuid(),
                row.templateRegionCode(),
                row.questionId(),
                row.questionUuid(),
                row.testPartId(),
                row.globalItemNumber(),
                row.partItemNumber(),
                row.questionType(),
                row.regionType(),
                row.responseRegionSize(),
                row.expectedResponseCount(),
                row.responseLineCount(),
                rectangle,
                row.geometryHash(),
                optionCoordinates(row)
        );
    }

    private V3AnswerSheetManifestResponse.Rectangle rectangle(
            V3MobileRepository.ManifestRegionRow row
    ) {
        JsonNode geometry = row.geometry();
        JsonNode rectangle = geometry.has("rectangle") ? geometry.get("rectangle") : geometry;
        return new V3AnswerSheetManifestResponse.Rectangle(
                decimalOrFallback(rectangle, List.of("x", "x_pt", "x_points"), row.fallbackX()),
                decimalOrFallback(rectangle, List.of("y", "y_pt", "y_points"), row.fallbackY()),
                decimalOrFallback(rectangle, List.of("width", "width_pt", "width_points"), row.fallbackWidth()),
                decimalOrFallback(rectangle, List.of("height", "height_pt", "height_points"), row.fallbackHeight())
        );
    }

    private List<V3AnswerSheetManifestResponse.OptionCoordinate> optionCoordinates(
            V3MobileRepository.ManifestRegionRow row
    ) {
        JsonNode geometry = row.geometry();
        JsonNode explicitOptions = geometry.get("options");
        if (explicitOptions != null && explicitOptions.isArray()) {
            List<V3AnswerSheetManifestResponse.OptionCoordinate> options = new ArrayList<>();
            for (JsonNode option : explicitOptions) {
                String key = textValue(option, "key");
                if (key == null) {
                    continue;
                }
                String storedValue = textValue(option, "storedValue");
                if (storedValue == null) {
                    storedValue = textValue(option, "stored_value");
                }
                options.add(new V3AnswerSheetManifestResponse.OptionCoordinate(
                        key,
                        storedValue == null ? objectiveStoredValue(row.questionType(), key) : storedValue,
                        decimalOrFallback(option, List.of("centerX", "center_x", "center_x_pt"), null),
                        decimalOrFallback(option, List.of("centerY", "center_y", "center_y_pt"), null)
                ));
            }
            return List.copyOf(options);
        }

        JsonNode keys = geometry.get("option_keys");
        JsonNode centers = geometry.get("bubble_centers_pt");
        if (keys == null || centers == null || !keys.isArray() || !centers.isArray()) {
            return List.of();
        }

        List<V3AnswerSheetManifestResponse.OptionCoordinate> options = new ArrayList<>();
        int count = Math.min(keys.size(), centers.size());
        for (int index = 0; index < count; index++) {
            JsonNode center = centers.get(index);
            if (!center.isArray() || center.size() < 2) {
                continue;
            }
            String key = keys.get(index).asText();
            options.add(new V3AnswerSheetManifestResponse.OptionCoordinate(
                    key,
                    objectiveStoredValue(row.questionType(), key),
                    center.get(0).decimalValue(),
                    center.get(1).decimalValue()
            ));
        }
        return List.copyOf(options);
    }

    private String objectiveStoredValue(String questionType, String displayedKey) {
        if (!"true_false".equals(questionType)) {
            return displayedKey;
        }
        return switch (displayedKey.toUpperCase(Locale.ROOT)) {
            case "T", "TRUE", "A" -> "A";
            case "F", "FALSE", "B" -> "B";
            default -> displayedKey;
        };
    }

    private BigDecimal decimalOrFallback(JsonNode node, List<String> keys, BigDecimal fallback) {
        if (node != null) {
            for (String key : keys) {
                JsonNode value = node.get(key);
                if (value != null && value.isNumber()) {
                    return value.decimalValue();
                }
            }
        }
        return fallback;
    }

    private String textValue(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
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
                    "MOBILE_TEACHER_REQUIRED",
                    "Only authenticated teachers may access the Mobile contract.",
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

    private Map<String, List<String>> statuses() {
        LinkedHashMap<String, List<String>> statuses = new LinkedHashMap<>();
        statuses.put("testAssignments", List.of("planned", "open", "closed", "archived"));
        statuses.put("scanSessions", List.of(
                "captured", "processing", "needs_verification", "accepted",
                "rescan_requested", "rejected", "superseded", "failed"
        ));
        statuses.put("omrDetections", List.of("detected", "blank", "multiple_marks", "uncertain"));
        statuses.put("studentAnswers", List.of(
                "answered", "blank", "multiple", "uncertain", "invalid", "pending_manual"
        ));
        statuses.put("answerEvaluation", List.of(
                "pending_verification", "needs_manual_scoring", "scored", "finalized"
        ));
        statuses.put("testResults", List.of("draft", "pending_verification", "finalized", "superseded"));
        statuses.put("syncs", List.of("pending", "in_progress", "partial_success", "success", "failed"));
        statuses.put("syncItems", List.of("pending", "success", "failed", "skipped"));
        return statuses;
    }

    private record CaptureAvailability(boolean allowed, String code) {
    }
}
