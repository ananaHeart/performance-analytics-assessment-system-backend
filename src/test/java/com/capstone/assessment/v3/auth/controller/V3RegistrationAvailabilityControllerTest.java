package com.capstone.assessment.v3.auth.controller;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import com.capstone.assessment.v3.auth.service.V3PublicRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V3RegistrationAvailabilityControllerTest {

    private final V3RegistrationRepository repository = mock(V3RegistrationRepository.class);
    private final V3RegistrationAvailabilityController controller =
            new V3RegistrationAvailabilityController(repository, new V3PublicRateLimiter());

    @Test
    void reg7SaysWhetherTheEmailAndNumberAreValidAndFree() {
        when(repository.emailExists("taken@example.com")).thenReturn(true);
        when(repository.contactExists("+639171234567")).thenReturn(false);

        var result = controller.availability("Taken@Example.com", "09171234567", new MockHttpServletRequest())
                .getBody().data();

        assertEquals(true, result.email().valid());
        assertEquals(false, result.email().available());
        assertEquals(true, result.contactNumber().available());
        var badFormat = controller.availability(null, "12345", new MockHttpServletRequest()).getBody().data();
        assertNull(badFormat.email());
        assertEquals(false, badFormat.contactNumber().valid());
    }

    @Test
    void reg7TooManyChecksFromOneClientAreRefused() {
        for (int i = 0; i < 30; i++) {
            controller.availability("a@example.com", null, new MockHttpServletRequest());
        }
        V3AuthException exception = assertThrows(V3AuthException.class,
                () -> controller.availability("a@example.com", null, new MockHttpServletRequest()));
        assertEquals("AVAILABILITY_RATE_LIMITED", exception.getCode());
    }
}
