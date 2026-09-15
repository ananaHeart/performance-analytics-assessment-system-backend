package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

@Profile("v3")
@Component
public class V3VerificationEmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(V3VerificationEmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final V3AuthProperties properties;

    public V3VerificationEmailSender(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            V3AuthProperties properties
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    public String sendVerificationCode(String recipient, String otp) {
        String mode = properties.getEmailDeliveryMode().trim().toLowerCase(Locale.ROOT);
        if ("log".equals(mode)) {
            LOGGER.warn("LOCAL-ONLY V3 teacher verification code for {}: {}", maskEmail(recipient), otp);
            return "local_log";
        }
        if (!"smtp".equals(mode)) {
            throw unavailable("V3 email delivery mode must be either log or smtp.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw unavailable("Email delivery is not configured.");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getEmailFromAddress(), "SMART Assessment System");
            helper.setTo(recipient);
            helper.setSubject("Verify your SMART Assessment teacher account");
            helper.setText(plainText(otp), htmlText(otp));
            mailSender.send(message);
            return "smtp";
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            LOGGER.error("V3 teacher verification email delivery failed for {}.", maskEmail(recipient), exception);
            throw new V3AuthException(
                    "EMAIL_DELIVERY_FAILED",
                    "The verification email could not be sent. Please try again shortly.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private V3AuthException unavailable(String message) {
        return new V3AuthException("EMAIL_DELIVERY_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private String plainText(String otp) {
        return """
                SMART Assessment System

                Verify your teacher email address

                Your verification code is: %s

                This code expires in %d minutes. Do not share this code with anyone.
                If you did not request this registration, you may ignore this email.
                """.formatted(otp, properties.getOtpTtl().toMinutes());
    }

    private String htmlText(String otp) {
        return """
                <!doctype html>
                <html lang="en">
                <body style="margin:0;padding:0;background:#f3f6f2;font-family:Arial,Helvetica,sans-serif;color:#0b1739;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:#f3f6f2;">
                    <tr><td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:600px;background:#fff;border:1px solid #dce5da;">
                        <tr><td style="padding:24px 32px;background:#173f2b;color:#fff;">
                          <div style="font-size:20px;font-weight:700;">SMART Assessment</div>
                          <div style="margin-top:4px;font-size:13px;color:#dcebd7;">Assessment System</div>
                        </td></tr>
                        <tr><td style="padding:36px 32px 20px;">
                          <div style="font-size:13px;font-weight:700;color:#2d7d24;text-transform:uppercase;">Teacher email verification</div>
                          <h1 style="margin:10px 0 12px;font-size:28px;">Verify your email address</h1>
                          <p style="margin:0;font-size:16px;line-height:1.6;color:#4d5d73;">Enter this code in SMART Assessment to continue registration.</p>
                        </td></tr>
                        <tr><td style="padding:8px 32px 24px;">
                          <div style="padding:22px;text-align:center;background:#eef9e8;border:1px solid #b9e5a7;">
                            <div style="font-size:13px;font-weight:700;color:#41613c;">Your verification code</div>
                            <div style="margin-top:8px;font-family:'Courier New',monospace;font-size:38px;font-weight:700;color:#176b21;">%s</div>
                            <div style="margin-top:10px;font-size:13px;color:#526b50;">Expires in %d minutes</div>
                          </div>
                        </td></tr>
                        <tr><td style="padding:0 32px 36px;font-size:14px;line-height:1.6;color:#4d5d73;">
                          Keep this code private. SMART Assessment staff will never ask you to send it by message or phone.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(otp, properties.getOtpTtl().toMinutes());
    }

    static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        return local.substring(0, 1) + "***" + local.substring(local.length() - 1) + domain;
    }
}
