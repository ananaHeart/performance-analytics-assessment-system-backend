package com.capstone.assessment.v3.scoring.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse;
import com.capstone.assessment.v3.scoring.service.V3ScoringService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/scoring/results")
public class V3ScoringController {

    private final V3ScoringService scoringService;

    public V3ScoringController(V3ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @PostMapping("/{testResultId}/finalize")
    public ResponseEntity<ApiResponse<V3ScoredResultResponse>> finalizeResult(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testResultId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 assessment result scored and finalized successfully.",
                scoringService.finalizeResult(user, testResultId, requestMetadata(httpRequest))
        ));
    }

    private V3RequestMetadata requestMetadata(HttpServletRequest request) {
        return new V3RequestMetadata(
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("X-Device-Identifier")
        );
    }
}
