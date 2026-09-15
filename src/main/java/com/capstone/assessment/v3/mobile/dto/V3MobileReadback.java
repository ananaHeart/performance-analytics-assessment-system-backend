package com.capstone.assessment.v3.mobile.dto;

import com.capstone.assessment.v3.mobile.dto.V3DetectionUploadResponse.IdMapping;
import com.capstone.assessment.v3.mobile.dto.V3VerificationResponse.Error;
import com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class V3MobileReadback {
    private V3MobileReadback() { }
    public record AnalyticsState(String status,Integer scoreVersion,String reasonCode) { }
    public record Metrics(BigDecimal totalScore,BigDecimal maxScore,BigDecimal percentage,
                          Object itemAnalysis,Object mastery,Object interventions,Object student360) { }
    public record Analytics(String contractVersion,String resultUuid,Integer scoreVersion,String status,String reasonCode,
                            Instant generatedAt,Metrics metrics,List<String> unavailableModules) { }
    public record Page(String scanUuid,String scanPageUuid,String pageUuid,String pageStatus,String originalAttachmentUuid) { }
    public record RubricMapping(String answerUuid,long rubricCriterionId,int scoreVersion,long centralAnswerRubricScoreId) { }
    public record Result(String contractVersion,String resultUuid,long testResultId,String assignmentUuid,long classListId,
                         long studentId,String resultStatus,long revision,int scoreVersion,List<String> pendingReasons,
                         List<IdMapping> idMappings,List<Page> pages,List<RubricMapping> rubricScoreMappings,
                         V3ScoredResultResponse officialScore,AnalyticsState analytics) { }
    public record PageOutcome(String scanPageUuid,String status,Error error) { }
    public record SyncItem(String resultUuid,String status,Error error,List<PageOutcome> pageOutcomes) { }
    public record Sync(String contractVersion,String syncUuid,String assignmentUuid,String syncStatus,List<SyncItem> items) { }
}
