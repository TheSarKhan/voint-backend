package com.starsoft.voint.analytics.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Runtime-computed analytics for a tenant.
 * DESIGN NOTE: analytics are computed on the fly from the calls table -
 * there is intentionally NO analytics snapshot table at this stage.
 */
public record AnalyticsResponse(
        UUID tenantId,
        long totalCalls,
        long resolvedCalls,
        long handoffCalls,
        long ongoingCalls,
        long reservationCount,
        /** resolvedCalls / totalCalls, 0.0 when there are no calls. */
        double resolutionRate,
        double avgDurationSeconds,
        /** Call counts for the last 7 days (oldest first), zero-filled for days with no calls. */
        List<DayCount> callsByDay
) {
    public record DayCount(LocalDate date, long count) {
    }
}
