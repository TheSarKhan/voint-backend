package com.starsoft.voint.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** The only ranges the dashboard's range picker offers - anything else falls back to 30. */
    private static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30, 90);
    private static final int DEFAULT_DAYS = 30;

    /** Above this many days a daily bar chart is too dense to read - bucket by week instead. */
    private static final int DAILY_BUCKET_THRESHOLD = 30;

    private final CallRepository callRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public AnalyticsResponse forTenant(UUID tenantId, Integer requestedDays) {
        int days = requestedDays != null && ALLOWED_DAYS.contains(requestedDays) ? requestedDays : DEFAULT_DAYS;
        Instant to = Instant.now();
        Instant from = to.minusSeconds(days * 24L * 3600);

        long total = callRepository.countByTenantIdAndStartedAtBetween(tenantId, from, to);
        long resolved = callRepository.countByTenantIdAndStatusAndStartedAtBetween(tenantId, CallStatus.RESOLVED, from, to);
        long handoff = callRepository.countByTenantIdAndStatusAndStartedAtBetween(tenantId, CallStatus.HANDOFF, from, to);
        long ongoing = callRepository.countByTenantIdAndStatusAndStartedAtBetween(tenantId, CallStatus.ONGOING, from, to);
        // All-time, deliberately not window-scoped: a business wants to know it has never
        // configured this feature, not that it had zero requests in the last 7 days.
        long reservations = reservationRepository.countByTenantId(tenantId);
        Double avgDuration = callRepository.averageDurationSecondsBetween(tenantId, from, to);

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
                callsByDay(tenantId, days, from, to),
                days <= DAILY_BUCKET_THRESHOLD ? 1 : 7);
    }

    /**
     * Zero-filled call counts across the window: one point per day up to
     * {@value #DAILY_BUCKET_THRESHOLD} days, one point per week beyond that - the 90-day range
     * would otherwise be 90 barely-visible bars. {@code date} is always a bucket's first day,
     * whichever size the bucket is; the panel labels it accordingly.
     */
    private List<DayCount> callsByDay(UUID tenantId, int days, Instant from, Instant to) {
        List<Call> calls = callRepository.findByTenantIdAndStartedAtBetweenOrderByStartedAtDesc(tenantId, from, to);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        if (days <= DAILY_BUCKET_THRESHOLD) {
            Map<LocalDate, Long> counts = calls.stream()
                    .filter(c -> c.getStartedAt() != null)
                    .collect(Collectors.groupingBy(
                            c -> c.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                            Collectors.counting()));
            List<DayCount> trend = new ArrayList<>(days);
            for (int i = days - 1; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                trend.add(new DayCount(day, counts.getOrDefault(day, 0L)));
            }
            return trend;
        }

        // Weekly buckets, keyed by the Monday each call's date falls in.
        Map<LocalDate, Long> counts = calls.stream()
                .filter(c -> c.getStartedAt() != null)
                .collect(Collectors.groupingBy(
                        c -> weekStart(c.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate()),
                        Collectors.counting()));
        LocalDate firstWeek = weekStart(today.minusDays(days - 1L));
        LocalDate lastWeek = weekStart(today);
        List<DayCount> trend = new ArrayList<>();
        for (LocalDate week = firstWeek; !week.isAfter(lastWeek); week = week.plusWeeks(1)) {
            trend.add(new DayCount(week, counts.getOrDefault(week, 0L)));
        }
        return trend;
    }

    private LocalDate weekStart(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }
}
