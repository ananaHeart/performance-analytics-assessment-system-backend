package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
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

    private V3AuthProperties twilio() {
        V3AuthProperties properties = new V3AuthProperties();
        properties.setSmsMode("twilio-verify");
        properties.setTwilioAccountSid("ACtest");
        properties.setTwilioAuthToken("secret");
        properties.setTwilioVerifyServiceSid("VAtest");
        return properties;
    }

    private V3SmsVerificationSender sender(V3AuthProperties properties, int status, String body) {
        return new V3SmsVerificationSender(properties, (url, auth, form) -> {
            calls.add(new Call(url, auth, form));
            return new V3SmsVerificationSender.Response(status, body);
        });
    }

    @Test
    void offByDefaultAndTwilioNeedsAllThreeValues() {
        assertThat(new V3SmsVerificationSender(new V3AuthProperties()).isEnabled()).isFalse();
        V3AuthProperties incomplete = twilio();
        incomplete.setTwilioVerifyServiceSid(" ");
        assertThat(new V3SmsVerificationSender(incomplete).isEnabled()).isFalse();
        assertThat(new V3SmsVerificationSender(twilio()).isEnabled()).isTrue();
    }

    @Test
    void logModeSendsNothingAndChecksItsOwnCode() {
        V3AuthProperties properties = new V3AuthProperties();
        properties.setSmsMode("log");
        V3SmsVerificationSender sender = sender(properties, 500, "");

        assertThat(sender.isEnabled()).isTrue();
        assertThat(sender.checksOwnCode()).isTrue();
        assertThat(sender.send("+639171234567", "123456")).isEqualTo(V3SmsVerificationSender.LOG_PROVIDER);
        assertThat(calls).isEmpty();
    }

    @Test
    void twilioSendStartsAnSmsVerificationWithBasicAuth() {
        V3SmsVerificationSender sender = sender(twilio(), 201, "{\"sid\":\"VE1\",\"status\":\"pending\"}");

        assertThat(sender.send("+639171234567", "ignored")).isEqualTo(V3SmsVerificationSender.TWILIO_PROVIDER);

        Call call = calls.get(0);
        assertThat(call.url().toString()).isEqualTo("https://verify.twilio.com/v2/Services/VAtest/Verifications");
        assertThat(call.auth()).isEqualTo("Basic "
                + Base64.getEncoder().encodeToString("ACtest:secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(call.body()).isEqualTo("To=%2B639171234567&Channel=sms");
    }

    @Test
    void aTwilioRejectionOrOutageMeansSmsIsUnavailable() {
        assertThatThrownBy(() -> sender(twilio(), 403, "{\"code\":20003,\"message\":\"Trial ended\"}")
                .send("+639171234567", "x"))
                .isInstanceOf(V3SmsVerificationSender.SmsUnavailableException.class);
        V3SmsVerificationSender unreachable = new V3SmsVerificationSender(twilio(), (url, auth, form) -> {
            throw new IOException("no route");
        });
        assertThatThrownBy(() -> unreachable.send("+639171234567", "x"))
                .isInstanceOf(V3SmsVerificationSender.SmsUnavailableException.class);
    }

    @Test
    void twilioRateLimitIsNotTreatedAsAnOutage() {
        assertThatThrownBy(() -> sender(twilio(), 429, "{\"code\":60203}").send("+639171234567", "x"))
                .isInstanceOf(V3SmsVerificationSender.SmsRateLimitedException.class);
        assertThatThrownBy(() -> sender(twilio(), 429, "{\"code\":60202}").checkWithProvider("+639171234567", "1"))
                .isInstanceOf(V3SmsVerificationSender.SmsRateLimitedException.class);
    }

    @Test
    void twilioCheckIsTrueOnlyWhenApproved() {
        assertThat(sender(twilio(), 200, "{\"status\":\"approved\",\"valid\":true}")
                .checkWithProvider("+639171234567", "123456")).isTrue();
        assertThat(sender(twilio(), 200, "{\"status\":\"pending\",\"valid\":false}")
                .checkWithProvider("+639171234567", "000000")).isFalse();
        // Twilio returns 404 once a verification is approved, expired or out of attempts.
        assertThat(sender(twilio(), 404, "{\"code\":20404}")
                .checkWithProvider("+639171234567", "123456")).isFalse();
        assertThat(calls.get(0).url().toString())
                .isEqualTo("https://verify.twilio.com/v2/Services/VAtest/VerificationCheck");
        assertThat(calls.get(0).body()).isEqualTo("To=%2B639171234567&Code=123456");
        assertThatThrownBy(() -> sender(twilio(), 500, "").checkWithProvider("+639171234567", "123456"))
                .isInstanceOf(V3SmsVerificationSender.SmsUnavailableException.class);
    }

    @Test
    void phoneNumbersAreMasked() {
        assertThat(V3SmsVerificationSender.maskPhone("+639171234567")).isEqualTo("+63 9** *** 4567");
        assertThat(V3SmsVerificationSender.maskPhone(null)).isEqualTo("***");
    }
}
