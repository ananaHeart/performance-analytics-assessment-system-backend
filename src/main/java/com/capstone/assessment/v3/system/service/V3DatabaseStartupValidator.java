package com.capstone.assessment.v3.system.service;

import com.capstone.assessment.v3.system.config.V3BaselineProperties;
import com.capstone.assessment.v3.system.dto.V3DatabaseReadinessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("v3")
@Component
public class V3DatabaseStartupValidator implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3DatabaseStartupValidator.class);

    private final V3DatabaseReadinessService readinessService;
    private final V3BaselineProperties properties;

    public V3DatabaseStartupValidator(
            V3DatabaseReadinessService readinessService,
            V3BaselineProperties properties
    ) {
        this.readinessService = readinessService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isValidateOnStartup()) {
            LOGGER.warn("V3 startup database baseline validation is disabled.");
            return;
        }

        V3DatabaseReadinessResponse response = readinessService.requireReady();
        LOGGER.info(
                "V3 database baseline ready: database={}, tables={}, foreignKeys={}, checks={}, uniqueConstraints={}, questionTypes={}, omrTemplates={}, ruleSets={}",
                response.databaseName(),
                response.tableCount(),
                response.foreignKeyCount(),
                response.checkConstraintCount(),
                response.uniqueConstraintCount(),
                response.activeQuestionTypeCount(),
                response.activeOmrTemplateCount(),
                response.activePerformanceRuleSetCount()
        );
    }
}
