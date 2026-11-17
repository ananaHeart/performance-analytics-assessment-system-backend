package com.capstone.assessment.v3.auth.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.service.V3PostalCodes;
import com.capstone.assessment.v3.auth.service.V3PublicRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * R2-3: the sign-up Address step checks the postal code before the final submit, with the same
 * rule as register-teacher (which stays the final guard).
 */
@Profile("v3")
@RestController
public class V3PostalCodeCheckController {

    private static final int CHECKS_PER_MINUTE = 30;

    private final V3PublicRateLimiter rateLimiter;

    public V3PostalCodeCheckController(V3PublicRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /** valid=false carries the same message register-teacher would give, plus the city's ZIP codes. */
    public record Result(boolean valid, String message, List<String> cityPostalCodes) {
    }

    @GetMapping("/api/v3/auth/postal-code-check")
    public ResponseEntity<ApiResponse<Result>> check(
            @RequestParam(required = false) String postalCode,
            @RequestParam(required = false) String cityMunicipalityCode,
            @RequestParam(required = false) String cityMunicipalityName,
            HttpServletRequest request
    ) {
        rateLimiter.check("postal-code-check:" + clientIp(request), CHECKS_PER_MINUTE, Duration.ofMinutes(1),
                "POSTAL_CODE_CHECK_RATE_LIMITED");
        Map<String, Object> errors = new LinkedHashMap<>();
        String zip = postalCode == null ? "" : postalCode.trim();
        String city = cityMunicipalityCode == null ? "" : cityMunicipalityCode.trim();
        if (!zip.matches("\\d{4}")) {
            errors.put("postalCode", "Postal code must be 4 digits.");
        }
        if (city.isEmpty()) {
            errors.put("cityMunicipalityCode", "Choose the city or municipality first.");
        }
        if (!errors.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "VALIDATION_FAILED");
            body.putAll(errors);
            throw new V3FieldValidationException("Validation failed.", HttpStatus.BAD_REQUEST, body);
        }
        Optional<String> problem = V3PostalCodes.problem(zip, city, cityMunicipalityName);
        return ResponseEntity.ok(ApiResponse.success("Postal code checked.",
                new Result(problem.isEmpty(), problem.orElse(null), V3PostalCodes.postalCodesFor(city))));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
    }
}
