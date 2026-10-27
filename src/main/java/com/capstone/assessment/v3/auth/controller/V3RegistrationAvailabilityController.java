package com.capstone.assessment.v3.auth.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.dto.V3RegistrationAvailabilityResponse;
import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;
import com.capstone.assessment.v3.auth.service.V3PublicRateLimiter;
import com.capstone.assessment.v3.auth.service.V3TeacherRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

/** REG-7: the registration form checks an email or number when the user leaves the field. */
@Profile("v3")
@RestController
@RequestMapping("/api/v3/auth/teacher-registration")
public class V3RegistrationAvailabilityController {

    private static final int CHECKS_PER_MINUTE = 30;

    private final V3RegistrationRepository repository;
    private final V3PublicRateLimiter rateLimiter;

    public V3RegistrationAvailabilityController(V3RegistrationRepository repository, V3PublicRateLimiter rateLimiter) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<V3RegistrationAvailabilityResponse>> availability(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String contactNumber,
            HttpServletRequest request
    ) {
        rateLimiter.check("availability:" + clientIp(request), CHECKS_PER_MINUTE, Duration.ofMinutes(1),
                "AVAILABILITY_RATE_LIMITED");
        V3RegistrationAvailabilityResponse.Field emailField = null;
        if (email != null) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            boolean valid = V3TeacherRegistrationService.isValidEmailAddress(normalized);
            emailField = new V3RegistrationAvailabilityResponse.Field(valid, valid && !repository.emailExists(normalized));
        }
        V3RegistrationAvailabilityResponse.Field contactField = null;
        if (contactNumber != null) {
            String normalized = V3TeacherRegistrationService.toE164Contact(contactNumber);
            boolean valid = normalized != null;
            contactField = new V3RegistrationAvailabilityResponse.Field(valid,
                    valid && !repository.contactExists(normalized));
        }
        return ResponseEntity.ok(ApiResponse.success("Availability checked.",
                new V3RegistrationAvailabilityResponse(emailField, contactField)));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
    }
}
