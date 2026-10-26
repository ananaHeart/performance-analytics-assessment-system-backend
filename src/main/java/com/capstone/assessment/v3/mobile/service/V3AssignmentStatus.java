package com.capstone.assessment.v3.mobile.service;

import java.time.Instant;
import java.util.Locale;

/**
 * The stored status of a test assignment is set once, at activation: 'planned' when its opening
 * time is still ahead. Nothing moves it to 'open' later, so from the opening time on a 'planned'
 * assignment is read as 'open'. Without this, an assessment activated before its opening time
 * could be scanned but its uploads were refused as outside the capture policy (BUG-1).
 */
final class V3AssignmentStatus {

    private V3AssignmentStatus() {
    }

    /** The status that applies at {@code at}: 'open' for a 'planned' assignment past its opening time. */
    static String effective(String storedStatus, Instant openAt, Instant at) {
        String status = storedStatus == null ? "" : storedStatus.toLowerCase(Locale.ROOT);
        if ("planned".equals(status) && openAt != null && !at.isBefore(openAt)) {
            return "open";
        }
        return status;
    }
}
