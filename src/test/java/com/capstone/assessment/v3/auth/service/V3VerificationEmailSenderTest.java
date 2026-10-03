package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** brevo-api mode: the same verification email over HTTPS, for hosts that block SMTP. */
class V3VerificationEmailSenderTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> mailSender = mock(ObjectProvider.class);

    private V3AuthProperties brevoProperties() {
        V3AuthProperties properties = new V3AuthProperties();
        properties.setEmailDeliveryMode("brevo-api");
        properties.setBrevoApiKey("  synthetic-brevo-key  ");
        properties.setEmailFromAddress("smart@example.invalid");
        return properties;
    }

    @Test
    void sendsTheVerificationEmailThroughTheBrevoApiWithoutTouchingSmtp() throws Exception {
        AtomicReference<URI> url = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        V3VerificationEmailSender sender = new V3VerificationEmailSender(mailSender, brevoProperties(), (u, k, b) -> {
            url.set(u);
            key.set(k);
            body.set(b);
            return 201;
        });

        assertEquals("brevo_api", sender.sendVerificationCode("teacher@example.invalid", "482915"));

        assertEquals(URI.create("https://api.brevo.com/v3/smtp/email"), url.get());
        assertEquals("synthetic-brevo-key", key.get(), "key is trimmed");
        JsonNode json = new ObjectMapper().readTree(body.get());
        assertEquals("smart@example.invalid", json.at("/sender/email").asText());
        assertEquals("teacher@example.invalid", json.at("/to/0/email").asText());
        assertEquals("Verify your Marka teacher account", json.get("subject").asText());
        assertTrue(json.get("textContent").asText().contains("482915"));
        assertTrue(json.get("htmlContent").asText().contains("482915"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void aRejectedOrUnreachableBrevoRequestIsReportedAsADeliveryFailure() {
        V3VerificationEmailSender rejected = new V3VerificationEmailSender(mailSender, brevoProperties(), (u, k, b) -> 401);
        V3VerificationEmailSender unreachable = new V3VerificationEmailSender(mailSender, brevoProperties(), (u, k, b) -> {
            throw new IOException("connect timed out");
        });

        assertEquals("EMAIL_DELIVERY_FAILED", assertThrows(V3AuthException.class,
                () -> rejected.sendVerificationCode("teacher@example.invalid", "482915")).getCode());
        assertEquals("EMAIL_DELIVERY_FAILED", assertThrows(V3AuthException.class,
                () -> unreachable.sendVerificationCode("teacher@example.invalid", "482915")).getCode());
    }

    @Test
    void brevoModeWithoutAKeyIsUnavailableRatherThanSilentlySkipped() {
        V3AuthProperties properties = brevoProperties();
        properties.setBrevoApiKey(" ");
        V3VerificationEmailSender sender = new V3VerificationEmailSender(mailSender, properties, (u, k, b) -> 201);

        assertEquals("EMAIL_DELIVERY_UNAVAILABLE", assertThrows(V3AuthException.class,
                () -> sender.sendVerificationCode("teacher@example.invalid", "482915")).getCode());
    }
}
