package com.capstone.assessment.v2.sync.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.sync.dto.V2SyncDownloadResponse;
import com.capstone.assessment.v2.sync.dto.V2SyncUploadRequest;
import com.capstone.assessment.v2.sync.dto.V2SyncUploadResponse;
import com.capstone.assessment.v2.sync.service.V2SyncDownloadService;
import com.capstone.assessment.v2.sync.service.V2SyncUploadService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("v2")
@RestController
@RequestMapping("/api/v2/sync")
public class V2SyncController {

    private final V2SyncDownloadService syncDownloadService;
    private final V2SyncUploadService syncUploadService;

    public V2SyncController(
            V2SyncDownloadService syncDownloadService,
            V2SyncUploadService syncUploadService
    ) {
        this.syncDownloadService = syncDownloadService;
        this.syncUploadService = syncUploadService;
    }

    @GetMapping("/download")
    public ResponseEntity<ApiResponse<V2SyncDownloadResponse>> download(
            @AuthenticationPrincipal V2AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V2 mobile download data retrieved successfully.",
                syncDownloadService.download(principal)
        ));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<V2SyncUploadResponse>> upload(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @Valid @RequestBody V2SyncUploadRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V2 mobile upload processed successfully.",
                syncUploadService.upload(principal, request)
        ));
    }
}
