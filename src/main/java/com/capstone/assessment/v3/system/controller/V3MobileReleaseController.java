package com.capstone.assessment.v3.system.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.system.dto.V3MobileReleaseReadinessResponse;
import com.capstone.assessment.v3.system.service.V3MobileReleaseReadinessService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/system")
public class V3MobileReleaseController {

    private final V3MobileReleaseReadinessService readinessService;

    public V3MobileReleaseController(V3MobileReleaseReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping("/mobile-release-readiness")
    public ResponseEntity<ApiResponse<V3MobileReleaseReadinessResponse>> readiness() {
        V3MobileReleaseReadinessResponse response = readinessService.checkReadiness();
        HttpStatus status = response.backendReady() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(ApiResponse.success("V3 Mobile release preflight checked.", response));
    }
}
