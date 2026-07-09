package com.capstone.assessment.sync.service;

import com.capstone.assessment.sync.dto.DownloadSyncResponse;
import com.capstone.assessment.sync.dto.UploadSyncRequest;
import com.capstone.assessment.sync.dto.UploadSyncResponse;

public interface SyncService {

    DownloadSyncResponse downloadForTeacher(Long teacherId);

    UploadSyncResponse uploadResults(UploadSyncRequest request);
}
