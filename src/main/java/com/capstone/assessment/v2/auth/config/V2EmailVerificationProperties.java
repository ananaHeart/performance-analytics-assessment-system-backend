package com.capstone.assessment.v2.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.v2.email-verification")
public class V2EmailVerificationProperties {

    private Duration otpTtl = Duration.ofMinutes(10);
    private Duration resendCooldown = Duration.ofMinutes(1);
    private int maxAttempts = 5;
    private String deliveryMode = "log";
    private String fromAddress = "no-reply@smart-assessment.local";

    public Duration getOtpTtl() { return otpTtl; }
    public void setOtpTtl(Duration otpTtl) { this.otpTtl = otpTtl; }
    public Duration getResendCooldown() { return resendCooldown; }
    public void setResendCooldown(Duration resendCooldown) { this.resendCooldown = resendCooldown; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
}
