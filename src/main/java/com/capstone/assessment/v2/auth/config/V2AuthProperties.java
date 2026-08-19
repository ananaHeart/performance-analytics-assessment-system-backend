package com.capstone.assessment.v2.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.v2.auth")
public record V2AuthProperties(
        Duration sessionTtl,
        int maxFailedAttempts,
        Duration lockDuration
) {
}
