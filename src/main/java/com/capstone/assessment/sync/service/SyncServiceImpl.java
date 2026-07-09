package com.capstone.assessment.sync.service;

import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.sync.dto.DownloadSyncResponse;
import com.capstone.assessment.sync.dto.ItemResponseUploadDto;
import com.capstone.assessment.sync.dto.TestResultUploadDto;
import com.capstone.assessment.sync.dto.UploadSyncRequest;
import com.capstone.assessment.sync.dto.UploadSyncResponse;
import com.capstone.assessment.sync.repository.SyncRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SyncServiceImpl implements SyncService {

    private final SyncRepository syncRepository;

    public SyncServiceImpl(SyncRepository syncRepository) {
        this.syncRepository = syncRepository;
    }

    @Override
    public DownloadSyncResponse downloadForTeacher(Long teacherId) {
        validateTeacherId(teacherId);

        return new DownloadSyncResponse(
                syncRepository.findClassesByTeacherId(teacherId),
                syncRepository.findStudentsByTeacherId(teacherId),
                syncRepository.findActiveTestsByTeacherId(teacherId),
                syncRepository.findTestPartsByTeacherId(teacherId),
                syncRepository.findCompetenciesByTeacherId(teacherId),
                syncRepository.findTestResultsByTeacherId(teacherId),
                syncRepository.findItemResponsesByTeacherId(teacherId)
        );
    }

    @Override
    @Transactional
    public UploadSyncResponse uploadResults(UploadSyncRequest request) {
        validateUploadRequest(request);

        Long teacherId = request.teacherId();
        Long testId = request.testId();
        int uploadedResults = 0;
        int uploadedItems = 0;
        Map<String, Long> localToServerResultIds = new HashMap<>();

        try {
            for (TestResultUploadDto testResult : request.testResults()) {
                Optional<Long> existingResultId = syncRepository.findExistingTestResultId(testId, testResult.studentId());

                Long serverResultId;
                if (existingResultId.isPresent()) {
                    serverResultId = existingResultId.get();
                } else {
                    serverResultId = syncRepository.insertTestResult(
                            testId,
                            testResult.studentId(),
                            testResult.totalScore(),
                            testResult.rawAnswers()
                    );
                    uploadedResults++;
                }

                if (testResult.localResultId() != null && !testResult.localResultId().isBlank()) {
                    localToServerResultIds.put(testResult.localResultId(), serverResultId);
                }
            }

            List<ItemResponseUploadDto> itemResponses = request.itemResponses();
            if (itemResponses != null) {
                for (ItemResponseUploadDto itemResponse : itemResponses) {
                    Long serverResultId = localToServerResultIds.get(itemResponse.localResultId());
                    if (serverResultId == null) {
                        continue;
                    }

                    boolean itemExists = syncRepository.itemResultExists(
                            serverResultId,
                            itemResponse.testPartId(),
                            itemResponse.itemNumber()
                    );

                    if (itemExists) {
                        continue;
                    }

                    syncRepository.insertItemResult(
                            itemResponse.testPartId(),
                            serverResultId,
                            itemResponse.itemNumber(),
                            itemResponse.isCorrect()
                    );
                    uploadedItems++;
                }
            }

            syncRepository.insertSyncLog(teacherId, testId, "Success");

            return new UploadSyncResponse(
                    "Success",
                    uploadedResults,
                    uploadedItems,
                    "Sync upload completed successfully."
            );
        } catch (Exception exception) {
            syncRepository.insertSyncLog(teacherId, testId, "Failed");
            throw exception;
        }
    }

    private void validateUploadRequest(UploadSyncRequest request) {
        if (request == null) {
            throw new BadRequestException("Upload request must not be null.");
        }

        validateTeacherId(request.teacherId());

        if (request.testId() == null) {
            throw new BadRequestException("Test ID is required.");
        }

        if (request.testResults() == null || request.testResults().isEmpty()) {
            throw new BadRequestException("At least one test result is required.");
        }
    }

    private void validateTeacherId(Long teacherId) {
        if (teacherId == null) {
            throw new BadRequestException("Teacher ID is required.");
        }
    }
}
