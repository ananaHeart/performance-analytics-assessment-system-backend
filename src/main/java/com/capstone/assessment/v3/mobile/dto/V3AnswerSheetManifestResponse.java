package com.capstone.assessment.v3.mobile.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record V3AnswerSheetManifestResponse(
        String contractVersion,
        int manifestVersion,
        String answerSheetUuid,
        TestAssignmentIdentity testAssignment,
        PaperSize paperSize,
        int testVersionNumber,
        int totalQuestions,
        int totalPages,
        String manifestHash,
        String requiredScannerVersion,
        Instant generatedAt,
        List<Page> pages
) {

    public V3AnswerSheetManifestResponse {
        pages = List.copyOf(pages);
    }

    public record TestAssignmentIdentity(long testAssignmentId, String assignmentUuid) {
    }

    public record PaperSize(
            String code,
            BigDecimal widthPt,
            BigDecimal heightPt,
            String orientation
    ) {
    }

    public record Page(
            String pageUuid,
            int pageNumber,
            int totalPages,
            Template template,
            Qr qr,
            CoordinateSpace coordinateSpace,
            List<Region> regions,
            String pageGeometryHash,
            List<V3MobileReferenceDataResponse.TemplateRegion> templateRegions
    ) {
        public Page(String pageUuid,int pageNumber,int totalPages,Template template,Qr qr,CoordinateSpace coordinateSpace,List<Region> regions) {
            this(pageUuid,pageNumber,totalPages,template,qr,coordinateSpace,regions,null,List.of());
        }
        public Page {
            regions = List.copyOf(regions);
            templateRegions = List.copyOf(templateRegions);
        }
    }

    public record Template(String code, String version, String geometryHash) {
    }

    public record Qr(int payloadVersion, String payload, String payloadHash, String errorCorrection) {
    }

    public record CoordinateSpace(
            String unit,
            String origin,
            BigDecimal width,
            BigDecimal height
    ) {
    }

    public record Region(
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
            String geometryHash,
            List<OptionCoordinate> options
    ) {
        public Region {
            options = List.copyOf(options);
        }
    }

    public record Rectangle(
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height
    ) {
    }

    public record OptionCoordinate(
            String key,
            String storedValue,
            BigDecimal centerX,
            BigDecimal centerY
    ) {
    }
}
