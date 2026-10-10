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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * Sends and checks the registration SMS code. In "log" mode the backend's own code is written to
 * the server log (local testing, nothing sent). In "twilio-verify" mode Twilio Verify sends and
 * checks the code over HTTPS, which also works on Render Free (no SMS ports needed).
 */
@Profile("v3")
@Component
public class V3SmsVerificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3SmsVerificationSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String LOG_PROVIDER = "local_log";
    public static final String TWILIO_PROVIDER = "twilio_verify";

    /** The provider could not send or check the code (not configured, trial over, outage...). */
    public static class SmsUnavailableException extends RuntimeException {
        public SmsUnavailableException(String message) {
            super(message);
        }
    }

    /**
     * Twilio refused for now (HTTP 429: too many sends or checks for this number). Unlike
     * {@link SmsUnavailableException} this must never fall back to email alone, or anyone could
     * skip the SMS code by requesting or guessing codes until Twilio says no.
     */
    public static class SmsRateLimitedException extends RuntimeException {
        public SmsRateLimitedException(String message) {
            super(message);
        }
    }

    /** One form POST to Twilio; returns the HTTP status and body. */
    @FunctionalInterface
    interface TwilioTransport {
        Response post(URI url, String basicAuth, String formBody) throws IOException, InterruptedException;
    }

    record Response(int status, String body) {
    }

    private final V3AuthProperties properties;
    private final TwilioTransport transport;

    @Autowired
    public V3SmsVerificationSender(V3AuthProperties properties) {
        this(properties, V3SmsVerificationSender::postWithHttpClient);
    }

    V3SmsVerificationSender(V3AuthProperties properties, TwilioTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    public boolean isEnabled() {
        return switch (mode()) {
            case "log" -> true;
            case "twilio-verify" -> hasText(properties.getTwilioAccountSid())
                    && hasText(properties.getTwilioAuthToken())
                    && hasText(properties.getTwilioVerifyServiceSid());
            default -> false;
        };
    }

    /** True when the backend generates and checks the code itself (log mode). */
    public boolean checksOwnCode() {
        return "log".equals(mode());
    }

    /** Sends the code to {@code phoneE164}; returns the provider name stored on the challenge. */
    public String send(String phoneE164, String ownCode) {
        if (!isEnabled()) {
            throw new SmsUnavailableException("SMS verification is not configured.");
        }
        if (checksOwnCode()) {
            LOGGER.warn("LOCAL-ONLY V3 contact verification code for {}: {}", maskPhone(phoneE164), ownCode);
            return LOG_PROVIDER;
        }
        Response response = twilio("/Verifications", "To=" + encode(phoneE164) + "&Channel=sms");
        rejectRateLimited(response, phoneE164);
        if (response.status() < 200 || response.status() >= 300) {
            LOGGER.error("Twilio Verify could not send to {}: HTTP {} {}", maskPhone(phoneE164),
                    response.status(), twilioError(response.body()));
            throw new SmsUnavailableException("The SMS code could not be sent.");
        }
        return TWILIO_PROVIDER;
    }

    /** Asks Twilio whether {@code code} is the code it sent to {@code phoneE164}. */
    public boolean checkWithProvider(String phoneE164, String code) {
        Response response = twilio("/VerificationCheck", "To=" + encode(phoneE164) + "&Code=" + encode(code));
        if (response.status() == 404) {
            // Twilio forgets a verification once it is approved, expired or out of attempts.
            return false;
        }
        rejectRateLimited(response, phoneE164);
        if (response.status() < 200 || response.status() >= 300) {
            LOGGER.error("Twilio Verify could not check {}: HTTP {} {}", maskPhone(phoneE164),
                    response.status(), twilioError(response.body()));
            throw new SmsUnavailableException("The SMS code could not be checked.");
        }
        try {
            return "approved".equalsIgnoreCase(JSON.readTree(response.body()).path("status").asText());
        } catch (IOException exception) {
            throw new SmsUnavailableException("The SMS check response could not be read.");
        }
    }

    /** "+639171234567" -> "+63 9** *** 4567". */
    public static String maskPhone(String phoneE164) {
        if (phoneE164 == null || phoneE164.length() < 8) {
            return "***";
        }
        String last4 = phoneE164.substring(phoneE164.length() - 4);
        return phoneE164.startsWith("+63") ? "+63 9** *** " + last4 : "*** *** " + last4;
    }

    private Response twilio(String path, String formBody) {
        String credentials = properties.getTwilioAccountSid().trim() + ":" + properties.getTwilioAuthToken().trim();
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        URI url = URI.create(properties.getTwilioVerifyUrl().replaceAll("/+$", "")
                + "/Services/" + properties.getTwilioVerifyServiceSid().trim() + path);
        try {
            return transport.post(url, basicAuth, formBody);
        } catch (IOException exception) {
            LOGGER.error("Twilio Verify request failed: {}", exception.toString());
            throw new SmsUnavailableException("The SMS service could not be reached.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SmsUnavailableException("The SMS request was interrupted.");
        }
    }

    private static void rejectRateLimited(Response response, String phoneE164) {
        if (response.status() == 429) {
            LOGGER.warn("Twilio Verify rate-limited {}: {}", maskPhone(phoneE164), twilioError(response.body()));
            throw new SmsRateLimitedException("Too many SMS codes were requested or tried for this number.");
        }
    }

    private static String twilioError(String body) {
        try {
            JsonNode node = JSON.readTree(body == null ? "" : body);
            return "code " + node.path("code").asText("?") + ": " + node.path("message").asText("");
        } catch (IOException exception) {
            return "(unreadable error body)";
        }
    }

    private static Response postWithHttpClient(URI url, String basicAuth, String formBody)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
