package com.capstone.assessment.v2.auth.service;

import com.capstone.assessment.v2.auth.config.V2EmailVerificationProperties;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;

@Profile("v2")
@Component
public class V2VerificationEmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(V2VerificationEmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final V2EmailVerificationProperties properties;

    public V2VerificationEmailSender(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            V2EmailVerificationProperties properties
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    public void sendVerificationCode(String recipient, String otp) {
        if ("log".equalsIgnoreCase(properties.getDeliveryMode())) {
            LOGGER.warn("LOCAL-ONLY teacher email verification code for {}: {}", recipient, otp);
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new V2AuthException(
                    "EMAIL_DELIVERY_UNAVAILABLE",
                    "Email delivery is not configured.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getFromAddress(), "SMART Assessment System");
            helper.setTo(recipient);
            helper.setSubject("Verify your SMART Assessment teacher account");
            helper.setText(plainText(otp), htmlText(otp));
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            LOGGER.error("Teacher verification email delivery failed for {}.", recipient, exception);
            throw new V2AuthException(
                    "EMAIL_DELIVERY_FAILED",
                    "The verification email could not be sent. Please try again shortly.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
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
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Verify your SMART Assessment teacher account</title>
                </head>
                <body style="margin:0;padding:0;background:#f3f6f2;font-family:Arial,Helvetica,sans-serif;color:#0b1739;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;background:#f3f6f2;">
                    <tr>
                      <td align="center" style="padding:32px 16px;">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:600px;background:#ffffff;border:1px solid #dce5da;">
                          <tr>
                            <td style="padding:24px 32px;background:#173f2b;color:#ffffff;">
                              <div style="font-size:20px;font-weight:700;line-height:1.3;">SMART Assessment</div>
                              <div style="margin-top:4px;font-size:13px;line-height:1.4;color:#dcebd7;">Assessment System</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:36px 32px 20px;">
                              <div style="font-size:13px;font-weight:700;line-height:1.4;color:#2d7d24;text-transform:uppercase;">Teacher email verification</div>
                              <h1 style="margin:10px 0 12px;font-size:28px;line-height:1.25;color:#0b1739;">Verify your email address</h1>
                              <p style="margin:0;font-size:16px;line-height:1.6;color:#4d5d73;">Enter this verification code in SMART Assessment to continue your teacher registration.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 32px 24px;">
                              <div style="padding:22px;text-align:center;background:#eef9e8;border:1px solid #b9e5a7;">
                                <div style="font-size:13px;font-weight:700;line-height:1.4;color:#41613c;">Your verification code</div>
                                <div style="margin-top:8px;font-family:'Courier New',monospace;font-size:38px;font-weight:700;line-height:1.2;color:#176b21;">{{OTP}}</div>
                                <div style="margin-top:10px;font-size:13px;line-height:1.4;color:#526b50;">Expires in {{MINUTES}} minutes</div>
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 32px 36px;">
                              <p style="margin:0 0 10px;font-size:14px;line-height:1.6;color:#4d5d73;"><strong style="color:#0b1739;">Keep this code private.</strong> SMART Assessment staff will never ask you to send this code by message or phone.</p>
                              <p style="margin:0;font-size:14px;line-height:1.6;color:#6b7788;">If you did not request this registration, you can safely ignore this email.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px;background:#f7f9f7;border-top:1px solid #e3e9e2;font-size:12px;line-height:1.5;color:#738071;">
                              This is an automated transactional message from SMART Assessment System. Please do not reply.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{OTP}}", otp)
                .replace("{{MINUTES}}", String.valueOf(properties.getOtpTtl().toMinutes()));
    }
}
