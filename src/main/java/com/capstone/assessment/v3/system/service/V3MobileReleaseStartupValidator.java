package com.capstone.assessment.v3.system.service;

import com.capstone.assessment.v3.mobile.config.V3MobileReleaseProperties;
import com.capstone.assessment.v3.system.dto.V3MobileReleaseReadinessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("v3 & v3-mobile-release")
@Component
public class V3MobileReleaseStartupValidator implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3MobileReleaseStartupValidator.class);
    private final V3MobileReleaseProperties properties;
    private final V3MobileReleaseReadinessService readinessService;

    public V3MobileReleaseStartupValidator(
            V3MobileReleaseProperties properties,
            V3MobileReleaseReadinessService readinessService
    ) {
        this.properties = properties;
        this.readinessService = readinessService;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.getRelease().isHttpEnabled()) {
            LOGGER.warn("V3 Mobile release profile is active, but write APIs remain disabled.");
            return;
        }
        V3MobileReleaseReadinessResponse response = readinessService.requireReady();
        LOGGER.info("V3 Mobile write APIs passed release preflight: contractPack={}, mode={}, databaseReady={}",
                response.contractPack(), response.mode(), response.databaseReady());
    }
}
