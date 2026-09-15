package com.capstone.assessment.v3.school.service;

import com.capstone.assessment.v3.school.model.V3AcademicCalendarModels.AcademicYearRow;
import com.capstone.assessment.v3.school.repository.V3AcademicCalendarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Profile("v3")
@Component
@ConditionalOnProperty(
        name = "app.v3.calendar.automation-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class V3AcademicCalendarAutomationScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(V3AcademicCalendarAutomationScheduler.class);
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Manila");

    private final V3AcademicCalendarRepository repository;
    private final V3AcademicCalendarAutomationService automationService;
    private final Clock clock;

    @Autowired
    public V3AcademicCalendarAutomationScheduler(
            V3AcademicCalendarRepository repository,
            V3AcademicCalendarAutomationService automationService
    ) {
        this(repository, automationService, Clock.systemUTC());
    }

    V3AcademicCalendarAutomationScheduler(
            V3AcademicCalendarRepository repository,
            V3AcademicCalendarAutomationService automationService,
            Clock clock
    ) {
        this.repository = repository;
        this.automationService = automationService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.v3.calendar.automation-cron:0 * * * * *}", zone = "UTC")
    public void reconcileDueCalendars() {
        Instant now = clock.instant();
        for (AcademicYearRow year : repository.listAutomaticTransitionCandidates(
                now.atZone(SCHOOL_ZONE).toLocalDate()
        )) {
            try {
                automationService.reconcileAcademicYear(year.schoolId(), year.academicYearId(), now);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "V3 academic-calendar automation skipped academic year {} after an error.",
                        year.academicYearId(),
                        exception
                );
            }
        }
    }
}
