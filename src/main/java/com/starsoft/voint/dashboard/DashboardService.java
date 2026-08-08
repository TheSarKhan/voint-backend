package com.starsoft.voint.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.approval.ApprovalRequestRepository;
import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.dashboard.dto.DashboardResponse;
import com.starsoft.voint.dashboard.dto.DashboardResponse.DayCount;
import com.starsoft.voint.dashboard.dto.DashboardResponse.RecentCall;
import com.starsoft.voint.dashboard.dto.DashboardResponse.TenantMargin;
import com.starsoft.voint.lead.LeadService;
import com.starsoft.voint.question.QuestionStatus;
import com.starsoft.voint.question.UnansweredQuestionRepository;
import com.starsoft.voint.tenant.Tenant;
import com.starsoft.voint.tenant.TenantRepository;
import com.starsoft.voint.usage.BillingRates;
import com.starsoft.voint.usage.UsageService;
import com.starsoft.voint.usage.dto.UsageReport;

import lombok.RequiredArgsConstructor;

/**
 * Assembles the platform-wide admin dashboard from data that already exists elsewhere.
 *
 * <p>Deliberately not a new source of truth: the money figures are {@link UsageService}'s own
 * per-tenant reports summed here, not recomputed - two places pricing a call differently is exactly
 * the kind of drift that makes an invoice indefensible later. Everything else is a bounded read
 * over {@code calls} / {@code approval_requests} / {@code unanswered_questions}, in the same
 * "compute at request time, no snapshot table" style the rest of this codebase already uses at
 * this scale (see {@code AnalyticsService}'s design note).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** Matches the per-tenant "Günlük zəng sayı" chart on the business detail page: 30, not 7 -
     *  a platform trend is read weekly, not daily, so a month of context reads better than a week. */
    private static final int TREND_DAYS = 30;

    private static final int TOP_TENANTS = 10;
    private static final int RECENT_CALLS = 10;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TenantRepository tenantRepository;
    private final CallRepository callRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final UnansweredQuestionRepository unansweredQuestionRepository;
    private final LeadService leadService;
    private final UsageService usageService;
    private final BillingRates rates;

    @Transactional(readOnly = true)
    public DashboardResponse get() {
        List<Tenant> tenants = tenantRepository.findAll();
        Map<java.util.UUID, String> tenantNames = tenants.stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getName));

        // Reuses UsageService's own pricing, not a second copy of it - see the class javadoc.
        List<UsageReport> reports = usageService.reportForAll(null);
        BigDecimal invoice = sum(reports, UsageReport::invoiceAzn);
        BigDecimal cost = sum(reports, r -> r.cost().total());
        BigDecimal margin = sum(reports, UsageReport::marginAzn);
        long callsThisMonth = reports.stream().mapToLong(r -> r.usage().calls()).sum();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long callsToday = callRepository.countByStartedAtBetween(startOfToday, startOfTomorrow);

        return new DashboardResponse(
                tenants.size(),
                callsToday,
                callsThisMonth,
                invoice,
                cost,
                margin,
                marginPercent(margin, invoice),
                approvalRequestRepository.countByStatus("PENDING"),
                unansweredQuestionRepository.countByStatus(QuestionStatus.OPEN),
                leadService.countNew(),
                callsByDay(today),
                recentCalls(tenantNames),
                topTenants(reports));
    }

    /** Zero-filled so the chart doesn't silently skip a day nothing happened on. */
    private List<DayCount> callsByDay(LocalDate today) {
        Instant windowStart = today.minusDays(TREND_DAYS - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<LocalDate, Long> counts = callRepository.findByStartedAtAfterOrderByStartedAtDesc(windowStart).stream()
                .filter(c -> c.getStartedAt() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()));

        List<DayCount> trend = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            trend.add(new DayCount(day, counts.getOrDefault(day, 0L)));
        }
        return trend;
    }

    private List<RecentCall> recentCalls(Map<java.util.UUID, String> tenantNames) {
        return callRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(c -> new RecentCall(c.getId(), c.getTenantId(),
                        tenantNames.getOrDefault(c.getTenantId(), "?"),
                        c.getCallerNumber(), c.getStatus(), c.getStartedAt(), c.getDurationSeconds()))
                .limit(RECENT_CALLS)
                .toList();
    }

    /** {@code reports} already arrives sorted biggest-invoice-first (see UsageService.reportForAll). */
    private List<TenantMargin> topTenants(List<UsageReport> reports) {
        return reports.stream()
                .limit(TOP_TENANTS)
                .map(r -> new TenantMargin(r.tenantId(), r.tenantName(), r.usage().calls(),
                        r.invoiceAzn(), r.marginAzn(), r.marginPercent()))
                .toList();
    }

    private BigDecimal sum(List<UsageReport> reports, Function<UsageReport, BigDecimal> field) {
        return reports.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Same formula as UsageService's per-tenant margin% (invoice==0 -> null, else scale-1 HALF_UP)
     *  - duplicated rather than shared because it is three lines, and the two call sites (one
     *  tenant's report vs. the platform sum) have no other reason to depend on each other. */
    private BigDecimal marginPercent(BigDecimal margin, BigDecimal invoice) {
        return invoice.signum() == 0 ? null
                : margin.multiply(HUNDRED).divide(invoice, 1, RoundingMode.HALF_UP);
    }
}
