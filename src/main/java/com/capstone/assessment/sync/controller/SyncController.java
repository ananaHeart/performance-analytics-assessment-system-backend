package com.capstone.assessment.sync.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.sync.dto.DownloadSyncResponse;
import com.capstone.assessment.sync.dto.UploadSyncRequest;
import com.capstone.assessment.sync.dto.UploadSyncResponse;
import com.capstone.assessment.sync.service.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/download/{teacherId}")
    public ApiResponse<DownloadSyncResponse> downloadForTeacher(@PathVariable Long teacherId) {
        DownloadSyncResponse response = syncService.downloadForTeacher(teacherId);
        return ApiResponse.success("Download sync data retrieved successfully.", response);
    }

    @PostMapping("/upload")
    public ApiResponse<UploadSyncResponse> uploadResults(@RequestBody UploadSyncRequest request) {
        UploadSyncResponse response = syncService.uploadResults(request);
        return ApiResponse.success("Upload sync completed successfully.", response);
    }
}
