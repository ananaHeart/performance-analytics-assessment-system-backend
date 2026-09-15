package com.capstone.assessment.v3.system.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.system.dto.V3DatabaseReadinessResponse;
import com.capstone.assessment.v3.system.service.V3DatabaseReadinessService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/system")
public class V3SystemController {

    private final V3DatabaseReadinessService readinessService;

    public V3SystemController(V3DatabaseReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping("/readiness")
    public ResponseEntity<ApiResponse<V3DatabaseReadinessResponse>> readiness() {
        V3DatabaseReadinessResponse response = readinessService.checkReadiness();
        HttpStatus status = response.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(ApiResponse.success("V3 database baseline checked.", response));
    }
}
