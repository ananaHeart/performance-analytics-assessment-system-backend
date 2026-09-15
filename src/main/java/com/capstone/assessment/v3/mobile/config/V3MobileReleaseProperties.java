package com.capstone.assessment.v3.mobile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.v3.mobile")
public class V3MobileReleaseProperties {

    private boolean scanRecoveryEnabled;
    private boolean finalizationEnabled;
    private boolean readbackEnabled;
    private boolean evaluationReferenceEnabled;
    private boolean reopenEnabled;
    private boolean correctionEnabled;
    private boolean supersedeEnabled;
    private final Release release = new Release();

    public boolean isScanRecoveryEnabled() { return scanRecoveryEnabled; }
    public void setScanRecoveryEnabled(boolean value) { this.scanRecoveryEnabled = value; }
    public boolean isFinalizationEnabled() { return finalizationEnabled; }
    public void setFinalizationEnabled(boolean value) { this.finalizationEnabled = value; }
    public boolean isReadbackEnabled() { return readbackEnabled; }
    public void setReadbackEnabled(boolean value) { this.readbackEnabled = value; }
    public boolean isEvaluationReferenceEnabled() { return evaluationReferenceEnabled; }
    public void setEvaluationReferenceEnabled(boolean value) { this.evaluationReferenceEnabled = value; }
    public boolean isReopenEnabled() { return reopenEnabled; }
    public void setReopenEnabled(boolean value) { this.reopenEnabled = value; }
    public boolean isCorrectionEnabled() { return correctionEnabled; }
    public void setCorrectionEnabled(boolean value) { this.correctionEnabled = value; }
    public boolean isSupersedeEnabled() { return supersedeEnabled; }
    public void setSupersedeEnabled(boolean value) { this.supersedeEnabled = value; }
    public Release getRelease() { return release; }

    public boolean isWriteApiEnabled() {
        return release.profileEnabled && release.httpEnabled;
    }

    public static class Release {
        private boolean profileEnabled;
        private boolean httpEnabled;
        private String mode = "development";
        private String publicBaseUrl = "";
        private String contractPack = "1.11.0";
        private boolean adbReverseEnabled;
        private boolean mobileWiringVerified;
        private boolean physicalScannerVerified;

        public boolean isProfileEnabled() { return profileEnabled; }
        public void setProfileEnabled(boolean value) { this.profileEnabled = value; }
        public boolean isHttpEnabled() { return httpEnabled; }
        public void setHttpEnabled(boolean value) { this.httpEnabled = value; }
        public String getMode() { return mode; }
        public void setMode(String value) { this.mode = value; }
        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String value) { this.publicBaseUrl = value; }
        public String getContractPack() { return contractPack; }
        public void setContractPack(String value) { this.contractPack = value; }
        public boolean isAdbReverseEnabled() { return adbReverseEnabled; }
        public void setAdbReverseEnabled(boolean value) { this.adbReverseEnabled = value; }
        public boolean isMobileWiringVerified() { return mobileWiringVerified; }
        public void setMobileWiringVerified(boolean value) { this.mobileWiringVerified = value; }
        public boolean isPhysicalScannerVerified() { return physicalScannerVerified; }
        public void setPhysicalScannerVerified(boolean value) { this.physicalScannerVerified = value; }
    }
}
