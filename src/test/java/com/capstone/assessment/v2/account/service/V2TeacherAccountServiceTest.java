package com.capstone.assessment.v2.account.service;

import com.capstone.assessment.v2.account.dto.V2AddressRequest;
import com.capstone.assessment.v2.account.dto.V2CreateTeacherRequest;
import com.capstone.assessment.v2.account.dto.V2EducationalAttainmentOption;
import com.capstone.assessment.v2.account.dto.V2GenderOption;
import com.capstone.assessment.v2.account.dto.V2MajorOption;
import com.capstone.assessment.v2.account.dto.V2SchoolOption;
import com.capstone.assessment.v2.account.dto.V2TeacherAccountResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherReferenceDataResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherRegistrationReferenceDataResponse;
import com.capstone.assessment.v2.account.dto.V2TeacherRegistrationRequest;
import com.capstone.assessment.v2.account.model.V2TeacherAccount;
import com.capstone.assessment.v2.account.repository.V2TeacherAccountRepository;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import com.capstone.assessment.v2.auth.exception.V2FieldValidationException;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.repository.V2AuthRepository;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2TeacherAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");
    private static final V2RequestMetadata METADATA = new V2RequestMetadata(
            "203.0.113.20",
            "JUnit",
            "principal-device"
    );
    private static final V2AuthenticatedUser PRINCIPAL = new V2AuthenticatedUser(
            10L,
            "SCHOOL-001",
            "principal@example.com",
            "principal",
            "active",
            "principal-session"
    );

    @Mock
    private V2TeacherAccountRepository accountRepository;

    @Mock
    private V2AuthRepository authRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private V2TeacherAccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new V2TeacherAccountService(
                accountRepository,
                authRepository,
                passwordEncoder,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void principalCreatesPendingTeacherInsideOwnSchool() {
        V2CreateTeacherRequest request = validRequest();
        when(accountRepository.genderExists(2)).thenReturn(true);
        when(accountRepository.majorExists(3)).thenReturn(true);
        when(accountRepository.educationalAttainmentExists(4)).thenReturn(true);
        when(passwordEncoder.encode("TempPass@2026")).thenReturn("bcrypt-hash");
        when(accountRepository.insertAddress(request.address())).thenReturn(101L);
        when(accountRepository.findRoleId("teacher")).thenReturn(2);
        when(accountRepository.findStatusId("pending")).thenReturn(1);
        when(accountRepository.insertTeacher(
                eq("SCHOOL-001"),
                eq(101L),
                eq(2),
                eq(1),
                eq(request),
                eq("teacher@example.com"),
                eq("+639171234567"),
                eq("bcrypt-hash"),
                eq(NOW)
        )).thenReturn(20L);
        when(accountRepository.findTeacher("SCHOOL-001", 20L))
                .thenReturn(Optional.of(teacher(20L, "SCHOOL-001", "pending")));

        V2TeacherAccountResponse response = accountService.createTeacher(PRINCIPAL, request, METADATA);

        assertEquals(20L, response.userId());
        assertEquals("SCHOOL-001", response.schoolId());
        assertEquals("pending", response.status());
        verify(authRepository).recordAudit(
                any(String.class),
                eq(10L),
                eq("CREATE_TEACHER_ACCOUNT"),
                eq("users"),
                eq("20"),
                eq("success"),
                eq("203.0.113.20"),
                eq("principal-device"),
                eq("JUnit"),
                eq("{\"status\":\"pending\"}"),
                eq(NOW)
        );
    }

    @Test
    void nonPrincipalCannotCreateTeacher() {
        V2AuthenticatedUser teacher = new V2AuthenticatedUser(
                11L,
                "SCHOOL-001",
                "teacher@example.com",
                "teacher",
                "active",
                "teacher-session"
        );

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> accountService.createTeacher(teacher, validRequest(), METADATA)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        verify(accountRepository, never()).insertAddress(any());
    }

    @Test
    void principalRetrievesTeacherReferenceData() {
        when(accountRepository.listGenders()).thenReturn(List.of(
                new V2GenderOption(1, "Male"),
                new V2GenderOption(2, "Female")
        ));
        when(accountRepository.listMajors()).thenReturn(List.of(
                new V2MajorOption(1, "English")
        ));
        when(accountRepository.listActiveEducationalAttainments()).thenReturn(List.of(
                new V2EducationalAttainmentOption(1, "Bachelor's Degree")
        ));

        V2TeacherReferenceDataResponse response = accountService.getReferenceData(PRINCIPAL);

        assertEquals(2, response.genders().size());
        assertEquals("English", response.majors().get(0).majorName());
        assertEquals("Bachelor's Degree", response.educationalAttainments().get(0).educationalAttainmentName());
    }

    @Test
    void nonPrincipalCannotRetrieveTeacherReferenceData() {
        V2AuthenticatedUser teacher = new V2AuthenticatedUser(
                11L,
                "SCHOOL-001",
                "teacher@example.com",
                "teacher",
                "active",
                "teacher-session"
        );

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> accountService.getReferenceData(teacher)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        verify(accountRepository, never()).listGenders();
        verify(accountRepository, never()).listMajors();
        verify(accountRepository, never()).listActiveEducationalAttainments();
    }

    @Test
    void publicRegistrationReferenceDataIncludesSchoolChoices() {
        when(accountRepository.listGenders()).thenReturn(List.of(
                new V2GenderOption(1, "Male")
        ));
        when(accountRepository.listMajors()).thenReturn(List.of(
                new V2MajorOption(1, "English")
        ));
        when(accountRepository.listActiveEducationalAttainments()).thenReturn(List.of(
                new V2EducationalAttainmentOption(1, "Bachelor's Degree")
        ));
        when(accountRepository.listSchools()).thenReturn(List.of(
                new V2SchoolOption("SCHOOL-001", "Test National High School")
        ));

        V2TeacherRegistrationReferenceDataResponse response = accountService.getPublicRegistrationReferenceData();

        assertEquals("SCHOOL-001", response.schools().get(0).schoolCode());
        assertEquals("Male", response.genders().get(0).genderName());
        assertEquals("English", response.majors().get(0).majorName());
        assertEquals("Bachelor's Degree", response.educationalAttainments().get(0).educationalAttainmentName());
    }

    @Test
    void publicRegistrationCreatesPendingTeacherForSubmittedSchoolCode() {
        V2TeacherRegistrationRequest request = validRegistrationRequest();
        when(accountRepository.findSchoolIdByCode("SCHOOL-001")).thenReturn(Optional.of("SCHOOL-001"));
        when(accountRepository.genderExists(2)).thenReturn(true);
        when(accountRepository.majorExists(3)).thenReturn(true);
        when(accountRepository.educationalAttainmentExists(4)).thenReturn(true);
        when(passwordEncoder.encode("TempPass@2026")).thenReturn("bcrypt-hash");
        when(accountRepository.insertAddress(request.address())).thenReturn(101L);
        when(accountRepository.findRoleId("teacher")).thenReturn(2);
        when(accountRepository.findStatusId("pending")).thenReturn(1);
        when(accountRepository.insertTeacher(
                eq("SCHOOL-001"),
                eq(101L),
                eq(2),
                eq(1),
                any(V2CreateTeacherRequest.class),
                eq("teacher@example.com"),
                eq("+639171234567"),
                eq("bcrypt-hash"),
                eq(NOW)
        )).thenReturn(20L);
        when(accountRepository.findTeacher("SCHOOL-001", 20L))
                .thenReturn(Optional.of(teacher(20L, "SCHOOL-001", "pending")));

        V2TeacherAccountResponse response = accountService.registerTeacher(request, METADATA);

        assertEquals(20L, response.userId());
        assertEquals("SCHOOL-001", response.schoolId());
        assertEquals("pending", response.status());
        verify(authRepository).recordAudit(
                any(String.class),
                eq(20L),
                eq("REGISTER_TEACHER_ACCOUNT"),
                eq("users"),
                eq("20"),
                eq("success"),
                eq("203.0.113.20"),
                eq("principal-device"),
                eq("JUnit"),
                argThat(details -> details != null
                        && details.contains("\"status\":\"pending\"")
                        && details.contains("\"schoolId\":\"SCHOOL-001\"")
                        && details.contains("\"source\":\"public_self_registration\"")),
                eq(NOW)
        );
    }

    @Test
    void publicRegistrationRejectsUnknownSchoolCodeBeforeAnyInsert() {
        V2TeacherRegistrationRequest request = new V2TeacherRegistrationRequest(
                "missing-school",
                validRequest().firstName(),
                validRequest().middleName(),
                validRequest().lastName(),
                validRequest().suffix(),
                validRequest().birthDate(),
                validRequest().teachingStartDate(),
                validRequest().email(),
                validRequest().contactNumber(),
                validRequest().temporaryPassword(),
                validRequest().genderId(),
                validRequest().majorId(),
                validRequest().educationalAttainmentId(),
                validRequest().address()
        );
        when(accountRepository.findSchoolIdByCode("MISSING-SCHOOL")).thenReturn(Optional.empty());

        V2FieldValidationException exception = assertThrows(
                V2FieldValidationException.class,
                () -> accountService.registerTeacher(request, METADATA)
        );

        assertEquals("INVALID_SCHOOL_CODE", exception.getErrors().get("code"));
        assertEquals("The selected school code is not registered.", exception.getErrors().get("schoolCode"));
        verify(accountRepository, never()).insertAddress(any());
    }

    @Test
    void publicRegistrationReturnsFieldErrorsForDuplicateEmailAndContact() {
        V2TeacherRegistrationRequest request = validRegistrationRequest();
        when(accountRepository.findSchoolIdByCode("SCHOOL-001")).thenReturn(Optional.of("SCHOOL-001"));
        when(accountRepository.genderExists(2)).thenReturn(true);
        when(accountRepository.majorExists(3)).thenReturn(true);
        when(accountRepository.educationalAttainmentExists(4)).thenReturn(true);
        when(accountRepository.emailExists("teacher@example.com")).thenReturn(true);
        when(accountRepository.contactExists("+639171234567")).thenReturn(true);

        V2FieldValidationException exception = assertThrows(
                V2FieldValidationException.class,
                () -> accountService.registerTeacher(request, METADATA)
        );

        assertEquals("DUPLICATE_ACCOUNT_DATA", exception.getErrors().get("code"));
        assertEquals("A user with this email already exists.", exception.getErrors().get("email"));
        assertEquals("A user with this contact number already exists.", exception.getErrors().get("contactNumber"));
        verify(accountRepository, never()).insertAddress(any());
    }

    @Test
    void duplicateEmailIsRejectedBeforeAnyInsert() {
        V2CreateTeacherRequest request = validRequest();
        when(accountRepository.genderExists(2)).thenReturn(true);
        when(accountRepository.majorExists(3)).thenReturn(true);
        when(accountRepository.educationalAttainmentExists(4)).thenReturn(true);
        when(accountRepository.emailExists("teacher@example.com")).thenReturn(true);

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> accountService.createTeacher(PRINCIPAL, request, METADATA)
        );

        assertEquals("DUPLICATE_EMAIL", exception.getCode());
        verify(accountRepository, never()).insertAddress(any());
        verify(accountRepository, never()).insertTeacher(
                any(), any(Long.class), any(Integer.class), any(Integer.class), any(),
                any(), any(), any(), any()
        );
    }

    @Test
    void teacherYoungerThanTwentyIsRejectedBeforeAnyInsert() {
        V2CreateTeacherRequest request = requestWithDates(
                LocalDate.of(2010, 1, 1),
                null
        );

        V2FieldValidationException exception = assertThrows(
                V2FieldValidationException.class,
                () -> accountService.createTeacher(PRINCIPAL, request, METADATA)
        );

        assertEquals("INVALID_TEACHER_DATES", exception.getErrors().get("code"));
        assertEquals("Teacher must be at least 20 years old.", exception.getErrors().get("birthDate"));
        verify(accountRepository, never()).insertAddress(any());
    }

    @Test
    void publicRegistrationRejectsTeachingStartDateToday() {
        V2TeacherRegistrationRequest request = registrationRequestWithDates(
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2026, 8, 10)
        );

        V2FieldValidationException exception = assertThrows(
                V2FieldValidationException.class,
                () -> accountService.registerTeacher(request, METADATA)
        );

        assertEquals("INVALID_TEACHER_DATES", exception.getErrors().get("code"));
        assertEquals(
                "Teaching start date must be before today.",
                exception.getErrors().get("teachingStartDate")
        );
        verify(accountRepository, never()).insertAddress(any());
    }

    @Test
    void publicRegistrationRejectsTeachingStartBeforeTeacherTurnsTwenty() {
        V2TeacherRegistrationRequest request = registrationRequestWithDates(
                LocalDate.of(2000, 1, 1),
                LocalDate.of(2018, 6, 1)
        );

        V2FieldValidationException exception = assertThrows(
                V2FieldValidationException.class,
                () -> accountService.registerTeacher(request, METADATA)
        );

        assertEquals("INVALID_TEACHER_DATES", exception.getErrors().get("code"));
        assertEquals(
                "Teaching start date cannot be earlier than the teacher's 20th birthday.",
                exception.getErrors().get("teachingStartDate")
        );
        verify(accountRepository, never()).insertAddress(any());
    }

    @Test
    void principalApprovesPendingTeacherWithinOwnSchool() {
        V2TeacherAccount pending = teacher(20L, "SCHOOL-001", "pending");
        V2TeacherAccount active = teacher(20L, "SCHOOL-001", "active");
        when(accountRepository.findTeacher("SCHOOL-001", 20L))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(active));
        when(accountRepository.findRoleId("teacher")).thenReturn(2);
        when(accountRepository.findStatusId("pending")).thenReturn(1);
        when(accountRepository.findStatusId("active")).thenReturn(2);
        when(accountRepository.transitionStatus("SCHOOL-001", 20L, 2, 1, 2)).thenReturn(1);

        V2TeacherAccountResponse response = accountService.approveTeacher(PRINCIPAL, 20L, METADATA);

        assertEquals("active", response.status());
        verify(accountRepository).transitionStatus("SCHOOL-001", 20L, 2, 1, 2);
        verify(authRepository).recordAudit(
                any(String.class),
                eq(10L),
                eq("APPROVE_TEACHER_ACCOUNT"),
                eq("users"),
                eq("20"),
                eq("success"),
                eq("203.0.113.20"),
                eq("principal-device"),
                eq("JUnit"),
                argThat(details -> details != null
                        && details.contains("\"previousStatus\":\"pending\"")
                        && details.contains("\"newStatus\":\"active\"")),
                eq(NOW)
        );
    }

    @Test
    void principalCannotManageTeacherFromAnotherSchool() {
        when(accountRepository.findTeacher("SCHOOL-001", 99L)).thenReturn(Optional.empty());

        V2AuthException exception = assertThrows(
                V2AuthException.class,
                () -> accountService.approveTeacher(PRINCIPAL, 99L, METADATA)
        );

        assertEquals("TEACHER_NOT_FOUND", exception.getCode());
        verify(accountRepository, never()).transitionStatus(
                any(), any(Long.class), any(Integer.class), any(Integer.class), any(Integer.class)
        );
    }

    private V2CreateTeacherRequest validRequest() {
        return new V2CreateTeacherRequest(
                " Maria ",
                "A",
                " Santos ",
                null,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2015, 6, 1),
                " Teacher@Example.com ",
                "+63 917 123 4567",
                "TempPass@2026",
                2,
                3,
                4,
                new V2AddressRequest(
                        "PH",
                        "06",
                        "Western Visayas",
                        "0604",
                        "Iloilo",
                        "063022",
                        "Iloilo City",
                        "063022001",
                        "City Proper",
                        "123 Test Street",
                        "5000"
                )
        );
    }

    private V2TeacherRegistrationRequest validRegistrationRequest() {
        V2CreateTeacherRequest request = validRequest();
        return new V2TeacherRegistrationRequest(
                " school-001 ",
                request.firstName(),
                request.middleName(),
                request.lastName(),
                request.suffix(),
                request.birthDate(),
                request.teachingStartDate(),
                request.email(),
                request.contactNumber(),
                request.temporaryPassword(),
                request.genderId(),
                request.majorId(),
                request.educationalAttainmentId(),
                request.address()
        );
    }

    private V2CreateTeacherRequest requestWithDates(LocalDate birthDate, LocalDate teachingStartDate) {
        V2CreateTeacherRequest request = validRequest();
        return new V2CreateTeacherRequest(
                request.firstName(),
                request.middleName(),
                request.lastName(),
                request.suffix(),
                birthDate,
                teachingStartDate,
                request.email(),
                request.contactNumber(),
                request.temporaryPassword(),
                request.genderId(),
                request.majorId(),
                request.educationalAttainmentId(),
                request.address()
        );
    }

    private V2TeacherRegistrationRequest registrationRequestWithDates(
            LocalDate birthDate,
            LocalDate teachingStartDate
    ) {
        V2CreateTeacherRequest request = requestWithDates(birthDate, teachingStartDate);
        return new V2TeacherRegistrationRequest(
                " school-001 ",
                request.firstName(),
                request.middleName(),
                request.lastName(),
                request.suffix(),
                request.birthDate(),
                request.teachingStartDate(),
                request.email(),
                request.contactNumber(),
                request.temporaryPassword(),
                request.genderId(),
                request.majorId(),
                request.educationalAttainmentId(),
                request.address()
        );
    }

    private V2TeacherAccount teacher(long userId, String schoolId, String status) {
        return new V2TeacherAccount(
                userId,
                schoolId,
                101L,
                2,
                3,
                4,
                "Maria",
                "A",
                "Santos",
                null,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2015, 6, 1),
                "teacher@example.com",
                "+639171234567",
                "teacher",
                status,
                false,
                false,
                NOW,
                NOW
        );
    }
}
