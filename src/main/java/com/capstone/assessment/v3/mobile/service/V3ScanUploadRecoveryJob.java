package com.capstone.assessment.v3.mobile.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("v3")
public class V3ScanUploadRecoveryJob {
    private static final Logger LOG = LoggerFactory.getLogger(V3ScanUploadRecoveryJob.class);
    private final V3ScanPageIngestionService ingestion;
    private final boolean enabled;

    public V3ScanUploadRecoveryJob(V3ScanPageIngestionService ingestion,
            @Value("${app.v3.mobile.scan-recovery-enabled:false}") boolean enabled) {
        this.ingestion = ingestion;
        this.enabled = enabled;
    }

    /** Disabled until the ledger migration and deployment storage have been validated. */
    @Scheduled(fixedDelayString = "${app.v3.mobile.scan-recovery-delay-ms:60000}")
    public void recover() {
        if (!enabled) return;
        try {
            var outcomes = ingestion.recoverPending(20);
            long recovered = outcomes.stream().filter(row -> "recovered".equals(row.status())).count();
            if (!outcomes.isEmpty()) LOG.info("Scan recovery: {} recovered, {} require another attempt or review.",
                    recovered, outcomes.size() - recovered);
        } catch (RuntimeException e) {
            LOG.warn("Scan recovery could not finish; durable upload intents remain pending.");
        }
    }
}
