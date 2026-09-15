package com.capstone.assessment.v3.account.service;

import com.capstone.assessment.v3.account.dto.V3RejectTeacherRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountDetailResponse;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountSummaryResponse;
import com.capstone.assessment.v3.account.model.V3TeacherAccount;
import com.capstone.assessment.v3.account.repository.V3TeacherAccountRepository;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V3TeacherAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final V3AuthenticatedUser PRINCIPAL = new V3AuthenticatedUser(
            10L,
            "SCHOOL-001",
            "principal@example.com",
            "principal",
            "active",
            "principal-session"
    );
    private static final V3RequestMetadata METADATA = new V3RequestMetadata(
            "127.0.0.1",
            "JUnit",
            null
    );

    private final V3TeacherAccountRepository repository = mock(V3TeacherAccountRepository.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private final V3NotificationService notificationService = mock(V3NotificationService.class);
    private V3TeacherAccountService service;

    @BeforeEach
    void setUp() {
        service = new V3TeacherAccountService(
                repository,
                auditService,
                notificationService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void listDefaultsToVerifiedPendingApprovalTeachersInPrincipalSchool() {
        when(repository.findVerifiedTeachers("SCHOOL-001", "pending_approval"))
                .thenReturn(List.of(teacher("pending_approval", NOW.minusSeconds(60))));

        List<V3TeacherAccountSummaryResponse> response = service.listTeachers(PRINCIPAL, null);

        assertEquals(1, response.size());
        assertEquals("Ana Marie Cruz Jr.", response.get(0).fullName());
        assertEquals("pending_approval", response.get(0).status());
        verify(repository).findVerifiedTeachers("SCHOOL-001", "pending_approval");
    }

    @Test
    void pendingEmailVerificationCannotBeRequestedFromPrincipalList() {
        V3FieldValidationException exception = assertThrows(
                V3FieldValidationException.class,
                () -> service.listTeachers(PRINCIPAL, "pending_email_verification")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("VALIDATION_FAILED", exception.getErrors().get("code"));
        verifyNoInteractions(repository);
    }

    @Test
    void unauthenticatedTeacherAndInactivePrincipalAreRejected() {
        V3AuthException unauthenticated = assertThrows(
                V3AuthException.class,
                () -> service.listTeachers(null, null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatus());

        V3AuthenticatedUser teacher = new V3AuthenticatedUser(
                20L, "SCHOOL-001", "teacher@example.com", "teacher", "active", "session"
        );
        V3AuthException teacherError = assertThrows(
                V3AuthException.class,
                () -> service.listTeachers(teacher, null)
        );
        assertEquals("PRINCIPAL_ROLE_REQUIRED", teacherError.getCode());

        V3AuthenticatedUser inactivePrincipal = new V3AuthenticatedUser(
                10L, "SCHOOL-001", "principal@example.com", "principal", "inactive", "session"
        );
        V3AuthException inactiveError = assertThrows(
                V3AuthException.class,
                () -> service.listTeachers(inactivePrincipal, null)
        );
        assertEquals("ACTIVE_SCHOOL_ACCOUNT_REQUIRED", inactiveError.getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void detailDoesNotExposeTeacherOutsidePrincipalSchool() {
        when(repository.findVerifiedTeacher("SCHOOL-001", 99L)).thenReturn(Optional.empty());

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.getTeacher(PRINCIPAL, 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("TEACHER_NOT_FOUND", exception.getCode());
        verify(repository).findVerifiedTeacher("SCHOOL-001", 99L);
    }

    @Test
    void approvesVerifiedPendingTeacherAndRecordsAudit() {
        V3TeacherAccount pending = teacher("pending_approval", NOW.minusSeconds(60));
        V3TeacherAccount active = teacher("active", NOW.minusSeconds(60));
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(pending));
        when(repository.transitionStatus(
                "SCHOOL-001", 42L, "pending_approval", "active", NOW
        )).thenReturn(1);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(active));

        V3TeacherAccountDetailResponse response = service.approveTeacher(
                PRINCIPAL,
                42L,
                METADATA
        );

        assertEquals("active", response.status());
        verify(auditService).record(
                eq(10L),
                eq("teacher.approve"),
                eq("users"),
                eq("42"),
                eq("success"),
                eq(METADATA),
                argThat(details -> "pending_approval".equals(details.get("previousStatus"))
                        && "active".equals(details.get("newStatus"))),
                eq(NOW)
        );
        verify(notificationService).notifyTeacherApproved(42L, NOW);
    }

    @Test
    void rejectsVerifiedPendingTeacherWithTrimmedReasonAndAudit() {
        V3TeacherAccount pending = teacher("pending_approval", NOW.minusSeconds(60));
        V3TeacherAccount rejected = teacher("rejected", NOW.minusSeconds(60));
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(pending));
        when(repository.transitionStatus(
                "SCHOOL-001", 42L, "pending_approval", "rejected", NOW
        )).thenReturn(1);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(rejected));

        V3TeacherAccountDetailResponse response = service.rejectTeacher(
                PRINCIPAL,
                42L,
                new V3RejectTeacherRequest("  School employment could not be confirmed.  "),
                METADATA
        );

        assertEquals("rejected", response.status());
        verify(auditService).record(
                eq(10L),
                eq("teacher.reject"),
                eq("users"),
                eq("42"),
                eq("success"),
                eq(METADATA),
                argThat(details -> "rejected".equals(details.get("newStatus"))
                        && "School employment could not be confirmed.".equals(details.get("reason"))),
                eq(NOW)
        );
        verify(notificationService).notifyTeacherRejected(42L, NOW);
    }

    @Test
    void rejectionReasonIsRequiredEvenWhenServiceCalledDirectly() {
        V3FieldValidationException exception = assertThrows(
                V3FieldValidationException.class,
                () -> service.rejectTeacher(
                        PRINCIPAL,
                        42L,
                        new V3RejectTeacherRequest(" "),
                        METADATA
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Rejection reason is required.", exception.getErrors().get("reason"));
        verifyNoInteractions(repository);
    }

    @Test
    void unverifiedTeacherCannotBeApproved() {
        when(repository.lockTeacher("SCHOOL-001", 42L))
                .thenReturn(Optional.of(teacher("pending_approval", null)));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.approveTeacher(PRINCIPAL, 42L, METADATA)
        );

        assertEquals("EMAIL_VERIFICATION_REQUIRED", exception.getCode());
        verify(repository, never()).transitionStatus(
                eq("SCHOOL-001"), eq(42L), eq("pending_approval"), eq("active"), eq(NOW)
        );
        verifyNoInteractions(auditService);
        verifyNoInteractions(notificationService);
    }

    @Test
    void onlyPendingApprovalTeacherCanBeDecided() {
        when(repository.lockTeacher("SCHOOL-001", 42L))
                .thenReturn(Optional.of(teacher("active", NOW.minusSeconds(60))));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.approveTeacher(PRINCIPAL, 42L, METADATA)
        );

        assertEquals("TEACHER_NOT_PENDING_APPROVAL", exception.getCode());
        verify(repository, never()).transitionStatus(
                eq("SCHOOL-001"), eq(42L), eq("pending_approval"), eq("active"), eq(NOW)
        );
        verifyNoInteractions(auditService);
        verifyNoInteractions(notificationService);
    }

    @Test
    void conditionalUpdateFailureDoesNotWriteAudit() {
        when(repository.lockTeacher("SCHOOL-001", 42L))
                .thenReturn(Optional.of(teacher("pending_approval", NOW.minusSeconds(60))));
        when(repository.transitionStatus(
                "SCHOOL-001", 42L, "pending_approval", "active", NOW
        )).thenReturn(0);

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.approveTeacher(PRINCIPAL, 42L, METADATA)
        );

        assertEquals("TEACHER_ACCOUNT_STATE_CHANGED", exception.getCode());
        verifyNoInteractions(auditService);
        verifyNoInteractions(notificationService);
    }

    private V3TeacherAccount teacher(String status, Instant emailVerifiedAt) {
        return new V3TeacherAccount(
                42L,
                "SCHOOL-001",
                100L,
                2,
                "Female",
                4,
                "English",
                3,
                "Bachelor's Degree",
                1,
                "Jr.",
                "Ana",
                "Marie",
                "Cruz",
                LocalDate.of(1990, 5, 20),
                6,
                2015,
                "ana.cruz@example.com",
                "+639171234567",
                "teacher",
                status,
                emailVerifiedAt,
                null,
                NOW.minusSeconds(3600),
                NOW,
                new V3TeacherAccount.Address(
                        "PH",
                        "1200000000",
                        "SOCCSKSARGEN",
                        "1280000000",
                        "Sarangani",
                        "1280500000",
                        "Malungon",
                        "1280501000",
                        "Poblacion",
                        "Unit 1",
                        "9503",
                        "api"
                )
        );
    }
}
