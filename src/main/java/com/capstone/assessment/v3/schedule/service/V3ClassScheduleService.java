package com.capstone.assessment.v3.schedule.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.schedule.dto.V3ClassScheduleRequest;
import com.capstone.assessment.v3.schedule.dto.V3ClassScheduleResponse;
import com.capstone.assessment.v3.schedule.dto.V3ClassScheduleStatusRequest;
import com.capstone.assessment.v3.schedule.model.V3ClassScheduleModels.AssignmentContext;
import com.capstone.assessment.v3.schedule.model.V3ClassScheduleModels.ScheduleRow;
import com.capstone.assessment.v3.schedule.repository.V3ClassScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Profile("v3")
@Service
public class V3ClassScheduleService {

    public static final String AUTHORITATIVE_TIMEZONE = "Asia/Manila";
    private static final String TEACHER_ROLE = "teacher";

    private final V3ClassScheduleRepository repository;
    private final V3AuditService auditService;
    private final Clock clock;

    @Autowired
    public V3ClassScheduleService(
            V3ClassScheduleRepository repository,
            V3AuditService auditService
    ) {
        this(repository, auditService, Clock.systemUTC());
    }

    V3ClassScheduleService(
            V3ClassScheduleRepository repository,
            V3AuditService auditService,
            Clock clock
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<V3ClassScheduleResponse> listSchedules(
            V3AuthenticatedUser user,
            long classAssignmentId
    ) {
        requireOwnedAssignment(user, classAssignmentId, false);
        return repository.listSchedules(classAssignmentId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public V3ClassScheduleResponse createSchedule(
            V3AuthenticatedUser user,
            long classAssignmentId,
            V3ClassScheduleRequest request,
            V3RequestMetadata metadata
    ) {
        requireTeacher(user);
        repository.lockTeacher(user.userId());
        AssignmentContext assignment = requireOwnedAssignment(user, classAssignmentId, true);
        PreparedSchedule prepared = prepare(request, assignment);
        rejectConflict(user.userId(), prepared, null);

        try {
            long scheduleId = repository.insertSchedule(
                    UUID.randomUUID().toString(),
                    classAssignmentId,
                    prepared.dayOfWeek(),
                    prepared.startTime(),
                    prepared.endTime(),
                    AUTHORITATIVE_TIMEZONE,
                    prepared.effectiveFrom(),
                    prepared.effectiveTo(),
                    user.userId()
            );
            auditService.record(
                    user.userId(),
                    "class_schedule.create",
                    "class_assignment_schedules",
                    Long.toString(scheduleId),
                    "success",
                    metadata,
                    Map.of(
                            "classAssignmentId", classAssignmentId,
                            "dayOfWeek", prepared.dayOfWeek(),
                            "startTime", prepared.startTime().toString(),
                            "endTime", prepared.endTime().toString()
                    ),
                    clock.instant()
            );
            return requireSchedule(classAssignmentId, scheduleId);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("CLASS_SCHEDULE_CONFLICT", "The class schedule conflicts with existing data.");
        }
    }

    @Transactional
    public V3ClassScheduleResponse updateSchedule(
            V3AuthenticatedUser user,
            long classAssignmentId,
            long scheduleId,
            V3ClassScheduleRequest request,
            V3RequestMetadata metadata
    ) {
        requireTeacher(user);
        repository.lockTeacher(user.userId());
        AssignmentContext assignment = requireOwnedAssignment(user, classAssignmentId, true);
        ScheduleRow existing = requireScheduleRow(classAssignmentId, scheduleId);
        if (!"active".equals(existing.scheduleStatus())) {
            throw conflict("CLASS_SCHEDULE_ARCHIVED", "An archived class schedule cannot be edited.");
        }
        PreparedSchedule prepared = prepare(request, assignment);
        rejectConflict(user.userId(), prepared, scheduleId);

        try {
            if (repository.updateSchedule(
                    scheduleId,
                    prepared.dayOfWeek(),
                    prepared.startTime(),
                    prepared.endTime(),
                    AUTHORITATIVE_TIMEZONE,
                    prepared.effectiveFrom(),
                    prepared.effectiveTo()
            ) != 1) {
                throw conflict("CLASS_SCHEDULE_UPDATE_CONFLICT", "The class schedule was changed before update.");
            }
            auditService.record(
                    user.userId(),
                    "class_schedule.update",
                    "class_assignment_schedules",
                    Long.toString(scheduleId),
                    "success",
                    metadata,
                    Map.of("classAssignmentId", classAssignmentId),
                    clock.instant()
            );
            return requireSchedule(classAssignmentId, scheduleId);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("CLASS_SCHEDULE_CONFLICT", "The class schedule conflicts with existing data.");
        }
    }

    @Transactional
    public V3ClassScheduleResponse archiveSchedule(
            V3AuthenticatedUser user,
            long classAssignmentId,
            long scheduleId,
            V3ClassScheduleStatusRequest request,
            V3RequestMetadata metadata
    ) {
        requireTeacher(user);
        repository.lockTeacher(user.userId());
        requireOwnedAssignment(user, classAssignmentId, false);
        ScheduleRow existing = requireScheduleRow(classAssignmentId, scheduleId);
        if ("archived".equals(existing.scheduleStatus())) {
            return toResponse(existing);
        }
        String reason = requiredReason(request == null ? null : request.reason());
        Instant now = clock.instant();
        if (repository.archiveSchedule(scheduleId, user.userId(), reason, now) != 1) {
            throw conflict("CLASS_SCHEDULE_ARCHIVE_CONFLICT", "The class schedule was changed before archive.");
        }
        auditService.record(
                user.userId(),
                "class_schedule.archive",
                "class_assignment_schedules",
                Long.toString(scheduleId),
                "success",
                metadata,
                Map.of("classAssignmentId", classAssignmentId, "reason", reason),
                now
        );
        return requireSchedule(classAssignmentId, scheduleId);
    }

    private PreparedSchedule prepare(V3ClassScheduleRequest request, AssignmentContext assignment) {
        if (request == null) {
            throw invalid("request", "Class schedule request is required.");
        }
        Integer dayOfWeek = request.dayOfWeek();
        if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
            throw invalid("dayOfWeek", "dayOfWeek must use ISO values 1 (Monday) through 7 (Sunday).");
        }
        LocalTime startTime = request.startTime();
        LocalTime endTime = request.endTime();
        if (startTime == null) {
            throw invalid("startTime", "Start time is required.");
        }
        if (endTime == null || !endTime.isAfter(startTime)) {
            throw invalid("endTime", "End time must be later than start time.");
        }
        String timezone = request.timezoneName() == null || request.timezoneName().isBlank()
                ? AUTHORITATIVE_TIMEZONE
                : request.timezoneName().trim();
        if (!AUTHORITATIVE_TIMEZONE.equals(timezone)) {
            throw invalid("timezoneName", "V3 class schedules use Asia/Manila.");
        }
        LocalDate effectiveFrom = request.effectiveFrom();
        LocalDate effectiveTo = request.effectiveTo();
        if (effectiveFrom == null) {
            throw invalid("effectiveFrom", "Effective start date is required.");
        }
        if (effectiveFrom.isBefore(assignment.academicYearStartDate())
                || effectiveFrom.isAfter(assignment.academicYearEndDate())) {
            throw invalid("effectiveFrom", "The schedule must start inside the assignment academic year.");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw invalid("effectiveTo", "Effective end date cannot be before the start date.");
        }
        if (effectiveTo != null && effectiveTo.isAfter(assignment.academicYearEndDate())) {
            throw invalid("effectiveTo", "The schedule cannot end after the assignment academic year.");
        }
        return new PreparedSchedule(
                dayOfWeek,
                startTime,
                endTime,
                effectiveFrom,
                effectiveTo
        );
    }

    private void rejectConflict(long teacherUserId, PreparedSchedule schedule, Long excludedScheduleId) {
        if (repository.hasConflictingActiveSchedule(
                teacherUserId,
                schedule.dayOfWeek(),
                schedule.startTime(),
                schedule.endTime(),
                schedule.effectiveFrom(),
                schedule.effectiveTo(),
                excludedScheduleId
        )) {
            throw conflict(
                    "TEACHER_TIMETABLE_CONFLICT",
                    "This schedule overlaps another active class schedule for the teacher."
            );
        }
    }

    private AssignmentContext requireOwnedAssignment(
            V3AuthenticatedUser user,
            long classAssignmentId,
            boolean requireActive
    ) {
        requireTeacher(user);
        AssignmentContext assignment = repository.findAssignmentContext(classAssignmentId)
                .orElseThrow(() -> notFound("CLASS_ASSIGNMENT_NOT_FOUND", "Class assignment was not found."));
        if (assignment.teacherUserId() != user.userId()
                || !assignment.schoolId().equals(user.schoolId())) {
            throw forbidden(
                    "CLASS_ASSIGNMENT_OWNERSHIP_REQUIRED",
                    "Teachers may manage schedules only for their own school class assignments."
            );
        }
        if (requireActive && (!"active".equals(assignment.assignmentStatus())
                || !"active".equals(assignment.classStatus()))) {
            throw conflict(
                    "ACTIVE_CLASS_ASSIGNMENT_REQUIRED",
                    "An active class assignment and class are required to manage this schedule."
            );
        }
        return assignment;
    }

    private V3ClassScheduleResponse requireSchedule(long classAssignmentId, long scheduleId) {
        return toResponse(requireScheduleRow(classAssignmentId, scheduleId));
    }

    private ScheduleRow requireScheduleRow(long classAssignmentId, long scheduleId) {
        return repository.findSchedule(classAssignmentId, scheduleId)
                .orElseThrow(() -> notFound("CLASS_SCHEDULE_NOT_FOUND", "Class schedule was not found."));
    }

    private V3ClassScheduleResponse toResponse(ScheduleRow row) {
        return new V3ClassScheduleResponse(
                row.classAssignmentScheduleId(),
                row.scheduleUuid(),
                row.classAssignmentId(),
                row.dayOfWeek(),
                DayOfWeek.of(row.dayOfWeek()).getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                row.startTime(),
                row.endTime(),
                row.timezoneName(),
                row.effectiveFrom(),
                row.effectiveTo(),
                row.scheduleStatus(),
                row.statusChangedByUserId(),
                row.statusReason(),
                row.archivedAt(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw invalid("reason", "A reason is required to archive a class schedule.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() < 5 || trimmed.length() > 255) {
            throw invalid("reason", "Reason must contain between 5 and 255 characters.");
        }
        return trimmed;
    }

    private void requireTeacher(V3AuthenticatedUser user) {
        if (user == null) {
            throw new V3AuthException(
                    "AUTHENTICATION_REQUIRED",
                    "Authentication is required.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        if (!TEACHER_ROLE.equalsIgnoreCase(user.role())) {
            throw forbidden("TEACHER_ROLE_REQUIRED", "Only authenticated teachers may manage class schedules.");
        }
        if (!"active".equalsIgnoreCase(user.status())
                || user.schoolId() == null
                || user.schoolId().isBlank()) {
            throw forbidden(
                    "ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active teacher account associated with a school is required."
            );
        }
    }

    private V3FieldValidationException invalid(String field, String message) {
        return new V3FieldValidationException(
                "Class schedule validation failed.",
                HttpStatus.BAD_REQUEST,
                Map.of("code", "VALIDATION_FAILED", field, message)
        );
    }

    private V3AuthException conflict(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.CONFLICT);
    }

    private V3AuthException forbidden(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.FORBIDDEN);
    }

    private V3AuthException notFound(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.NOT_FOUND);
    }

    private record PreparedSchedule(
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
    }
}
