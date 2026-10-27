package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REG-7, TCH-7: a small in-memory limit for public endpoints, per client IP and endpoint.
 * It resets on restart, which is enough to stop casual probing of emails and numbers.
 */
@Profile("v3")
@Component
public class V3PublicRateLimiter {

    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final Clock clock;

    public V3PublicRateLimiter() {
        this(Clock.systemUTC());
    }

    V3PublicRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Throws 429 {code} with details.retryAfterSeconds when the key used up its calls in the window. */
    public void check(String key, int limit, Duration window, String code) {
        Instant now = clock.instant();
        Deque<Instant> times = calls.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(now.minus(window))) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                long retryAfter = Math.max(1, Duration.between(now, times.peekFirst().plus(window)).toSeconds());
                throw new V3AuthException(code, "Too many requests. Try again in a moment.",
                        HttpStatus.TOO_MANY_REQUESTS, Map.of("retryAfterSeconds", retryAfter));
            }
            times.addLast(now);
        }
        if (calls.size() > 10_000) {
            calls.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }
}
