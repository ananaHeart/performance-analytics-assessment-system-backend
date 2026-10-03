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

import java.util.Map;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/system")
public class V3SystemController {

    private final V3DatabaseReadinessService readinessService;

    public V3SystemController(V3DatabaseReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    /**
     * Keep-alive target for an external pinger: answers without touching the database, so pinging
     * every few minutes costs no TiDB request units (readiness runs the full schema check).
     */
    @GetMapping("/ping")
    public ResponseEntity<ApiResponse<Map<String, String>>> ping() {
        return ResponseEntity.ok(ApiResponse.success("V3 backend is awake.", Map.of("status", "up")));
    }

    @GetMapping("/readiness")
    public ResponseEntity<ApiResponse<V3DatabaseReadinessResponse>> readiness() {
        V3DatabaseReadinessResponse response = readinessService.checkReadiness();
        HttpStatus status = response.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(ApiResponse.success("V3 database baseline checked.", response));
    }
}
