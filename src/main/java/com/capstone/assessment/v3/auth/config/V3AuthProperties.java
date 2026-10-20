package com.capstone.assessment.v3.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.v3.auth")
public class V3AuthProperties {

    private Duration sessionTtl = Duration.ofHours(8);
    /** Off by default (school capstone): wrong passwords are still recorded in login_attempts,
     *  but never lock the account or rate-limit login. Every phone and browser on the laptop
     *  reaches the backend as 127.0.0.1, so an IP-based throttle locked out everyone at once. */
    private boolean loginLockoutEnabled = false;
    private int maxFailedAttempts = 5;
    private Duration lockDuration = Duration.ofMinutes(15);
    private Duration otpTtl = Duration.ofMinutes(10);
    private Duration resendCooldown = Duration.ofMinutes(1);
    private int maxOtpAttempts = 5;
    private int maxVerificationMessagesPerDay = 5;
    private Duration registrationRetention = Duration.ofDays(30);
    private int cleanupBatchSize = 100;
    private boolean cleanupEnabled = true;
    /** log (local only), smtp, or brevo-api (HTTPS - works where outbound SMTP ports are blocked). */
    private String emailDeliveryMode = "log";
    private String emailFromAddress = "no-reply@smart-assessment.local";
    private String brevoApiKey;
    private String brevoApiUrl = "https://api.brevo.com/v3/smtp/email";
    /**
     * Contact-number (SMS) verification at registration: off, log (local only: the code is written
     * to the server log, no SMS is sent) or philsms. While on, a teacher confirms both the
     * email code and the SMS code; when off or when an SMS can't be sent, registration falls back
     * to the email code alone and the contact number stays unverified.
     */
    private String smsMode = "off";
    private String philsmsApiToken;
    // Up to 11 letters; "PhilSMS" works before the school's own sender name is approved.
    private String philsmsSenderId = "PhilSMS";
    private String philsmsApiUrl = "https://app.philsms.com/api/v3";

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public boolean isLoginLockoutEnabled() {
        return loginLockoutEnabled;
    }

    public void setLoginLockoutEnabled(boolean loginLockoutEnabled) {
        this.loginLockoutEnabled = loginLockoutEnabled;
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

    public String getBrevoApiKey() {
        return brevoApiKey;
    }

    public void setBrevoApiKey(String brevoApiKey) {
        this.brevoApiKey = brevoApiKey;
    }

    public String getBrevoApiUrl() {
        return brevoApiUrl;
    }

    public void setBrevoApiUrl(String brevoApiUrl) {
        this.brevoApiUrl = brevoApiUrl;
    }

    public String getSmsMode() {
        return smsMode;
    }

    public void setSmsMode(String smsMode) {
        this.smsMode = smsMode;
    }

    public String getPhilsmsApiToken() {
        return philsmsApiToken;
    }

    public void setPhilsmsApiToken(String philsmsApiToken) {
        this.philsmsApiToken = philsmsApiToken;
    }

    public String getPhilsmsSenderId() {
        return philsmsSenderId;
    }

    public void setPhilsmsSenderId(String philsmsSenderId) {
        this.philsmsSenderId = philsmsSenderId;
    }

    public String getPhilsmsApiUrl() {
        return philsmsApiUrl;
    }

    public void setPhilsmsApiUrl(String philsmsApiUrl) {
        this.philsmsApiUrl = philsmsApiUrl;
    }
}
