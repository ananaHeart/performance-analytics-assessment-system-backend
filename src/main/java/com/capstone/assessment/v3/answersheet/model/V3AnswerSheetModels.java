package com.capstone.assessment.v3.answersheet.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class V3AnswerSheetModels {

    private V3AnswerSheetModels() {
    }

    public record PaperSize(
            int paperSizeId,
            String code,
            String name,
            BigDecimal widthPoints,
            BigDecimal heightPoints,
            boolean active
    ) {
    }

    public record AssignmentContext(
            long testAssignmentId,
            String assignmentUuid,
            long testId,
            String testUuid,
            int testVersionNumber,
            String testName,
            String testStatus,
            int totalItemsSnapshot,
            String assignmentStatus,
            String classAssignmentStatus,
            String gradeLevelName,
            String sectionName,
            String subjectName
    ) {
    }

    public record Question(
            long questionId,
            String questionUuid,
            long testPartId,
            int partOrder,
            int partItemCountSnapshot,
            int partItemNumber,
            int globalItemNumber,
            int questionTypeId,
            String questionType,
            int activeOptionCount,
            List<String> activeOptionKeys,
            int answerKeyCount,
            int validOptionAnswerKeyCount,
            int skillMappingCount
    ) {
    }

    public record Template(
            long omrTemplateId,
            String code,
            String name,
            String version,
            int paperSizeId,
            String paperSizeCode,
            BigDecimal pageWidthPoints,
            BigDecimal pageHeightPoints,
            String orientation,
            Integer minimumItemCount,
            Integer maximumItemCount,
            Integer optionCount,
            int qrPayloadVersion,
            String minimumScannerVersion,
            String coordinateOrigin,
            BigDecimal requiredPrintScalePercent,
            String geometryHash,
            List<TemplateRegion> regions
    ) {
    }

    public record TemplateRegion(
            long omrTemplateRegionId,
            String code,
            int order,
            String type,
            Integer questionTypeId,
            String questionType,
            String layoutVariant,
            String responseRegionSize,
            BigDecimal xPoints,
            BigDecimal yPoints,
            BigDecimal widthPoints,
            BigDecimal heightPoints,
            JsonNode geometry,
            String geometryJson,
            String geometryHash,
            boolean required
    ) {
    }

    public record GenerationPlan(
            AssignmentContext assignment,
            PaperSize paperSize,
            Template template,
            List<Question> questions,
            String answerSheetUuid,
            String pageUuid,
            int generationNumber,
            String qrPayload,
            String qrPayloadHash,
            String pageGeometryHash,
            String manifestHash,
            Instant generatedAt
    ) {
    }

    public record StoredVersion(
            long answerSheetVersionId,
            String answerSheetUuid,
            long testAssignmentId,
            String assignmentUuid,
            String paperSizeCode,
            int generationNumber,
            int testVersionNumber,
            int totalQuestions,
            int totalPages,
            int manifestVersion,
            String manifestHash,
            String generationStatus,
            String pdfStorageKey,
            String pdfContentHash,
            long pdfFileSizeBytes,
            Instant generatedAt
    ) {
    }
}
