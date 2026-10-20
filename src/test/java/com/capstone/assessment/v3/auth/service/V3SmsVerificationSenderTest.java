package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V3SmsVerificationSenderTest {

    private record Call(URI url, String auth, String body) {
    }

    private final List<Call> calls = new ArrayList<>();

    private V3AuthProperties smsgate() {
        V3AuthProperties properties = new V3AuthProperties();
        properties.setSmsMode("smsgate");
        properties.setSmsgateUsername("GATEUSER");
        properties.setSmsgatePassword("gate-pass");
        return properties;
    }

    private V3SmsVerificationSender sender(V3AuthProperties properties, int status, String body) {
        return new V3SmsVerificationSender(properties, (url, auth, json) -> {
            calls.add(new Call(url, auth, json));
            return new V3SmsVerificationSender.Response(status, body);
        });
    }

    @Test
    void offByDefaultAndTheGatewayNeedsItsLogin() {
        assertThat(new V3SmsVerificationSender(new V3AuthProperties()).isEnabled()).isFalse();
        V3AuthProperties noPassword = smsgate();
        noPassword.setSmsgatePassword(" ");
        assertThat(new V3SmsVerificationSender(noPassword).isEnabled()).isFalse();
        assertThat(new V3SmsVerificationSender(smsgate()).isEnabled()).isTrue();
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
    void theGatewayGetsTheBackendsOwnCodeWithBasicAuth() throws Exception {
        V3SmsVerificationSender sender = sender(smsgate(), 202, "{\"id\":\"msg-1\",\"state\":\"Pending\"}");

        assertThat(sender.send("+639171234567", "482913")).isEqualTo(V3SmsVerificationSender.SMSGATE_PROVIDER);

        Call call = calls.get(0);
        assertThat(call.url().toString()).isEqualTo("https://api.sms-gate.app/3rdparty/v1/messages");
        assertThat(call.auth()).isEqualTo("Basic "
                + Base64.getEncoder().encodeToString("GATEUSER:gate-pass".getBytes(StandardCharsets.UTF_8)));
        JsonNode body = new ObjectMapper().readTree(call.body());
        assertThat(body.path("phoneNumbers").get(0).asText()).isEqualTo("+639171234567");
        assertThat(body.path("textMessage").path("text").asText())
                .isEqualTo("Your Marka verification code is 482913. It expires in 10 minutes. Do not share this code.");
    }

    @Test
    void aRejectedLoginOrAnOutageMeansSmsIsUnavailable() {
        assertThatThrownBy(() -> sender(smsgate(), 401, "{\"message\":\"Unauthorized\"}").send("+639171234567", "1"))
                .isInstanceOf(V3SmsVerificationSender.SmsUnavailableException.class);
        V3SmsVerificationSender unreachable = new V3SmsVerificationSender(smsgate(), (url, auth, json) -> {
            throw new IOException("no route");
        });
        assertThatThrownBy(() -> unreachable.send("+639171234567", "1"))
                .isInstanceOf(V3SmsVerificationSender.SmsUnavailableException.class);
    }

    @Test
    void aRateLimitIsNotTreatedAsAnOutage() {
        assertThatThrownBy(() -> sender(smsgate(), 429, "{\"message\":\"Too Many Requests\"}")
                .send("+639171234567", "1"))
                .isInstanceOf(V3SmsVerificationSender.SmsRateLimitedException.class);
    }

    @Test
    void phoneNumbersAreMasked() {
        assertThat(V3SmsVerificationSender.maskPhone("+639171234567")).isEqualTo("+63 9** *** 4567");
        assertThat(V3SmsVerificationSender.maskPhone(null)).isEqualTo("***");
    }
}
