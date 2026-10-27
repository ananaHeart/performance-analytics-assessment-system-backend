package com.capstone.assessment.v3.account.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** REG-2 (D2): every night, teachers whose 3-year contract has ended become inactive. */
@Profile("v3")
@Component
public class V3TeacherContractScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3TeacherContractScheduler.class);

    private final V3TeacherAccountService teacherAccountService;

    public V3TeacherContractScheduler(V3TeacherAccountService teacherAccountService) {
        this.teacherAccountService = teacherAccountService;
    }

    @Scheduled(cron = "${app.v3.auth.contract-expiry-cron:0 5 0 * * *}", zone = "Asia/Manila")
    public void deactivateEndedContracts() {
        try {
            int deactivated = teacherAccountService.deactivateEndedContracts();
            if (deactivated > 0) {
                LOGGER.info("Set {} teacher account(s) to inactive because the contract ended.", deactivated);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to check ended teacher contracts.", exception);
        }
    }
}
