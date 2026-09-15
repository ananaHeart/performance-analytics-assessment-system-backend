package com.capstone.assessment.v2.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("v2")
@Component
@ConfigurationProperties(prefix = "app.v2.analytics")
public class V2AnalyticsProperties {

    private double maintainThreshold = 80;
    private double reviewThreshold = 60;
    private double reteachThreshold = 40;
    private double easyItemThreshold = 80;
    private double moderateItemThreshold = 50;

    public double getMaintainThreshold() {
        return maintainThreshold;
    }

    public void setMaintainThreshold(double maintainThreshold) {
        this.maintainThreshold = maintainThreshold;
    }

    public double getReviewThreshold() {
        return reviewThreshold;
    }

    public void setReviewThreshold(double reviewThreshold) {
        this.reviewThreshold = reviewThreshold;
    }

    public double getReteachThreshold() {
        return reteachThreshold;
    }

    public void setReteachThreshold(double reteachThreshold) {
        this.reteachThreshold = reteachThreshold;
    }

    public double getEasyItemThreshold() {
        return easyItemThreshold;
    }

    public void setEasyItemThreshold(double easyItemThreshold) {
        this.easyItemThreshold = easyItemThreshold;
    }

    public double getModerateItemThreshold() {
        return moderateItemThreshold;
    }

    public void setModerateItemThreshold(double moderateItemThreshold) {
        this.moderateItemThreshold = moderateItemThreshold;
    }
}
