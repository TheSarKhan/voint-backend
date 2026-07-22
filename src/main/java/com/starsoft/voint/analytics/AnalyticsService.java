package com.starsoft.voint.analytics;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.analytics.dto.AnalyticsResponse;
import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.call.CallStatus;

import lombok.RequiredArgsConstructor;

/**
 * Analytics are computed at runtime from the calls table (no snapshot table by design;
 * a materialized/snapshot approach can be introduced later if volume requires it).
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final CallRepository callRepository;

    @Transactional(readOnly = true)
    public AnalyticsResponse forTenant(UUID tenantId) {
        long total = callRepository.countByTenantId(tenantId);
        long resolved = callRepository.countByTenantIdAndStatus(tenantId, CallStatus.RESOLVED);
        long handoff = callRepository.countByTenantIdAndStatus(tenantId, CallStatus.HANDOFF);
        long ongoing = callRepository.countByTenantIdAndStatus(tenantId, CallStatus.ONGOING);
        Double avgDuration = callRepository.averageDurationSeconds(tenantId);

        double resolutionRate = total > 0 ? (double) resolved / total : 0.0;

        return new AnalyticsResponse(
                tenantId,
                total,
                resolved,
                handoff,
                ongoing,
                resolutionRate,
                avgDuration != null ? avgDuration : 0.0);
    }
}
