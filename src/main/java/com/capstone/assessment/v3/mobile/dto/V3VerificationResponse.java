package com.capstone.assessment.v3.mobile.dto;

import java.util.List;
import com.capstone.assessment.v3.mobile.dto.V3DetectionUploadResponse.IdMapping;

public record V3VerificationResponse(String syncUuid, String operationUuid, String syncStatus, List<Outcome> items) {
    public record Error(String code,String message,boolean retryable) { }
    public record Outcome(String resultUuid,String status,String disposition,Long revision,List<IdMapping> idMappings,Error error) {
        public Outcome replay() { return "success".equals(status)
                ? new Outcome(resultUuid,status,"replayed",revision,idMappings,null) : this; }
    }
}
