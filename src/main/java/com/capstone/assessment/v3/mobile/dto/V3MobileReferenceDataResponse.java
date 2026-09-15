package com.capstone.assessment.v3.mobile.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record V3MobileReferenceDataResponse(
        String contractVersion,
        Instant serverTime,
        String downloadMode,
        List<QuestionTypeCapability> questionTypes,
        List<PaperSizeCapability> paperSizes,
        List<OmrTemplateCapability> omrTemplates,
        Map<String, List<String>> statuses,
        SyncPolicy syncPolicy
) {

    public V3MobileReferenceDataResponse {
        questionTypes = List.copyOf(questionTypes);
        paperSizes = List.copyOf(paperSizes);
        omrTemplates = List.copyOf(omrTemplates);
        LinkedHashMap<String, List<String>> copiedStatuses = new LinkedHashMap<>();
        statuses.forEach((key, values) -> copiedStatuses.put(key, List.copyOf(values)));
        statuses = Map.copyOf(copiedStatuses);
    }

    public record QuestionTypeCapability(
            int questionTypeId,
            String code,
            String name,
            String captureMode,
            String scoringMode,
            boolean supportsOmr,
            boolean supportsOcr,
            boolean supportsMultipleResponse,
            boolean requiresAttachment,
            boolean requiresTeacherVerification,
            boolean allowsTeacherAnswerEdit
    ) {
    }

    public record PaperSizeCapability(
            int paperSizeId,
            String code,
            String name,
            BigDecimal widthPt,
            BigDecimal heightPt,
            boolean operationallySupported
    ) {
    }

    public record OmrTemplateCapability(
            long omrTemplateId,
            String code,
            String name,
            String version,
            String questionType,
            String paperSize,
            String orientation,
            Integer minimumItemCount,
            Integer maximumItemCount,
            Integer optionCount,
            int qrPayloadVersion,
            String minimumScannerVersion,
            String coordinateOrigin,
            BigDecimal requiredPrintScalePercent,
            String geometryHash,
            boolean physicallyValidated,
            List<TemplateRegion> regions
    ) {
        public OmrTemplateCapability {
            regions = List.copyOf(regions);
        }
    }

    public record TemplateRegion(
            String regionUuid,
            String regionCode,
            int regionOrder,
            String regionType,
            String questionType,
            String layoutVariant,
            String responseRegionSize,
            Rectangle rectangle,
            JsonNode geometry,
            String geometryHash,
            boolean required
    ) {
    }

    public record Rectangle(
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height
    ) {
    }

    public record SyncPolicy(
            String syncAction,
            boolean oneAssignmentPerSync,
            List<String> stableUuids,
            String identicalRetryStatus,
            String changedPayloadConflictCode,
            boolean scanPageUploadAvailable,
            String scanPageUploadAvailabilityReason,
            int maximumQrPayloadBytes,
            int minimumAnswerSheetQuestions
    ) {
        public SyncPolicy {
            stableUuids = List.copyOf(stableUuids);
        }
    }
}
