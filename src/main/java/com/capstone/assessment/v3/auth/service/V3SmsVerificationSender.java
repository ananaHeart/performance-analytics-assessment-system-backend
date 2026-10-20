package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Delivers the registration SMS code. The backend always makes and checks the code itself; this
 * class only delivers it. In "log" mode the code is written to the server log (local testing,
 * nothing sent). In "philsms" mode PhilSMS sends it over HTTPS, which also works on Render Free.
 */
@Profile("v3")
@Component
public class V3SmsVerificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3SmsVerificationSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String LOG_PROVIDER = "local_log";
    public static final String PHILSMS_PROVIDER = "philsms";

    /** The provider could not send the code (not configured, no credit, outage...). */
    public static class SmsUnavailableException extends RuntimeException {
        public SmsUnavailableException(String message) {
            super(message);
        }
    }

    /**
     * The provider refused for now (HTTP 429). Unlike {@link SmsUnavailableException} this must
     * never fall back to email alone, or anyone could skip the SMS code by resending until refused.
     */
    public static class SmsRateLimitedException extends RuntimeException {
        public SmsRateLimitedException(String message) {
            super(message);
        }
    }

    /** One JSON POST to the SMS provider; returns the HTTP status and body. */
    @FunctionalInterface
    interface SmsTransport {
        Response post(URI url, String authorization, String jsonBody) throws IOException, InterruptedException;
    }

    record Response(int status, String body) {
    }

    private final V3AuthProperties properties;
    private final SmsTransport transport;

    @Autowired
    public V3SmsVerificationSender(V3AuthProperties properties) {
        this(properties, V3SmsVerificationSender::postWithHttpClient);
    }

    V3SmsVerificationSender(V3AuthProperties properties, SmsTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    public boolean isEnabled() {
        return switch (mode()) {
            case "log" -> true;
            case "philsms" -> hasText(properties.getPhilsmsApiToken()) && hasText(properties.getPhilsmsSenderId());
            default -> false;
        };
    }

    /** The provider name stored on a challenge, also when sending failed. */
    public String provider() {
        return "log".equals(mode()) ? LOG_PROVIDER : PHILSMS_PROVIDER;
    }

    /** Sends {@code code} to {@code phoneE164}; returns the provider name stored on the challenge. */
    public String send(String phoneE164, String code) {
        if (!isEnabled()) {
            throw new SmsUnavailableException("SMS verification is not configured.");
        }
        if ("log".equals(mode())) {
            LOGGER.warn("LOCAL-ONLY V3 contact verification code for {}: {}", maskPhone(phoneE164), code);
            return provider();
        }
        Map<String, String> body = new LinkedHashMap<>();
        // PhilSMS takes 63XXXXXXXXXX: the country code without "+".
        body.put("recipient", phoneE164.startsWith("+") ? phoneE164.substring(1) : phoneE164);
        body.put("sender_id", properties.getPhilsmsSenderId().trim());
        body.put("type", "plain");
        body.put("message", message(code));
        Response response = post("/sms/send", body);
        if (response.status() == 429) {
            LOGGER.warn("PhilSMS rate-limited {}: {}", maskPhone(phoneE164), providerError(response.body()));
            throw new SmsRateLimitedException("Too many SMS codes were requested. Wait a minute and try again.");
        }
        // PhilSMS can answer 200 with {"status":"error"} (e.g. no credit), so both are checked.
        if (response.status() < 200 || response.status() >= 300 || !"success".equals(status(response.body()))) {
            LOGGER.error("PhilSMS could not send to {}: HTTP {} {}", maskPhone(phoneE164),
                    response.status(), providerError(response.body()));
            throw new SmsUnavailableException("The SMS code could not be sent.");
        }
        return provider();
    }

    /** No links: Smart blocks shortened URLs, and a plain code is all the teacher needs. */
    private String message(String code) {
        long minutes = Math.max(1, properties.getOtpTtl().toMinutes());
        return "Your Marka verification code is " + code + ". It expires in " + minutes
                + " minutes. Do not share this code.";
    }

    /** "+639171234567" -> "+63 9** *** 4567". */
    public static String maskPhone(String phoneE164) {
        if (phoneE164 == null || phoneE164.length() < 8) {
            return "***";
        }
        String last4 = phoneE164.substring(phoneE164.length() - 4);
        return phoneE164.startsWith("+63") ? "+63 9** *** " + last4 : "*** *** " + last4;
    }

    private Response post(String path, Map<String, String> body) {
        URI url = URI.create(properties.getPhilsmsApiUrl().replaceAll("/+$", "") + path);
        try {
            return transport.post(url, "Bearer " + properties.getPhilsmsApiToken().trim(),
                    JSON.writeValueAsString(body));
        } catch (IOException exception) {
            LOGGER.error("PhilSMS request failed: {}", exception.toString());
            throw new SmsUnavailableException("The SMS service could not be reached.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SmsUnavailableException("The SMS request was interrupted.");
        }
    }

    private static String status(String body) {
        try {
            return JSON.readTree(body == null ? "" : body).path("status").asText("");
        } catch (IOException exception) {
            return "";
        }
    }

    private static String providerError(String body) {
        try {
            JsonNode node = JSON.readTree(body == null ? "" : body);
            return node.path("message").asText("(no message)");
        } catch (IOException exception) {
            return "(unreadable error body)";
        }
    }

    private static Response postWithHttpClient(URI url, String authorization, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String mode() {
        String mode = properties.getSmsMode();
        return mode == null ? "off" : mode.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
