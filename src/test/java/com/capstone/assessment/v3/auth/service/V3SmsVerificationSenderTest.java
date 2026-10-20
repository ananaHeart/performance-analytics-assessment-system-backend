package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V3SmsVerificationSenderTest {

    private record Call(URI url, String auth, String body) {
    }

    private final List<Call> calls = new ArrayList<>();

    private V3AuthProperties philsms() {
        V3AuthProperties properties = new V3AuthProperties();
        properties.setSmsMode("philsms");
        properties.setPhilsmsApiToken("token-123");
        properties.setPhilsmsSenderId("MARKA");
        return properties;
    }

    private V3SmsVerificationSender sender(V3AuthProperties properties, int status, String body) {
        return new V3SmsVerificationSender(properties, (url, auth, json) -> {
            calls.add(new Call(url, auth, json));
            return new V3SmsVerificationSender.Response(status, body);
        });
    }

    @Test
    void offByDefaultAndPhilSmsNeedsItsToken() {
        assertThat(new V3SmsVerificationSender(new V3AuthProperties()).isEnabled()).isFalse();
        V3AuthProperties noToken = philsms();
        noToken.setPhilsmsApiToken(" ");
        assertThat(new V3SmsVerificationSender(noToken).isEnabled()).isFalse();
        assertThat(new V3SmsVerificationSender(philsms()).isEnabled()).isTrue();
    }

    @Test
    void logModeSendsNothing() {
        V3AuthProperties properties = new V3AuthProperties();
        properties.setSmsMode("log");
        V3SmsVerificationSender sender = sender(properties, 500, "");

        assertThat(sender.isEnabled()).isTrue();
        assertThat(sender.send("+639171234567", "123456")).isEqualTo(V3SmsVerificationSender.LOG_PROVIDER);
        assertThat(calls).isEmpty();
    }

    @Test
    void philSmsGetsTheBackendsOwnCodeWithBearerAuth() throws Exception {
        V3SmsVerificationSender sender = sender(philsms(), 200, "{\"status\":\"success\",\"data\":{}}");

        assertThat(sender.send("+639171234567", "482913")).isEqualTo(V3SmsVerificationSender.PHILSMS_PROVIDER);

        Call call = calls.get(0);
        assertThat(call.url().toString()).isEqualTo("https://app.philsms.com/api/v3/sms/send");
        assertThat(call.auth()).isEqualTo("Bearer token-123");
        JsonNode body = new ObjectMapper().readTree(call.body());
        assertThat(body.path("recipient").asText()).isEqualTo("639171234567");
        assertThat(body.path("sender_id").asText()).isEqualTo("MARKA");
        assertThat(body.path("type").asText()).isEqualTo("plain");
        assertThat(body.path("message").asText())
                .isEqualTo("Your Marka verification code is 482913. It expires in 10 minutes. Do not share this code.");
    }

    @Test
    void anErrorStatusAnHttpFailureOrAnOutageMeansSmsIsUnavailable() {
        // PhilSMS can answer HTTP 200 with an error body, e.g. when the account has no credit.
        assertThatThrownBy(() -> sender(philsms(), 200, "{\"status\":\"error\",\"message\":\"Insufficient balance\"}")
                .send("+639171234567", "1"))
                .isInstanceOf(V3SmsVerificationSender.SmsUnavailableException.class);
        assertThatThrownBy(() -> sender(philsms(), 401, "{\"status\":\"error\",\"message\":\"Unauthenticated.\"}")
                .send("+639171234567", "1"))
                .isInstanceOf(V3SmsVerificationSender.SmsUnavailableException.class);
        V3SmsVerificationSender unreachable = new V3SmsVerificationSender(philsms(), (url, auth, json) -> {
            throw new IOException("no route");
        });
        assertThatThrownBy(() -> unreachable.send("+639171234567", "1"))
                .isInstanceOf(V3SmsVerificationSender.SmsUnavailableException.class);
    }

    @Test
    void aRateLimitIsNotTreatedAsAnOutage() {
        assertThatThrownBy(() -> sender(philsms(), 429, "{\"status\":\"error\",\"message\":\"Too Many Attempts.\"}")
                .send("+639171234567", "1"))
                .isInstanceOf(V3SmsVerificationSender.SmsRateLimitedException.class);
    }

    @Test
    void phoneNumbersAreMasked() {
        assertThat(V3SmsVerificationSender.maskPhone("+639171234567")).isEqualTo("+63 9** *** 4567");
        assertThat(V3SmsVerificationSender.maskPhone(null)).isEqualTo("***");
    }
}
