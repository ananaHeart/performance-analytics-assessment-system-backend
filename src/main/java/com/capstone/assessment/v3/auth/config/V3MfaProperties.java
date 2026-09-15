package com.capstone.assessment.v3.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.v3.auth.mfa")
public class V3MfaProperties {

    private boolean enabled = true;
    private String issuer = "SMART Assessment";
    private Duration challengeTtl = Duration.ofMinutes(5);
    private int maxAttempts = 5;
    private int recoveryCodeCount = 10;
    private int verificationWindow = 1;
    private int secretKeyVersion = 1;
    private int qrCodeSize = 240;
    private String encryptionKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getChallengeTtl() {
        return challengeTtl;
    }

    public void setChallengeTtl(Duration challengeTtl) {
        this.challengeTtl = challengeTtl;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRecoveryCodeCount() {
        return recoveryCodeCount;
    }

    public void setRecoveryCodeCount(int recoveryCodeCount) {
        this.recoveryCodeCount = recoveryCodeCount;
    }

    public int getVerificationWindow() {
        return verificationWindow;
    }

    public void setVerificationWindow(int verificationWindow) {
        this.verificationWindow = verificationWindow;
    }

    public int getSecretKeyVersion() {
        return secretKeyVersion;
    }

    public void setSecretKeyVersion(int secretKeyVersion) {
        this.secretKeyVersion = secretKeyVersion;
    }

    public int getQrCodeSize() {
        return qrCodeSize;
    }

    public void setQrCodeSize(int qrCodeSize) {
        this.qrCodeSize = qrCodeSize;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
