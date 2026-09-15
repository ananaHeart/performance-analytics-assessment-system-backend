package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Profile("v3")
@Service
public class V3ProvisionalRegistrationCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3ProvisionalRegistrationCleanupService.class);

    private final V3RegistrationPersistenceService persistenceService;
    private final V3AuthProperties properties;
    private final Clock clock;

    @Autowired
    public V3ProvisionalRegistrationCleanupService(
            V3RegistrationPersistenceService persistenceService,
            V3AuthProperties properties
    ) {
        this(persistenceService, properties, Clock.systemUTC());
    }

    V3ProvisionalRegistrationCleanupService(
            V3RegistrationPersistenceService persistenceService,
            V3AuthProperties properties,
            Clock clock
    ) {
        this.persistenceService = persistenceService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.v3.auth.cleanup-cron:0 15 2 * * *}", zone = "UTC")
    public void purgeExpiredRegistrations() {
        if (!properties.isCleanupEnabled()) {
            return;
        }
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.getRegistrationRetention());
        List<Long> userIds = persistenceService.findExpiredProvisionalUserIds(cutoff);
        int deleted = 0;
        for (Long userId : userIds) {
            try {
                if (persistenceService.purgeExpiredProvisionalUser(userId, cutoff, now)) {
                    deleted++;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to purge expired V3 provisional registration for userId={}", userId, exception);
            }
        }
        if (deleted > 0) {
            LOGGER.info("Purged {} expired V3 provisional teacher registration(s).", deleted);
        }
    }
}
