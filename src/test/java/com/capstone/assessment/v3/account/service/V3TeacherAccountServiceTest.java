package com.capstone.assessment.v3.account.service;

import com.capstone.assessment.v3.account.dto.V3RejectTeacherRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountDetailResponse;
import com.capstone.assessment.v3.account.dto.V3TeacherAccountSummaryResponse;
import com.capstone.assessment.v3.account.model.V3TeacherAccount;
import com.capstone.assessment.v3.account.repository.V3TeacherAccountRepository;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.notification.service.V3NotificationService;
import org.junit.jupiter.api.BeforeEach;
import com.capstone.assessment.v3.account.dto.V3TeacherContractRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherDecisionRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherReasonRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherStatusRequest;
import com.capstone.assessment.v3.account.dto.V3TeacherUpdateRequest;
import com.capstone.assessment.v3.auth.dto.V3TeacherRegistrationRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import java.util.Map;
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
    private final V3RegistrationRepository registrationRepository = mock(V3RegistrationRepository.class);
    private V3TeacherAccountService service;

    @BeforeEach
    void setUp() {
        service = new V3TeacherAccountService(
                repository,
                auditService,
                notificationService,
                registrationRepository,
                new V3AuthProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void listDefaultsToVerifiedPendingApprovalTeachersInPrincipalSchool() {
        when(repository.findVerifiedTeachers("SCHOOL-001", List.of("pending_approval")))
                .thenReturn(List.of(teacher("pending_approval", NOW.minusSeconds(60))));

        List<V3TeacherAccountSummaryResponse> response = service.listTeachers(PRINCIPAL, null);

        assertEquals(1, response.size());
        assertEquals("Ana Marie Cruz Jr.", response.get(0).fullName());
        assertEquals("pending_approval", response.get(0).status());
        verify(repository).findVerifiedTeachers("SCHOOL-001", List.of("pending_approval"));
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
    void aTeacherVerifiedBySmsOnlyCanBeApproved() {
        V3TeacherAccount pending = teacher("pending_approval", null, NOW.minusSeconds(60));
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(pending));
        when(repository.transitionStatus(
                "SCHOOL-001", 42L, "pending_approval", "active", NOW
        )).thenReturn(1);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L))
                .thenReturn(Optional.of(teacher("active", null, NOW.minusSeconds(60))));

        assertEquals("active", service.approveTeacher(PRINCIPAL, 42L, METADATA).status());
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

    @Test
    void reg2ApprovalStartsAThreeYearContract() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("pending_approval", NOW)));
        when(repository.transitionStatus("SCHOOL-001", 42L, "pending_approval", "active", NOW)).thenReturn(1);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("active", NOW)));

        service.approveTeacher(PRINCIPAL, 42L, METADATA);

        verify(repository).updateContractEndDate(42L, LocalDate.of(2029, 9, 2), NOW);
    }

    @Test
    void tch3TheApprovedTabListsActiveAndInactiveTeachersWithBothWords() {
        when(repository.findVerifiedTeachers("SCHOOL-001", List.of("active", "inactive", "locked")))
                .thenReturn(List.of(teacher("inactive", NOW)));

        V3TeacherAccountSummaryResponse summary = service.listTeachers(PRINCIPAL, "Approved").get(0);

        assertEquals("approved", summary.approvalStatus());
        assertEquals("inactive", summary.accountStatus());
        assertEquals("rejected", V3TeacherAccountService.approvalStatus("rejected"));
        assertEquals("pending", V3TeacherAccountService.approvalStatus("pending_approval"));
        assertEquals("archived", V3TeacherAccountService.accountStatus("archived"));
        assertEquals(null, V3TeacherAccountService.accountStatus("rejected"));
    }

    @Test
    void tch9ArchivingATeacherEndsTheirAssignmentsAndSignsThemOut() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("active", NOW)));
        when(repository.transitionArchiveState("SCHOOL-001", 42L, "active", "archived", NOW, NOW)).thenReturn(1);
        when(repository.endOpenClassAssignments(42L, 10L, "Teacher archived: Resigned in October", NOW))
                .thenReturn(2);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("archived", NOW)));

        V3TeacherAccountDetailResponse response = service.archiveTeacher(PRINCIPAL, 42L,
                new V3TeacherReasonRequest("Resigned in October"), METADATA);

        assertEquals("archived", response.accountStatus());
        verify(repository).revokeSessions(42L, NOW);
        verify(auditService).record(eq(10L), eq("teacher.archive"), eq("users"), eq("42"), eq("success"),
                eq(METADATA), argThat(details -> Integer.valueOf(2).equals(details.get("endedClassAssignments"))),
                eq(NOW));
    }

    @Test
    void tch4OnlyApprovedTeachersAreArchivedAndOnlyArchivedOnesRestored() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("pending_approval", NOW)));
        assertEquals("TEACHER_NOT_APPROVED", assertThrows(V3AuthException.class, () -> service.archiveTeacher(
                PRINCIPAL, 42L, new V3TeacherReasonRequest("Wrong account"), METADATA)).getCode());
        assertEquals("TEACHER_NOT_ARCHIVED", assertThrows(V3AuthException.class, () -> service.restoreTeacher(
                PRINCIPAL, 42L, new V3TeacherReasonRequest("Came back"), METADATA)).getCode());
        verify(repository, never()).transitionArchiveState(anyString(), anyLong(), anyString(), anyString(),
                any(), any());
    }

    @Test
    void tch4RestoringATeacherWhoseContractEndedLeavesThemInactive() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(
                teacher("archived", NOW, null, LocalDate.of(2026, 8, 1))));
        when(repository.transitionArchiveState("SCHOOL-001", 42L, "archived", "inactive", null, NOW)).thenReturn(1);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("inactive", NOW)));

        service.restoreTeacher(PRINCIPAL, 42L, new V3TeacherReasonRequest("Came back to school"), METADATA);

        verify(repository).transitionArchiveState("SCHOOL-001", 42L, "archived", "inactive", null, NOW);
    }

    @Test
    void tch5RejectingAnApprovedTeacherSignsThemOutAndNeedsAReason() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("active", NOW)));
        when(repository.transitionStatus("SCHOOL-001", 42L, "active", "rejected", NOW)).thenReturn(1);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("rejected", NOW)));

        assertThrows(V3FieldValidationException.class, () -> service.changeDecision(PRINCIPAL, 42L,
                new V3TeacherDecisionRequest("rejected", " "), METADATA));
        V3TeacherAccountDetailResponse response = service.changeDecision(PRINCIPAL, 42L,
                new V3TeacherDecisionRequest("Rejected", "Approved by mistake"), METADATA);

        assertEquals("rejected", response.approvalStatus());
        verify(repository).endOpenClassAssignments(42L, 10L, "Teacher rejected: Approved by mistake", NOW);
        verify(repository).revokeSessions(42L, NOW);
        verify(notificationService).notifyTeacherRejected(42L, NOW);
        verify(auditService).record(eq(10L), eq("teacher.decision_change"), eq("users"), eq("42"),
                eq("success"), eq(METADATA), anyMap(), eq(NOW));
    }

    @Test
    void tch5ApprovingARejectedTeacherStartsANewContract() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("rejected", NOW)));
        when(repository.transitionStatus("SCHOOL-001", 42L, "rejected", "active", NOW)).thenReturn(1);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("active", NOW)));

        service.changeDecision(PRINCIPAL, 42L, new V3TeacherDecisionRequest("approved", "Rejected by mistake"),
                METADATA);

        verify(repository).updateContractEndDate(42L, LocalDate.of(2029, 9, 2), NOW);
        verify(notificationService).notifyTeacherApproved(42L, NOW);
        assertEquals("TEACHER_NOT_DECIDED", assertThrows(V3AuthException.class, () -> {
            when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("pending_approval", NOW)));
            service.changeDecision(PRINCIPAL, 42L, new V3TeacherDecisionRequest("rejected", "Not a teacher"),
                    METADATA);
        }).getCode());
    }

    @Test
    void tch3AnEndedContractMustBeExtendedBeforeTheAccountIsActiveAgain() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(
                teacher("inactive", NOW, null, LocalDate.of(2026, 8, 31))));

        V3AuthException exception = assertThrows(V3AuthException.class, () -> service.changeAccountStatus(
                PRINCIPAL, 42L, new V3TeacherStatusRequest("active", "Back from leave"), METADATA));

        assertEquals("TEACHER_CONTRACT_ENDED", exception.getCode());
        verify(repository, never()).transitionStatus(anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void tch3SettingATeacherInactiveSignsThemOut() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("active", NOW)));
        when(repository.transitionStatus("SCHOOL-001", 42L, "active", "inactive", NOW)).thenReturn(1);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("inactive", NOW)));

        service.changeAccountStatus(PRINCIPAL, 42L, new V3TeacherStatusRequest("inactive", "On leave"), METADATA);

        verify(repository).revokeSessions(42L, NOW);
    }

    @Test
    void reg2ExtendingAnEndedContractMakesTheAccountActiveAgain() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(
                teacher("inactive", NOW, null, LocalDate.of(2026, 8, 31))));
        when(repository.transitionStatus("SCHOOL-001", 42L, "inactive", "active", NOW)).thenReturn(1);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("active", NOW)));

        service.updateContract(PRINCIPAL, 42L,
                new V3TeacherContractRequest(LocalDate.of(2029, 8, 31), "Contract renewed"), METADATA);

        verify(repository).updateContractEndDate(42L, LocalDate.of(2029, 8, 31), NOW);
        verify(repository).transitionStatus("SCHOOL-001", 42L, "inactive", "active", NOW);
        V3FieldValidationException tooFar = assertThrows(V3FieldValidationException.class, () ->
                service.updateContract(PRINCIPAL, 42L,
                        new V3TeacherContractRequest(LocalDate.of(2029, 9, 3), "Contract renewed"), METADATA));
        assertEquals("Choose a date after today and at most 3 years from today.",
                tooFar.getErrors().get("contractEndDate"));
    }

    @Test
    void reg2TheNightlyCheckSetsEndedContractsToInactive() {
        when(repository.findActiveTeachersWithEndedContracts(LocalDate.of(2026, 9, 2))).thenReturn(List.of(42L, 43L));
        when(repository.deactivateEndedContract(42L, LocalDate.of(2026, 9, 2), NOW)).thenReturn(1);
        when(repository.deactivateEndedContract(43L, LocalDate.of(2026, 9, 2), NOW)).thenReturn(0);

        assertEquals(1, service.deactivateEndedContracts());
        verify(repository).revokeSessions(42L, NOW);
        verify(repository, never()).revokeSessions(43L, NOW);
    }

    @Test
    void tch4ThePrincipalCanCorrectATeachersDetailsButNotForAnArchivedTeacher() {
        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("active", NOW)));
        when(registrationRepository.genderExists(2)).thenReturn(true);
        when(registrationRepository.majorExists(4)).thenReturn(true);
        when(registrationRepository.educationalAttainmentExists(3)).thenReturn(true);
        when(repository.findVerifiedTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("active", NOW)));

        service.updateTeacher(PRINCIPAL, 42L, updateRequest(4), METADATA);

        verify(repository).updateTeacherDetails(eq(42L), argThat(details -> "Ana".equals(details.firstName())
                && details.middleName() == null && details.majorId() == 4), eq(NOW));
        verify(repository).updateAddress(eq(100L), argThat(address -> "9503".equals(address.postalCode())));

        V3FieldValidationException invalid = assertThrows(V3FieldValidationException.class,
                () -> service.updateTeacher(PRINCIPAL, 42L, updateRequest(99), METADATA));
        assertEquals("The selected teacher major does not exist.", invalid.getErrors().get("majorId"));

        when(repository.lockTeacher("SCHOOL-001", 42L)).thenReturn(Optional.of(teacher("archived", NOW)));
        assertEquals("TEACHER_ARCHIVED", assertThrows(V3AuthException.class,
                () -> service.updateTeacher(PRINCIPAL, 42L, updateRequest(4), METADATA)).getCode());
    }

    @Test
    void tch8TeachersArchivedTenYearsAgoLoseTheirPersonalDetailsOnly() {
        Instant cutoff = NOW.atZone(java.time.ZoneId.of("Asia/Manila")).minusYears(10).toInstant();
        when(repository.findTeachersArchivedBefore(cutoff)).thenReturn(List.of(Map.entry(42L, "ana.cruz@example.com")));
        when(repository.anonymizeTeacher(42L, NOW)).thenReturn(1);

        assertEquals(1, service.anonymizeLongArchivedTeachers());

        verify(registrationRepository).anonymizeLoginAttempts(42L, "ana.cruz@example.com",
                "anonymized-42@invalid.local");
        verify(repository).revokeSessions(42L, NOW);
        verify(repository, never()).endOpenClassAssignments(anyLong(), anyLong(), anyString(), any());
    }

    private static V3TeacherUpdateRequest updateRequest(int majorId) {
        return new V3TeacherUpdateRequest(" Ana ", " ", "Cruz", null, 2, LocalDate.of(1990, 5, 20), 6, 2015,
                majorId, 3,
                new V3TeacherRegistrationRequest.Address("PH", null, null, null, null, null, "Malungon", null,
                        "Poblacion", "Purok 1", "9503"),
                "Typo in the name");
    }

    private V3TeacherAccount teacher(String status, Instant emailVerifiedAt) {
        return teacher(status, emailVerifiedAt, null);
    }

    private V3TeacherAccount teacher(String status, Instant emailVerifiedAt, Instant contactVerifiedAt) {
        return teacher(status, emailVerifiedAt, contactVerifiedAt, null);
    }

    private V3TeacherAccount teacher(String status, Instant emailVerifiedAt, Instant contactVerifiedAt,
                                     LocalDate contractEndDate) {
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
                contactVerifiedAt,
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
                ),
                contractEndDate,
                "archived".equals(status) ? NOW.minusSeconds(60) : null
        );
    }
}
