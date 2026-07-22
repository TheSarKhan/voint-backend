package com.starsoft.voint.analytics.dto;

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
        /** resolvedCalls / totalCalls, 0.0 when there are no calls. */
        double resolutionRate,
        double avgDurationSeconds
) {
}
