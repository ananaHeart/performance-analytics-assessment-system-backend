package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.dto.V3RegistrationResponse;
import com.capstone.assessment.v3.auth.dto.V3ResendVerificationRequest;
import com.capstone.assessment.v3.auth.dto.V3TeacherRegistrationRequest;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3PendingRegistrationChallenge;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V3TeacherRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final String NORMALIZED_CONTACT = "+639123456789";

    private final V3RegistrationRepository repository = mock(V3RegistrationRepository.class);
    private final V3RegistrationPersistenceService persistenceService =
            mock(V3RegistrationPersistenceService.class);
    private final V3VerificationEmailSender emailSender = mock(V3VerificationEmailSender.class);
    private final V3AuthProperties properties = new V3AuthProperties();
    private V3TeacherRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new V3TeacherRegistrationService(
                repository,
                persistenceService,
                emailSender,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(repository.findSchoolId("SCHOOL-001")).thenReturn(Optional.of("SCHOOL-001"));
        when(repository.genderExists(1)).thenReturn(true);
        when(repository.majorExists(1)).thenReturn(true);
        when(repository.educationalAttainmentExists(1)).thenReturn(true);
        when(repository.emailExists("teacher@example.com")).thenReturn(false);
        when(repository.contactExists(NORMALIZED_CONTACT)).thenReturn(false);
    }

    @Test
    void registrationPurgesExpiredIdentityBeforeCreatingNewProvisionalAccount() {
        V3TeacherRegistrationRequest request = validRequest(LocalDate.of(1990, 1, 1));
        V3PendingRegistrationChallenge challenge = pendingChallenge();
        when(persistenceService.createRegistration(
                eq("SCHOOL-001"),
                eq(request),
                eq("teacher@example.com"),
                eq(NORMALIZED_CONTACT),
                eq("email"),
                any(V3RequestMetadata.class),
                eq(NOW)
        )).thenReturn(challenge);
        when(emailSender.sendVerificationCode(challenge.recipientEmail(), challenge.rawOtp()))
                .thenReturn("local_log");

        V3RegistrationResponse response = service.register(request, metadata());

        assertEquals("pending_email_verification", response.accountStatus());
        assertEquals("sent", response.deliveryStatus());
        verify(persistenceService).purgeExpiredProvisionalIdentity(
                "teacher@example.com",
                NORMALIZED_CONTACT,
                NOW.minus(Duration.ofDays(30)),
                NOW
        );
        verify(persistenceService).markDeliverySent(challenge, "local_log", NOW);
    }

    @Test
    void underageTeacherIsRejectedBeforeAnyAccountIsCreated() {
        V3TeacherRegistrationRequest request = validRequest(LocalDate.of(2010, 1, 1));

        V3FieldValidationException exception = assertThrows(
                V3FieldValidationException.class,
                () -> service.register(request, metadata())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(
                "Teacher must be at least 18 years old and birth date must be in the past.",
                exception.getErrors().get("birthDate")
        );
        verify(persistenceService, never()).createRegistration(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void reg3AnUnknownSubjectIsRefusedAtRegistration() {
        V3TeacherRegistrationRequest base = validRequest(LocalDate.of(1995, 1, 1));
        V3TeacherRegistrationRequest withSubjects = new V3TeacherRegistrationRequest(base.schoolCode(),
                base.verificationMethod(), base.firstName(), base.middleName(), base.lastName(), base.suffixId(),
                base.birthDate(), base.teachingStartMonth(), base.teachingStartYear(), base.email(),
                base.contactNumber(), base.password(), base.genderId(), base.majorId(),
                base.educationalAttainmentId(), base.address(), java.util.List.of(77), null);

        V3FieldValidationException exception = assertThrows(V3FieldValidationException.class,
                () -> service.register(withSubjects, metadata()));

        assertEquals("Choose subjects from the list.", exception.getErrors().get("additionalMajorSubjectIds"));
    }

    @Test
    void reg1TheMinimumAgeIsASetting() {
        properties.setMinimumTeacherAge(21);

        V3FieldValidationException exception = assertThrows(V3FieldValidationException.class,
                () -> service.register(validRequest(LocalDate.of(2006, 1, 1)), metadata()));

        assertEquals("Teacher must be at least 21 years old and birth date must be in the past.",
                exception.getErrors().get("birthDate"));
    }

    @Test
    void deliveryFailureKeepsAccountResumableAndReturnsChallengeDetails() {
        V3TeacherRegistrationRequest request = validRequest(LocalDate.of(1990, 1, 1));
        V3PendingRegistrationChallenge challenge = pendingChallenge();
        when(persistenceService.createRegistration(
                eq("SCHOOL-001"),
                eq(request),
                eq("teacher@example.com"),
                eq(NORMALIZED_CONTACT),
                eq("email"),
                any(V3RequestMetadata.class),
                eq(NOW)
        )).thenReturn(challenge);
        when(emailSender.sendVerificationCode(challenge.recipientEmail(), challenge.rawOtp()))
                .thenThrow(new V3AuthException(
                        "EMAIL_DELIVERY_FAILED",
                        "The verification email could not be sent.",
                        HttpStatus.SERVICE_UNAVAILABLE
                ));

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.register(request, metadata())
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals("EMAIL_DELIVERY_FAILED", exception.getCode());
        assertEquals(true, exception.getDetails().get("accountCreated"));
        assertEquals(challenge.challengeUuid(), exception.getDetails().get("challengeUuid"));
        verify(persistenceService).markDeliveryFailed(challenge, "smtp", NOW);
    }

    @Test
    void choosingSmsSendsTheOnlyCodeByTextMessage() {
        V3TeacherRegistrationService smsService = serviceWithSmsGate(202, "{\"id\":\"msg-1\"}");
        V3TeacherRegistrationRequest request = validRequest(LocalDate.of(1990, 1, 1), "sms");
        V3PendingRegistrationChallenge challenge = pendingSmsChallenge();
        when(persistenceService.createRegistration(eq("SCHOOL-001"), eq(request), eq("teacher@example.com"),
                eq(NORMALIZED_CONTACT), eq("sms"), any(V3RequestMetadata.class), eq(NOW))).thenReturn(challenge);

        V3RegistrationResponse response = smsService.register(request, metadata());

        assertEquals("sms", response.verificationMethod());
        assertEquals("+63 9** *** 6789", response.contactMasked());
        assertNull(response.emailMasked());
        verify(persistenceService).markDeliverySent(challenge, V3SmsVerificationSender.SMSGATE_PROVIDER, NOW);
        verify(emailSender, never()).sendVerificationCode(any(), any());
    }

    @Test
    void aFailedSmsKeepsTheAccountSoTheTeacherCanResendOrUseEmail() {
        V3TeacherRegistrationService smsService = serviceWithSmsGate(401, "{\"message\":\"Unauthorized\"}");
        V3TeacherRegistrationRequest request = validRequest(LocalDate.of(1990, 1, 1), "sms");
        V3PendingRegistrationChallenge challenge = pendingSmsChallenge();
        when(persistenceService.createRegistration(eq("SCHOOL-001"), eq(request), eq("teacher@example.com"),
                eq(NORMALIZED_CONTACT), eq("sms"), any(V3RequestMetadata.class), eq(NOW))).thenReturn(challenge);

        V3AuthException exception = assertThrows(V3AuthException.class, () -> smsService.register(request, metadata()));

        assertEquals("SMS_DELIVERY_FAILED", exception.getCode());
        assertEquals(true, exception.getDetails().get("accountCreated"));
        assertEquals("sms", exception.getDetails().get("verificationMethod"));
        assertEquals(challenge.challengeUuid(), exception.getDetails().get("challengeUuid"));
        verify(persistenceService).markDeliveryFailed(challenge, V3SmsVerificationSender.SMSGATE_PROVIDER, NOW);
    }

    @Test
    void smsCannotBeChosenWhileItIsOff() {
        V3FieldValidationException exception = assertThrows(V3FieldValidationException.class,
                () -> service.register(validRequest(LocalDate.of(1990, 1, 1), "sms"), metadata()));

        assertEquals("SMS verification is not available. Choose email.",
                exception.getErrors().get("verificationMethod"));
        verify(persistenceService, never()).createRegistration(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aResendCanSwitchTheCodeToEmail() {
        V3PendingRegistrationChallenge challenge = pendingChallenge();
        when(persistenceService.prepareResend(eq("challenge-uuid"), eq("email"), any(V3RequestMetadata.class), eq(NOW)))
                .thenReturn(challenge);
        when(emailSender.sendVerificationCode(challenge.recipientEmail(), challenge.rawOtp())).thenReturn("local_log");

        var response = service.resend(new V3ResendVerificationRequest("challenge-uuid", "email"), metadata());

        assertEquals("email", response.verificationMethod());
        assertEquals("t***r@example.com", response.emailMasked());
        verify(persistenceService).markDeliverySent(challenge, "local_log", NOW);
    }

    private V3TeacherRegistrationService serviceWithSmsGate(int status, String body) {
        properties.setSmsMode("smsgate");
        properties.setSmsgateUsername("GATEUSER");
        properties.setSmsgatePassword("gate-pass");
        V3SmsVerificationSender sender = new V3SmsVerificationSender(properties,
                (url, auth, form) -> new V3SmsVerificationSender.Response(status, body));
        return new V3TeacherRegistrationService(repository, persistenceService, emailSender, sender, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private V3PendingRegistrationChallenge pendingSmsChallenge() {
        return new V3PendingRegistrationChallenge(102L, "challenge-uuid", 42L, 77L, "sms", "teacher@example.com",
                NORMALIZED_CONTACT, "+63 9** *** 6789", "654321", 0, NOW.plus(Duration.ofMinutes(10)),
                NOW.plus(Duration.ofMinutes(1)), NOW.plus(Duration.ofDays(30)));
    }

    private V3TeacherRegistrationRequest validRequest(LocalDate birthDate) {
        return validRequest(birthDate, "email");
    }

    private V3TeacherRegistrationRequest validRequest(LocalDate birthDate, String verificationMethod) {
        return new V3TeacherRegistrationRequest(
                "SCHOOL-001",
                verificationMethod,
                "Test",
                null,
                "Teacher",
                null,
                birthDate,
                6,
                2015,
                "Teacher@Example.com",
                "09123456789",
                "Strong@1234",
                1,
                1,
                1,
                new V3TeacherRegistrationRequest.Address(
                        "PH",
                        "12",
                        "SOCCSKSARGEN",
                        "1280",
                        "Sarangani",
                        "128006",
                        "Malungon",
                        "128006001",
                        "Poblacion",
                        "Unit 1, Main Street",
                        "9503"
                )
        );
    }

    private V3PendingRegistrationChallenge pendingChallenge() {
        return new V3PendingRegistrationChallenge(
                101L,
                "challenge-uuid",
                42L,
                77L,
                "email",
                "teacher@example.com",
                NORMALIZED_CONTACT,
                "t***r@example.com",
                "123456",
                0,
                NOW.plus(Duration.ofMinutes(10)),
                NOW.plus(Duration.ofMinutes(1)),
                NOW.plus(Duration.ofDays(30))
        );
    }

    private V3RequestMetadata metadata() {
        return new V3RequestMetadata("127.0.0.1", "JUnit", "browser-1");
    }
}
