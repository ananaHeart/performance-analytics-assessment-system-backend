package com.capstone.assessment.v3.account.service;

import com.capstone.assessment.v3.school.service.V3AcademicCalendarService;
import com.capstone.assessment.v3.school.service.V3SchoolSetupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly lifecycle jobs (Philippine time):
 * REG-2 ended contracts become inactive; TCH-8 teachers archived 10 years ago are anonymized;
 * CLS-6 class assignments given 3 years ago are archived; SET-5 ended school years become
 * Completed and principals are told when one is ready to archive.
 */
@Profile("v3")
@Component
public class V3TeacherContractScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3TeacherContractScheduler.class);

    private final V3TeacherAccountService teacherAccountService;
    private final V3SchoolSetupService schoolSetupService;
    private final V3AcademicCalendarService academicCalendarService;

    public V3TeacherContractScheduler(V3TeacherAccountService teacherAccountService,
                                      V3SchoolSetupService schoolSetupService,
                                      V3AcademicCalendarService academicCalendarService) {
        this.teacherAccountService = teacherAccountService;
        this.schoolSetupService = schoolSetupService;
        this.academicCalendarService = academicCalendarService;
    }

    @Scheduled(cron = "${app.v3.auth.contract-expiry-cron:0 5 0 * * *}", zone = "Asia/Manila")
    public void deactivateEndedContracts() {
        run("Set {} teacher account(s) to inactive because the contract ended.",
                teacherAccountService::deactivateEndedContracts);
        run("Erased the personal details of {} teacher(s) archived for 10 years.",
                teacherAccountService::anonymizeLongArchivedTeachers);
        run("Archived {} class assignment(s) given more than 3 years ago.",
                schoolSetupService::autoArchiveOldAssignments);
        run("Completed {} school year(s) that ended.", academicCalendarService::runYearLifecycle);
    }

    private static void run(String message, java.util.function.IntSupplier job) {
        try {
            int count = job.getAsInt();
            if (count > 0) {
                LOGGER.info(message, count);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("A nightly lifecycle job failed.", exception);
        }
    }
}
