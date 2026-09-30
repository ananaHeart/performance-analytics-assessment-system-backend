package com.capstone.assessment.v3.answersheet.service.dynamic;

import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicManifest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicOptionInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicPage;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicPaperProfile;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicPartInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicQuestionInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicRegion;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetRequest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.MarkerPattern;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.PartHeader;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.Rectangle;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.RegionOption;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.RegistrationMarker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Java port of {@code generate_dynamic_answer_sheet.py} (OMRPrototype repo, audited
 * 2026-09-15). Every constant and layout rule below is copied from that script, not
 * reinvented - the previous backend attempt failed Mobile acceptance precisely because
 * it built its own layout instead of following this one. Line references in comments
 * point at the Python source for anyone diffing behavior later.
 *
 * <p>Not yet wired into {@code V3AnswerSheetService} / the answer-sheet controller.
 * This class is deliberately freestanding so it can be verified against the confirmed
 * reference manifests ({@code DynamicSheetPackerTest}) before touching any code the
 * fixed A4 10-item MC template depends on.
 */
public final class DynamicSheetPacker {

    // -- contract constants (py lines 30-38) --------------------------------------------
    public static final String CONTRACT_VERSION = "3.0";
    public static final int MANIFEST_VERSION = 2;
    public static final int QR_PAYLOAD_VERSION = 3;
    public static final String TEMPLATE_VERSION = "3";
    private static final int TEMPLATE_VERSION_NUMERIC = Integer.parseInt(TEMPLATE_VERSION);
    public static final int MAX_QR_PAYLOAD_BYTES = 256;
    public static final int MAX_PAGES = 12;
    public static final int MINIMUM_OBJECTIVE_QUESTIONS = 5;
    public static final String COORDINATE_ORIGIN = "pdf_bottom_left";
    public static final String DEFAULT_SCANNER_VERSION = "3.0.0-prototype.2";

    private static final Set<String> OBJECTIVE_TYPES = Set.of("multiple_choice", "true_false");

    // -- layout constants (py lines 49-69) -------------------------------------------------
    private static final double MARKER_MARGIN = 20.0;
    private static final double MARKER_SIZE = 15.0;
    private static final double QR_SIZE = 72.0;
    private static final double QR_BOTTOM = 20.0;
    private static final double CONTENT_MARGIN_X = 42.0;
    private static final double PAGE_ONE_CONTENT_TOP_FROM_PAGE_TOP = 160.0;
    private static final double CONTINUATION_CONTENT_TOP_FROM_PAGE_TOP = 70.0;
    private static final double CONTENT_BOTTOM = 102.0;
    private static final double PART_HEADER_HEIGHT = 24.0;
    private static final double PART_HEADER_GAP = 5.0;
    private static final double REGION_GAP = 7.0;
    private static final int OBJECTIVE_COLUMNS = 2;
    private static final double OBJECTIVE_COLUMN_GAP = 18.0;
    private static final double MINIMUM_FULL_PAGE_REMAINING_HEIGHT = 220.0;
    public static final double OPTION_SPACING = 36.0;
    public static final double BUBBLE_RADIUS = 6.4;

    public static final String DESIGN_SYSTEM_CODE = "SMART-DYNAMIC-ANSWER-SHEET";
    public static final String DESIGN_SYSTEM_VERSION = "1";
    private static final String ORIENTATION_MARKER_CORNER = "top_left";
    private static final List<String> MARKER_CORNERS =
            List.of("top_left", "top_right", "bottom_right", "bottom_left");
    private static final List<String> SHARED_RULES = List.of(
            "header_structure", "typography", "bubble_geometry",
            "registration_marker_pattern", "response_region_rules");

    public static final Map<String, DynamicPaperProfile> PAPER_PROFILES = Map.of(
            "A4", new DynamicPaperProfile("A4", 595.276, 841.890, "OMR-A4-DYNAMIC-CTX-V3"),
            "US_LETTER", new DynamicPaperProfile("US_LETTER", 612.000, 792.000, "OMR-US-LETTER-DYNAMIC-CTX-V3"),
            "US_LEGAL", new DynamicPaperProfile("US_LEGAL", 612.000, 1008.000, "OMR-US-LEGAL-DYNAMIC-CTX-V3")
    );

    private DynamicSheetPacker() {
    }

    // ---------------------------------------------------------------------------------
    // question_height (py:412-425) / response_line_count (py:428-435)
    // ---------------------------------------------------------------------------------

    private static double questionHeight(DynamicQuestionInput question) {
        String type = question.questionType();
        if (type.equals("multiple_choice") || type.equals("true_false")) {
            return 34.0;
        }
        if (type.equals("identification")) {
            return 42.0;
        }
        if (type.equals("enumeration")) {
            int lineCount = question.expectedResponseCount();
            return Math.max(72.0, 38.0 + (lineCount * 22.0));
        }
        if ("full_page".equals(question.responseRegionSize())) {
            return 0.0;
        }
        return switch (question.responseRegionSize()) {
            case "short" -> 120.0;
            case "medium" -> 180.0;
            case "long" -> 220.0;
            default -> throw new IllegalArgumentException(
                    "Unsupported responseRegionSize: " + question.responseRegionSize());
        };
    }

    private static Integer responseLineCount(DynamicQuestionInput question, double height) {
        if (question.questionType().equals("identification")) {
            return 1;
        }
        if (question.questionType().equals("enumeration")) {
            return question.expectedResponseCount();
        }
        if (question.questionType().equals("essay")) {
            return Math.max(4, (int) ((height - 46.0) / 20.0));
        }
        return null;
    }

    // ---------------------------------------------------------------------------------
    // marker_rectangles / registration_markers / qr_rectangle (py:165-209)
    // ---------------------------------------------------------------------------------

    private static List<Rectangle> markerRectangles(DynamicPaperProfile profile) {
        return List.of(
                new Rectangle(MARKER_MARGIN, profile.heightPt() - MARKER_MARGIN - MARKER_SIZE, MARKER_SIZE, MARKER_SIZE),
                new Rectangle(profile.widthPt() - MARKER_MARGIN - MARKER_SIZE,
                        profile.heightPt() - MARKER_MARGIN - MARKER_SIZE, MARKER_SIZE, MARKER_SIZE),
                new Rectangle(profile.widthPt() - MARKER_MARGIN - MARKER_SIZE, MARKER_MARGIN, MARKER_SIZE, MARKER_SIZE),
                new Rectangle(MARKER_MARGIN, MARKER_MARGIN, MARKER_SIZE, MARKER_SIZE)
        );
    }

    private static List<RegistrationMarker> registrationMarkers(DynamicPaperProfile profile) {
        List<Rectangle> rectangles = markerRectangles(profile);
        List<RegistrationMarker> markers = new ArrayList<>();
        for (int i = 0; i < MARKER_CORNERS.size(); i++) {
            String corner = MARKER_CORNERS.get(i);
            String style = corner.equals(ORIENTATION_MARKER_CORNER) ? "hollow" : "solid";
            markers.add(new RegistrationMarker(corner, corner, style, rectangles.get(i)));
        }
        return markers;
    }

    private static Rectangle qrRectangle(DynamicPaperProfile profile) {
        return new Rectangle(profile.widthPt() - CONTENT_MARGIN_X - QR_SIZE, QR_BOTTOM, QR_SIZE, QR_SIZE);
    }

    // ---------------------------------------------------------------------------------
    // create_region (py:438-501)
    // ---------------------------------------------------------------------------------

    private static DynamicRegion createRegion(
            UUID answerSheetUuid,
            DynamicPaperProfile profile,
            DynamicPartInput part,
            DynamicQuestionInput question,
            int pageNumber,
            int slotNumber,
            double x,
            double y,
            double width,
            double height
    ) {
        String regionUuid = DynamicCanonicalHash.uuid5(answerSheetUuid,
                "region:%s:v%s:page:%d:slot:%d:%s".formatted(
                        profile.code(), TEMPLATE_VERSION, pageNumber, slotNumber, question.questionUuid())
        ).toString();

        List<RegionOption> options = new ArrayList<>();
        if (!question.options().isEmpty()) {
            double startX = x + 82.0;
            double centerY = y + (height / 2.0);
            for (int i = 0; i < question.options().size(); i++) {
                DynamicOptionInput option = question.options().get(i);
                options.add(new RegionOption(
                        option.key(), option.storedValue(),
                        DynamicCanonicalHash.round3(startX + (i * OPTION_SPACING)),
                        DynamicCanonicalHash.round3(centerY)
                ));
            }
        }

        String regionType = OBJECTIVE_TYPES.contains(question.questionType()) ? "objective_bubbles" : "written_response";
        Rectangle rectangle = new Rectangle(
                DynamicCanonicalHash.round3(x), DynamicCanonicalHash.round3(y),
                DynamicCanonicalHash.round3(width), DynamicCanonicalHash.round3(height));
        Integer lineCount = responseLineCount(question, height);

        Map<String, Object> publicGeometry = regionGeometryMap(
                regionUuid, slotNumber, question, part, regionType, rectangle, options, lineCount);
        String geometryHash = DynamicCanonicalHash.sha256Hex(publicGeometry);

        return new DynamicRegion(
                regionUuid,
                "DYNAMIC_SLOT_%02d".formatted(slotNumber),
                question.questionId(),
                question.questionUuid(),
                part.testPartId(),
                question.globalItemNumber(),
                question.partItemNumber(),
                question.questionType(),
                regionType,
                question.responseRegionSize() == null ? "none" : question.responseRegionSize(),
                question.expectedResponseCount(),
                lineCount,
                rectangle,
                options,
                geometryHash,
                question.questionText(),
                question.maximumPoints(),
                part.partName(),
                pageNumber
        );
    }

    private static Map<String, Object> regionGeometryMap(
            String regionUuid, int slotNumber, DynamicQuestionInput question, DynamicPartInput part,
            String regionType, Rectangle rectangle, List<RegionOption> options, Integer lineCount
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("regionUuid", regionUuid);
        map.put("templateRegionCode", "DYNAMIC_SLOT_%02d".formatted(slotNumber));
        map.put("questionId", question.questionId());
        map.put("questionUuid", question.questionUuid());
        map.put("testPartId", part.testPartId());
        map.put("globalItemNumber", question.globalItemNumber());
        map.put("partItemNumber", question.partItemNumber());
        map.put("questionType", question.questionType());
        map.put("regionType", regionType);
        map.put("responseRegionSize", question.responseRegionSize() == null ? "none" : question.responseRegionSize());
        map.put("expectedResponseCount", question.expectedResponseCount());
        map.put("responseLineCount", lineCount);
        map.put("rectangle", rectangleMap(rectangle));
        List<Object> optionMaps = new ArrayList<>();
        for (RegionOption option : options) {
            Map<String, Object> optionMap = new LinkedHashMap<>();
            optionMap.put("key", option.key());
            optionMap.put("storedValue", option.storedValue());
            optionMap.put("centerX", option.centerX());
            optionMap.put("centerY", option.centerY());
            optionMaps.add(optionMap);
        }
        map.put("options", optionMaps);
        return map;
    }

    private static Map<String, Object> rectangleMap(Rectangle rectangle) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", rectangle.x());
        map.put("y", rectangle.y());
        map.put("width", rectangle.width());
        map.put("height", rectangle.height());
        return map;
    }

    // ---------------------------------------------------------------------------------
    // plan_pages (py:504-731) - mutable page-builder state machine.
    // ---------------------------------------------------------------------------------

    private static final class PageBuilder {
        final int pageNumber;
        double cursor;
        final List<DynamicRegion> regions = new ArrayList<>();
        final List<PartHeader> partHeaders = new ArrayList<>();

        PageBuilder(int pageNumber, double cursor) {
            this.pageNumber = pageNumber;
            this.cursor = cursor;
        }
    }

    private static String laneFamily(DynamicPartInput part) {
        boolean allObjective = part.questions().stream().allMatch(q -> OBJECTIVE_TYPES.contains(q.questionType()));
        if (allObjective) {
            return "objective";
        }
        boolean allCompactWritten = part.questions().stream()
                .allMatch(q -> q.questionType().equals("identification") || q.questionType().equals("enumeration"));
        return allCompactWritten ? "compact_written" : null;
    }

    private static double laneRequiredHeight(DynamicPartInput part) {
        double sum = 0;
        for (DynamicQuestionInput question : part.questions()) {
            sum += questionHeight(question);
        }
        int gaps = Math.max(0, part.questions().size() - 1);
        return PART_HEADER_HEIGHT + PART_HEADER_GAP + sum + (gaps * REGION_GAP);
    }

    private static List<PageBuilder> planPages(DynamicSheetRequest request, DynamicPaperProfile profile, UUID answerSheetUuid) {
        double regionWidth = profile.widthPt() - (2 * CONTENT_MARGIN_X);
        double laneWidth = (regionWidth - OBJECTIVE_COLUMN_GAP) / OBJECTIVE_COLUMNS;
        List<PageBuilder> pages = new ArrayList<>();

        List<DynamicPartInput> parts = request.parts();
        newPage(pages, profile);
        int partIndex = 0;
        while (partIndex < parts.size()) {
            PageBuilder page = pages.get(pages.size() - 1);
            DynamicPartInput part = parts.get(partIndex);

            if (partIndex + 1 < parts.size()) {
                DynamicPartInput nextPart = parts.get(partIndex + 1);
                String family = laneFamily(part);
                boolean forcesBreak = part.questions().stream().anyMatch(DynamicQuestionInput::forcePageBreakBefore)
                        || nextPart.questions().stream().anyMatch(DynamicQuestionInput::forcePageBreakBefore);
                boolean canPair = family != null && family.equals(laneFamily(nextPart)) && !forcesBreak;
                if (canPair) {
                    double requiredHeight = Math.max(laneRequiredHeight(part), laneRequiredHeight(nextPart));
                    if (page.cursor - requiredHeight < CONTENT_BOTTOM) {
                        page = newPage(pages, profile);
                    }
                    double laneTop = page.cursor;
                    double leftCursor = addLanePart(page, answerSheetUuid, profile, part, CONTENT_MARGIN_X, laneTop, laneWidth);
                    double rightCursor = addLanePart(page, answerSheetUuid, profile, nextPart,
                            CONTENT_MARGIN_X + laneWidth + OBJECTIVE_COLUMN_GAP, laneTop, laneWidth);
                    page.cursor = Math.min(leftCursor, rightCursor) - PART_HEADER_GAP;
                    partIndex += 2;
                    continue;
                }
            }

            List<DynamicQuestionInput> questions = part.questions();
            double firstHeight = questionHeight(questions.get(0));
            if (firstHeight == 0.0) {
                firstHeight = 180.0;
            }
            if (questions.get(0).forcePageBreakBefore() && !page.regions.isEmpty()) {
                page = newPage(pages, profile);
            }
            if (page.cursor - PART_HEADER_HEIGHT - PART_HEADER_GAP - firstHeight < CONTENT_BOTTOM) {
                page = newPage(pages, profile);
            }
            page.cursor = addPartHeader(page, part, false, CONTENT_MARGIN_X, regionWidth, null);

            int questionIndex = 0;
            while (questionIndex < questions.size()) {
                DynamicQuestionInput question = questions.get(questionIndex);
                double requestedHeight = questionHeight(question);
                boolean forceBreak = question.forcePageBreakBefore();
                boolean fullPage = question.questionType().equals("essay") && "full_page".equals(question.responseRegionSize());

                if (forceBreak && !page.regions.isEmpty()) {
                    page = newPage(pages, profile);
                    page.cursor = addPartHeader(page, part, true, CONTENT_MARGIN_X, regionWidth, null);
                }

                if (OBJECTIVE_TYPES.contains(question.questionType())) {
                    List<DynamicQuestionInput> rowQuestions = new ArrayList<>();
                    rowQuestions.add(question);
                    int nextIndex = questionIndex + 1;
                    if (nextIndex < questions.size()
                            && OBJECTIVE_TYPES.contains(questions.get(nextIndex).questionType())
                            && !questions.get(nextIndex).forcePageBreakBefore()) {
                        rowQuestions.add(questions.get(nextIndex));
                    }
                    if (page.cursor - requestedHeight < CONTENT_BOTTOM) {
                        page = newPage(pages, profile);
                        page.cursor = addPartHeader(page, part, true, CONTENT_MARGIN_X, regionWidth, null);
                    }
                    double y = page.cursor - requestedHeight;
                    for (int columnIndex = 0; columnIndex < rowQuestions.size(); columnIndex++) {
                        double x = CONTENT_MARGIN_X + columnIndex * (laneWidth + OBJECTIVE_COLUMN_GAP);
                        page.regions.add(createRegion(answerSheetUuid, profile, part, rowQuestions.get(columnIndex),
                                page.pageNumber, page.regions.size() + 1, x, y, laneWidth, requestedHeight));
                    }
                    page.cursor = y - REGION_GAP;
                    questionIndex += rowQuestions.size();
                    continue;
                }

                if (fullPage) {
                    double remainingHeight = page.cursor - CONTENT_BOTTOM;
                    if (remainingHeight < MINIMUM_FULL_PAGE_REMAINING_HEIGHT) {
                        page = newPage(pages, profile);
                        page.cursor = addPartHeader(page, part, true, CONTENT_MARGIN_X, regionWidth, null);
                    }
                    requestedHeight = page.cursor - CONTENT_BOTTOM;
                } else if (page.cursor - requestedHeight < CONTENT_BOTTOM) {
                    page = newPage(pages, profile);
                    page.cursor = addPartHeader(page, part, true, CONTENT_MARGIN_X, regionWidth, null);
                }

                if (requestedHeight <= 0 || page.cursor - requestedHeight < CONTENT_BOTTOM - 0.01) {
                    throw new IllegalArgumentException(
                            "Question " + question.questionId() + " cannot fit on " + profile.code() + ".");
                }

                double y = page.cursor - requestedHeight;
                DynamicRegion region = createRegion(answerSheetUuid, profile, part, question,
                        page.pageNumber, page.regions.size() + 1, CONTENT_MARGIN_X, y, regionWidth, requestedHeight);
                page.regions.add(region);
                page.cursor = y - REGION_GAP;
                questionIndex += 1;
            }

            page.cursor -= PART_HEADER_GAP;
            partIndex += 1;
        }

        return pages;
    }

    private static PageBuilder newPage(List<PageBuilder> pages, DynamicPaperProfile profile) {
        if (pages.size() >= MAX_PAGES) {
            throw new IllegalArgumentException(
                    "The generated answer sheet exceeds the " + MAX_PAGES + "-page prototype limit.");
        }
        int pageNumber = pages.size() + 1;
        double topInset = pageNumber == 1 ? PAGE_ONE_CONTENT_TOP_FROM_PAGE_TOP : CONTINUATION_CONTENT_TOP_FROM_PAGE_TOP;
        PageBuilder page = new PageBuilder(pageNumber, profile.heightPt() - topInset);
        pages.add(page);
        return page;
    }

    private static double addPartHeader(
            PageBuilder page, DynamicPartInput part, boolean continued, double x, double width, Double topOverride
    ) {
        double headerTop = topOverride != null ? topOverride : page.cursor;
        double y = headerTop - PART_HEADER_HEIGHT;
        page.partHeaders.add(new PartHeader(x, y, width, PART_HEADER_HEIGHT,
                part.partName() + (continued ? " (continued)" : ""),
                part.instructions() == null ? "" : part.instructions().trim()));
        return y - PART_HEADER_GAP;
    }

    private static double addLanePart(
            PageBuilder page, UUID answerSheetUuid, DynamicPaperProfile profile,
            DynamicPartInput part, double x, double top, double laneWidth
    ) {
        double cursor = addPartHeader(page, part, false, x, laneWidth, top);
        List<DynamicQuestionInput> questions = part.questions();
        for (int i = 0; i < questions.size(); i++) {
            DynamicQuestionInput question = questions.get(i);
            double height = questionHeight(question);
            double y = cursor - height;
            page.regions.add(createRegion(answerSheetUuid, profile, part, question,
                    page.pageNumber, page.regions.size() + 1, x, y, laneWidth, height));
            cursor = y;
            if (i < questions.size() - 1) {
                cursor -= REGION_GAP;
            }
        }
        return cursor;
    }

    // ---------------------------------------------------------------------------------
    // build_manifest (py:738-875)
    // ---------------------------------------------------------------------------------

    public static DynamicManifest buildManifest(DynamicSheetRequest request) {
        DynamicPaperProfile profile = PAPER_PROFILES.get(request.paperSizeCode());
        if (profile == null) {
            throw new IllegalArgumentException("paperSize must be A4, US_LETTER, or US_LEGAL.");
        }
        UUID answerSheetUuid = UUID.fromString(request.answerSheetUuid());
        List<PageBuilder> plannedPages = planPages(request, profile, answerSheetUuid);
        int totalPages = plannedPages.size();
        List<RegistrationMarker> markers = registrationMarkers(profile);
        Rectangle qrRect = qrRectangle(profile);
        MarkerPattern markerPattern = new MarkerPattern(ORIENTATION_MARKER_CORNER, "hollow", "solid");
        String scannerVersion = request.requiredScannerVersion() == null
                ? DEFAULT_SCANNER_VERSION : request.requiredScannerVersion();

        List<DynamicPage> pages = new ArrayList<>();
        int totalQuestions = 0;
        for (PageBuilder planned : plannedPages) {
            totalQuestions += planned.regions.size();
        }

        for (PageBuilder planned : plannedPages) {
            String pageUuid = DynamicCanonicalHash.uuid5(answerSheetUuid,
                    "page:%s:v%s:%d".formatted(profile.code(), TEMPLATE_VERSION, planned.pageNumber)).toString();

            Map<String, Object> pageGeometry = pageGeometryMap(profile, markers, markerPattern, qrRect, planned.regions);
            String pageGeometryHash = DynamicCanonicalHash.sha256Hex(pageGeometry);
            String qrGeometryHashB64 = DynamicCanonicalHash.sha256Base64Url(pageGeometry);

            Map<String, Object> qrPayloadMap = new LinkedHashMap<>();
            qrPayloadMap.put("v", QR_PAYLOAD_VERSION);
            qrPayloadMap.put("as", DynamicCanonicalHash.uuidToBase64Url(answerSheetUuid.toString()));
            qrPayloadMap.put("pg", DynamicCanonicalHash.uuidToBase64Url(pageUuid));
            qrPayloadMap.put("ta", DynamicCanonicalHash.uuidToBase64Url(request.assignmentUuid()));
            qrPayloadMap.put("pn", planned.pageNumber);
            qrPayloadMap.put("pc", totalPages);
            qrPayloadMap.put("tc", profile.templateCode());
            qrPayloadMap.put("tv", TEMPLATE_VERSION_NUMERIC); // numeric, per confirmed contract
            qrPayloadMap.put("gh", qrGeometryHashB64);
            String qrPayloadText = DynamicCanonicalHash.canonicalJson(qrPayloadMap);
            int payloadBytes = qrPayloadText.getBytes(StandardCharsets.UTF_8).length;
            if (payloadBytes > MAX_QR_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(
                        "Page " + planned.pageNumber + " QR payload is " + payloadBytes
                                + " bytes; maximum is " + MAX_QR_PAYLOAD_BYTES + ".");
            }
            String qrPayloadHash = DynamicCanonicalHash.sha256HexOfText(qrPayloadText);

            pages.add(new DynamicPage(
                    pageUuid, planned.pageNumber, totalPages,
                    profile.templateCode(), TEMPLATE_VERSION, pageGeometryHash,
                    QR_PAYLOAD_VERSION, qrPayloadText, qrPayloadHash, "M",
                    profile.widthPt(), profile.heightPt(),
                    markers, markerPattern, qrRect,
                    planned.regions, planned.partHeaders
            ));
        }

        Map<String, Object> manifestForHash = manifestGeometryMap(
                profile, request, totalQuestions, totalPages, scannerVersion, markers, markerPattern, qrRect, pages);
        String manifestHash = DynamicCanonicalHash.sha256Hex(manifestForHash);

        return new DynamicManifest(
                CONTRACT_VERSION, MANIFEST_VERSION, DESIGN_SYSTEM_CODE, DESIGN_SYSTEM_VERSION,
                answerSheetUuid.toString(), request.testAssignmentId(), request.assignmentUuid(),
                profile.code(), profile.widthPt(), profile.heightPt(),
                request.testVersionNumber(), totalQuestions, totalPages, manifestHash,
                scannerVersion, request.generatedAt(), pages,
                DynamicCanonicalHash.canonicalJson(manifestForHash)
        );
    }

    private static Map<String, Object> pageGeometryMap(
            DynamicPaperProfile profile, List<RegistrationMarker> markers, MarkerPattern pattern,
            Rectangle qrRect, List<DynamicRegion> regions
    ) {
        Map<String, Object> designSystem = new LinkedHashMap<>();
        designSystem.put("code", DESIGN_SYSTEM_CODE);
        designSystem.put("version", DESIGN_SYSTEM_VERSION);
        designSystem.put("nativePaperGeometry", true);
        designSystem.put("sharedRules", SHARED_RULES);

        Map<String, Object> coordinateSpace = new LinkedHashMap<>();
        coordinateSpace.put("unit", "pt");
        coordinateSpace.put("origin", COORDINATE_ORIGIN);
        coordinateSpace.put("width", profile.widthPt());
        coordinateSpace.put("height", profile.heightPt());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("designSystem", designSystem);
        map.put("templateCode", profile.templateCode());
        map.put("templateVersion", TEMPLATE_VERSION);
        map.put("paperSize", profile.code());
        map.put("coordinateSpace", coordinateSpace);
        map.put("registrationMarkers", markerMaps(markers));
        map.put("registrationMarkerPattern", markerPatternMap(pattern));
        map.put("qrRectangle", rectangleMap(qrRect));
        List<Object> regionMaps = new ArrayList<>();
        for (DynamicRegion region : regions) {
            regionMaps.add(regionPublicMap(region));
        }
        map.put("regions", regionMaps);
        return map;
    }

    private static Map<String, Object> manifestGeometryMap(
            DynamicPaperProfile profile, DynamicSheetRequest request, int totalQuestions, int totalPages,
            String scannerVersion, List<RegistrationMarker> markers, MarkerPattern pattern, Rectangle qrRect,
            List<DynamicPage> pages
    ) {
        Map<String, Object> designSystem = new LinkedHashMap<>();
        designSystem.put("code", DESIGN_SYSTEM_CODE);
        designSystem.put("version", DESIGN_SYSTEM_VERSION);
        designSystem.put("nativePaperGeometry", true);
        designSystem.put("sharedRules", SHARED_RULES);

        Map<String, Object> testAssignment = new LinkedHashMap<>();
        testAssignment.put("testAssignmentId", request.testAssignmentId());
        testAssignment.put("assignmentUuid", request.assignmentUuid());

        Map<String, Object> paperSize = new LinkedHashMap<>();
        paperSize.put("code", profile.code());
        paperSize.put("widthPt", profile.widthPt());
        paperSize.put("heightPt", profile.heightPt());
        paperSize.put("orientation", "portrait");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("contractVersion", CONTRACT_VERSION);
        map.put("manifestVersion", MANIFEST_VERSION);
        map.put("designSystem", designSystem);
        map.put("answerSheetUuid", request.answerSheetUuid());
        map.put("testAssignment", testAssignment);
        map.put("paperSize", paperSize);
        map.put("testVersionNumber", request.testVersionNumber());
        map.put("totalQuestions", totalQuestions);
        map.put("totalPages", totalPages);
        map.put("requiredScannerVersion", scannerVersion);
        map.put("generatedAt", request.generatedAt());

        List<Object> pageMaps = new ArrayList<>();
        for (DynamicPage page : pages) {
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("code", page.templateCode());
            template.put("version", page.templateVersion());
            template.put("geometryHash", page.pageGeometryHash());

            Map<String, Object> qr = new LinkedHashMap<>();
            qr.put("payloadVersion", page.qrPayloadVersion());
            qr.put("payload", page.qrPayload());
            qr.put("payloadHash", page.qrPayloadHash());
            qr.put("errorCorrection", page.errorCorrection());

            Map<String, Object> coordinateSpace = new LinkedHashMap<>();
            coordinateSpace.put("unit", "pt");
            coordinateSpace.put("origin", COORDINATE_ORIGIN);
            coordinateSpace.put("width", page.widthPt());
            coordinateSpace.put("height", page.heightPt());

            Map<String, Object> pageMap = new LinkedHashMap<>();
            pageMap.put("pageUuid", page.pageUuid());
            pageMap.put("pageNumber", page.pageNumber());
            pageMap.put("totalPages", page.totalPages());
            pageMap.put("template", template);
            pageMap.put("qr", qr);
            pageMap.put("coordinateSpace", coordinateSpace);
            pageMap.put("registrationMarkers", markerMaps(markers));
            pageMap.put("registrationMarkerPattern", markerPatternMap(pattern));
            pageMap.put("qrRectangle", rectangleMap(qrRect));
            List<Object> regionMaps = new ArrayList<>();
            for (DynamicRegion region : page.regions()) {
                regionMaps.add(regionPublicMap(region));
            }
            pageMap.put("regions", regionMaps);
            pageMaps.add(pageMap);
        }
        map.put("pages", pageMaps);
        return map;
    }

    private static Map<String, Object> regionPublicMap(DynamicRegion region) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("regionUuid", region.regionUuid());
        map.put("templateRegionCode", region.templateRegionCode());
        map.put("questionId", region.questionId());
        map.put("questionUuid", region.questionUuid());
        map.put("testPartId", region.testPartId());
        map.put("globalItemNumber", region.globalItemNumber());
        map.put("partItemNumber", region.partItemNumber());
        map.put("questionType", region.questionType());
        map.put("regionType", region.regionType());
        map.put("responseRegionSize", region.responseRegionSize());
        map.put("expectedResponseCount", region.expectedResponseCount());
        map.put("responseLineCount", region.responseLineCount());
        map.put("rectangle", rectangleMap(region.rectangle()));
        List<Object> optionMaps = new ArrayList<>();
        for (RegionOption option : region.options()) {
            Map<String, Object> optionMap = new LinkedHashMap<>();
            optionMap.put("key", option.key());
            optionMap.put("storedValue", option.storedValue());
            optionMap.put("centerX", option.centerX());
            optionMap.put("centerY", option.centerY());
            optionMaps.add(optionMap);
        }
        map.put("options", optionMaps);
        map.put("geometryHash", region.geometryHash());
        return map;
    }

    private static List<Object> markerMaps(List<RegistrationMarker> markers) {
        List<Object> list = new ArrayList<>();
        for (RegistrationMarker marker : markers) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("markerId", marker.markerId());
            map.put("corner", marker.corner());
            map.put("style", marker.style());
            map.put("rectangle", rectangleMap(marker.rectangle()));
            list.add(map);
        }
        return list;
    }

    private static Map<String, Object> markerPatternMap(MarkerPattern pattern) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orientationCorner", pattern.orientationCorner());
        map.put("orientationStyle", pattern.orientationStyle());
        map.put("locatorStyle", pattern.locatorStyle());
        return map;
    }
}
