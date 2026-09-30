package com.capstone.assessment.v3.answersheet.service.dynamic;

import java.util.List;

/**
 * Data model for the dynamic mixed-question answer sheet (A4 / US Letter / US Legal).
 *
 * <p>This mirrors, field for field, the manifest produced by the approved reference
 * generator {@code generate_dynamic_answer_sheet.py} (OMRPrototype repo) and consumed by
 * {@code DynamicOmrDetector.kt} on Mobile. It is intentionally isolated from
 * {@code V3AnswerSheetModels} (the physically validated fixed A4 10-item MC template) so
 * that fixing this engine cannot regress the sheet the app already scans in production.
 *
 * <p>Do not add fields here without confirming they exist in the reference manifest -
 * see docs/V3_DYNAMIC_ANSWER_SHEET_SCHEMA_DELTA_HANDOFF.md for the audited field list.
 */
public final class DynamicSheetModels {

    private DynamicSheetModels() {
    }

    // ---------------------------------------------------------------------
    // Input: what the caller (assessment + assignment context) supplies.
    // ---------------------------------------------------------------------

    public record DynamicOptionInput(String key, String storedValue) {
    }

    public record DynamicQuestionInput(
            long questionId,
            String questionUuid,
            String questionType,
            Integer partItemNumber,
            Integer globalItemNumber,
            double maximumPoints,
            String questionText,
            String responseRegionSize,
            Integer expectedResponseCount,
            boolean forcePageBreakBefore,
            List<DynamicOptionInput> options
    ) {
    }

    public record DynamicPartInput(
            long testPartId,
            int partOrder,
            String partName,
            String instructions,
            List<DynamicQuestionInput> questions
    ) {
    }

    public record DynamicSheetContext(
            String schoolName,
            String assessmentName,
            String subject,
            String gradeSection,
            double totalPoints
    ) {
    }

    public record DynamicSheetRequest(
            String answerSheetUuid,
            long testAssignmentId,
            String assignmentUuid,
            String paperSizeCode,
            int testVersionNumber,
            String requiredScannerVersion,
            String generatedAt,
            List<DynamicPartInput> parts,
            DynamicSheetContext context
    ) {
    }

    // ---------------------------------------------------------------------
    // Output: the manifest/PDF geometry, matching the Mobile wire contract.
    // ---------------------------------------------------------------------

    public record Rectangle(double x, double y, double width, double height) {
    }

    public record RegistrationMarker(String markerId, String corner, String style, Rectangle rectangle) {
    }

    public record MarkerPattern(String orientationCorner, String orientationStyle, String locatorStyle) {
    }

    public record RegionOption(String key, String storedValue, double centerX, double centerY) {
    }

    /** A single question's rendered slot. Carries render-only extras (not serialized to Mobile). */
    public record DynamicRegion(
            String regionUuid,
            String templateRegionCode,
            long questionId,
            String questionUuid,
            long testPartId,
            int globalItemNumber,
            int partItemNumber,
            String questionType,
            String regionType,
            String responseRegionSize,
            Integer expectedResponseCount,
            Integer responseLineCount,
            Rectangle rectangle,
            List<RegionOption> options,
            String geometryHash,
            // render-only, excluded from the manifest wire payload:
            String questionText,
            double maximumPoints,
            String partName,
            int pageNumber
    ) {
    }

    public record PartHeader(double x, double y, double width, double height, String title, String instructions) {
    }

    public record DynamicPage(
            String pageUuid,
            int pageNumber,
            int totalPages,
            String templateCode,
            String templateVersion,
            String pageGeometryHash,
            int qrPayloadVersion,
            String qrPayload,
            String qrPayloadHash,
            String errorCorrection,
            double widthPt,
            double heightPt,
            List<RegistrationMarker> markers,
            MarkerPattern markerPattern,
            Rectangle qrRectangle,
            List<DynamicRegion> regions,
            // render-only:
            List<PartHeader> partHeaders
    ) {
    }

    public record DynamicManifest(
            String contractVersion,
            int manifestVersion,
            String designSystemCode,
            String designSystemVersion,
            String answerSheetUuid,
            long testAssignmentId,
            String assignmentUuid,
            String paperSizeCode,
            double widthPt,
            double heightPt,
            int testVersionNumber,
            int totalQuestions,
            int totalPages,
            String manifestHash,
            String requiredScannerVersion,
            String generatedAt,
            List<DynamicPage> pages,
            String canonicalManifestJson
    ) {
    }

    /** Fixed per-paper-size geometry, matching PAPER_PROFILES in the reference generator. */
    public record DynamicPaperProfile(String code, double widthPt, double heightPt, String templateCode) {
    }
}
