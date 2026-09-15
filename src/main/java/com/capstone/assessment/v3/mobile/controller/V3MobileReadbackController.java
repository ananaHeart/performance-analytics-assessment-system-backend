package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3MobileReadback.*;
import com.capstone.assessment.v3.mobile.service.V3MobileReadbackService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("v3")
@RequestMapping("/api/v3/mobile")
public class V3MobileReadbackController {
    private final V3MobileReadbackService service;
    public V3MobileReadbackController(V3MobileReadbackService service) { this.service=service; }
    @GetMapping("/results/{resultUuid}")
    public ResponseEntity<ApiResponse<Result>> result(@AuthenticationPrincipal V3AuthenticatedUser user,@PathVariable String resultUuid) {
        return response(service.result(user,resultUuid));
    }
    @GetMapping("/results/{resultUuid}/analytics")
    public ResponseEntity<ApiResponse<Analytics>> analytics(@AuthenticationPrincipal V3AuthenticatedUser user,@PathVariable String resultUuid) {
        return response(service.analytics(user,resultUuid));
    }
    @GetMapping("/syncs/{syncUuid}")
    public ResponseEntity<ApiResponse<Sync>> sync(@AuthenticationPrincipal V3AuthenticatedUser user,@PathVariable String syncUuid) {
        return response(service.sync(user,syncUuid));
    }
    private <T> ResponseEntity<ApiResponse<T>> response(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("Owned Mobile state retrieved successfully.",data));
    }
}
