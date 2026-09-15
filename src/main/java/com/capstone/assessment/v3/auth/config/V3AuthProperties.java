package com.capstone.assessment.v3.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.v3.auth")
public class V3AuthProperties {

    private Duration sessionTtl = Duration.ofHours(8);
    private int maxFailedAttempts = 5;
    private Duration lockDuration = Duration.ofMinutes(15);
    private Duration otpTtl = Duration.ofMinutes(10);
    private Duration resendCooldown = Duration.ofMinutes(1);
    private int maxOtpAttempts = 5;
    private int maxVerificationMessagesPerDay = 5;
    private Duration registrationRetention = Duration.ofDays(30);
    private int cleanupBatchSize = 100;
    private boolean cleanupEnabled = true;
    private String emailDeliveryMode = "log";
    private String emailFromAddress = "no-reply@smart-assessment.local";

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    public Duration getOtpTtl() {
        return otpTtl;
    }

    public void setOtpTtl(Duration otpTtl) {
        this.otpTtl = otpTtl;
    }

    public Duration getResendCooldown() {
        return resendCooldown;
    }

    public void setResendCooldown(Duration resendCooldown) {
        this.resendCooldown = resendCooldown;
    }

    public int getMaxOtpAttempts() {
        return maxOtpAttempts;
    }

    public void setMaxOtpAttempts(int maxOtpAttempts) {
        this.maxOtpAttempts = maxOtpAttempts;
    }

    public int getMaxVerificationMessagesPerDay() {
        return maxVerificationMessagesPerDay;
    }

    public void setMaxVerificationMessagesPerDay(int maxVerificationMessagesPerDay) {
        this.maxVerificationMessagesPerDay = maxVerificationMessagesPerDay;
    }

    public Duration getRegistrationRetention() {
        return registrationRetention;
    }

    public void setRegistrationRetention(Duration registrationRetention) {
        this.registrationRetention = registrationRetention;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public String getEmailDeliveryMode() {
        return emailDeliveryMode;
    }

    public void setEmailDeliveryMode(String emailDeliveryMode) {
        this.emailDeliveryMode = emailDeliveryMode;
    }

    public String getEmailFromAddress() {
        return emailFromAddress;
    }

    public void setEmailFromAddress(String emailFromAddress) {
        this.emailFromAddress = emailFromAddress;
    }
}
