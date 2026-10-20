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
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Delivers the registration SMS code. The backend always makes and checks the code itself; this
 * class only delivers it. In "log" mode the code is written to the server log (local testing,
 * nothing sent). In "smsgate" mode the SMS Gateway for Android cloud server hands it to the
 * school's Android phone, which texts it from its own SIM. That needs no sender-name registration
 * and works over HTTPS from Render Free.
 */
@Profile("v3")
@Component
public class V3SmsVerificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3SmsVerificationSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String LOG_PROVIDER = "local_log";
    public static final String SMSGATE_PROVIDER = "smsgate";

    /** The code could not be handed over (not configured, wrong login, outage...). */
    public static class SmsUnavailableException extends RuntimeException {
        public SmsUnavailableException(String message) {
            super(message);
        }
    }

    /**
     * The gateway refused for now (HTTP 429). Unlike {@link SmsUnavailableException} this must
     * never fall back to email alone, or anyone could skip the SMS code by resending until refused.
     */
    public static class SmsRateLimitedException extends RuntimeException {
        public SmsRateLimitedException(String message) {
            super(message);
        }
    }

    /** One JSON POST to the SMS gateway; returns the HTTP status and body. */
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
            case "smsgate" -> hasText(properties.getSmsgateUsername()) && hasText(properties.getSmsgatePassword());
            default -> false;
        };
    }

    /** The provider name stored on a challenge, also when sending failed. */
    public String provider() {
        return "log".equals(mode()) ? LOG_PROVIDER : SMSGATE_PROVIDER;
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
        Map<String, Object> body = Map.of(
                "textMessage", Map.of("text", message(code)),
                "phoneNumbers", List.of(phoneE164));
        Response response = post("/messages", body);
        if (response.status() == 429) {
            LOGGER.warn("SMS gateway rate-limited {}: {}", maskPhone(phoneE164), gatewayError(response.body()));
            throw new SmsRateLimitedException("Too many SMS codes were requested. Wait a minute and try again.");
        }
        // 202 Accepted: queued for the phone, which texts it as soon as it is online.
        if (response.status() < 200 || response.status() >= 300) {
            LOGGER.error("SMS gateway could not send to {}: HTTP {} {}", maskPhone(phoneE164),
                    response.status(), gatewayError(response.body()));
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

    private Response post(String path, Map<String, Object> body) {
        URI url = URI.create(properties.getSmsgateApiUrl().replaceAll("/+$", "") + path);
        String credentials = properties.getSmsgateUsername().trim() + ":" + properties.getSmsgatePassword().trim();
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        try {
            return transport.post(url, basicAuth, JSON.writeValueAsString(body));
        } catch (IOException exception) {
            LOGGER.error("SMS gateway request failed: {}", exception.toString());
            throw new SmsUnavailableException("The SMS service could not be reached.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SmsUnavailableException("The SMS request was interrupted.");
        }
    }

    private static String gatewayError(String body) {
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
