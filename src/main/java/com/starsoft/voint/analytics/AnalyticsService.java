package com.starsoft.voint.analytics;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.analytics.dto.AnalyticsResponse;
import com.starsoft.voint.analytics.dto.AnalyticsResponse.DayCount;
import com.starsoft.voint.call.Call;
import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.call.CallStatus;
import com.starsoft.voint.reservation.ReservationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Analytics are computed at runtime from the calls table (no snapshot table by design;
 * a materialized/snapshot approach can be introduced later if volume requires it).
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int TREND_DAYS = 7;

    private final CallRepository callRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public AnalyticsResponse forTenant(UUID tenantId) {
        long total = callRepository.countByTenantId(tenantId);
        long resolved = callRepository.countByTenantIdAndStatus(tenantId, CallStatus.RESOLVED);
        long handoff = callRepository.countByTenantIdAndStatus(tenantId, CallStatus.HANDOFF);
        long ongoing = callRepository.countByTenantIdAndStatus(tenantId, CallStatus.ONGOING);
        long reservations = reservationRepository.countByTenantId(tenantId);
        Double avgDuration = callRepository.averageDurationSeconds(tenantId);

        double resolutionRate = total > 0 ? (double) resolved / total : 0.0;

        return new AnalyticsResponse(
                tenantId,
                total,
                resolved,
                handoff,
                ongoing,
                reservations,
                resolutionRate,
                avgDuration != null ? avgDuration : 0.0,
                callsByDay(tenantId));
    }

    /** Zero-filled call counts for the last {@value #TREND_DAYS} days (oldest first). */
    private List<DayCount> callsByDay(UUID tenantId) {
        Map<LocalDate, Long> counts = callRepository.findByTenantIdOrderByStartedAtDesc(tenantId).stream()
                .filter(c -> c.getStartedAt() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<DayCount> trend = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            trend.add(new DayCount(day, counts.getOrDefault(day, 0L)));
        }
        return trend;
    }
}
