package com.capstone.assessment.v3.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record V3ConsolidatedReportResponse(
        String reportType,
        Instant generatedAt,
        String groupedBy,
        String dataStatus,
        List<V3AssessmentResultsReportResponse.ReportWarning> warnings,
        List<GroupRow> groups,
        // Weighted over every finalized result in scope (earned / possible points), not an
        // average of the group means; null when nothing is finalized yet.
        BigDecimal overallMeanPercentage
) {

    public V3ConsolidatedReportResponse {
        warnings = List.copyOf(warnings);
        groups = List.copyOf(groups);
    }

    public record GroupRow(
            String groupKey,
            String groupLabel,
            int studentCount,
            BigDecimal meanPercentage,
            List<MasteryStatusCount> masteryStatusCounts,
            List<LeastMasteredSkill> leastMasteredSkills
    ) {
        public GroupRow {
            masteryStatusCounts = List.copyOf(masteryStatusCounts);
            leastMasteredSkills = List.copyOf(leastMasteredSkills);
        }
    }

    public record MasteryStatusCount(String performanceStatus, int count) {
    }

    public record LeastMasteredSkill(
            long skillId,
            String skillName,
            BigDecimal masteryPercentage,
            String masteryStatusCode
    ) {
    }
}
