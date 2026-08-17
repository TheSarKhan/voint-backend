package com.starsoft.voint.billing;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.starsoft.voint.billing.dto.*;
import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.tenant.Tenant;
import com.starsoft.voint.tenant.TenantRepository;
import com.starsoft.voint.usage.TenantQuotaService;
import com.starsoft.voint.usage.UsageService;
import com.starsoft.voint.usage.dto.UsageReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Commercial catalogue and immutable monthly invoice snapshots. UsageService remains the pricing source. */
@Slf4j
@Service @RequiredArgsConstructor
public class BillingService {
    private final BillingPlanRepository plans;
    private final BillingInvoiceRepository invoices;
    private final TenantRepository tenants;
    private final UsageService usage;
    private final TenantQuotaService quota;

    @Transactional(readOnly = true)
    public List<BillingPlanResponse> listPlans() { return plans.findAllByOrderByActiveDescNameAsc().stream().map(BillingPlanResponse::from).toList(); }

    @Transactional
    public BillingPlanResponse createPlan(BillingPlanRequest r) {
        BillingPlan p = new BillingPlan(); p.setId(UUID.randomUUID()); apply(p, r); return BillingPlanResponse.from(plans.save(p));
    }

    @Transactional
    public BillingPlanResponse updatePlan(UUID id, BillingPlanRequest r) {
        BillingPlan p = plans.findById(id).orElseThrow(() -> NotFoundException.of("Billing plan", id));
        apply(p, r); return BillingPlanResponse.from(plans.save(p));
    }

    /**
     * Who is on this plan, what it actually brings in, and whether its minute cap is sized right
     * for the tenants that have it - the plan detail screen.
     */
    @Transactional(readOnly = true)
    public BillingPlanDetailResponse planDetail(UUID planId) {
        BillingPlan plan = plans.findById(planId).orElseThrow(() -> NotFoundException.of("Billing plan", planId));
        List<Tenant> onPlan = tenants.findByBillingPlanId(planId);
        Set<UUID> onPlanIds = onPlan.stream().map(Tenant::getId).collect(Collectors.toSet());

        Map<UUID, UsageReport> currentByTenant = usage.reportForAll(null).stream()
                .filter(rep -> onPlanIds.contains(rep.tenantId()))
                .collect(Collectors.toMap(UsageReport::tenantId, rep -> rep));

        BigDecimal currentRevenue = currentByTenant.values().stream()
                .map(UsageReport::invoiceAzn).reduce(BigDecimal.ZERO, BigDecimal::add);
        long currentCalls = currentByTenant.values().stream().mapToLong(rep -> rep.usage().calls()).sum();
        BigDecimal currentMinutes = currentByTenant.values().stream()
                .map(rep -> rep.usage().minutes()).reduce(BigDecimal.ZERO, BigDecimal::add);

        int ok = 0, warning = 0, blocked = 0;
        List<BillingPlanDetailResponse.PlanTenant> tenantRows = new ArrayList<>(onPlan.size());
        for (Tenant t : onPlan) {
            TenantQuotaService.Status status = quota.check(t).status();
            switch (status) {
                case OK -> ok++;
                case WARNING -> warning++;
                case BLOCKED -> blocked++;
            }
            UsageReport rep = currentByTenant.get(t.getId());
            tenantRows.add(new BillingPlanDetailResponse.PlanTenant(t.getId(), t.getName(), t.getSubdomain(),
                    status.name(), rep != null ? rep.invoiceAzn() : BigDecimal.ZERO,
                    rep != null ? rep.usage().calls() : 0));
        }
        tenantRows.sort(Comparator.comparing(BillingPlanDetailResponse.PlanTenant::name, String.CASE_INSENSITIVE_ORDER));

        List<BillingPlanDetailResponse.MonthRevenue> trend = new ArrayList<>(12);
        YearMonth cursor = YearMonth.now();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = cursor.minusMonths(i);
            trend.add(new BillingPlanDetailResponse.MonthRevenue(ym.toString(), revenueFor(ym, cursor, onPlanIds)));
        }

        return new BillingPlanDetailResponse(BillingPlanResponse.from(plan), onPlan.size(),
                currentRevenue, currentCalls, currentMinutes,
                new BillingPlanDetailResponse.QuotaBreakdown(ok, warning, blocked), trend, tenantRows);
    }

    @Transactional
    public Tenant updateProfile(UUID tenantId, BillingProfileRequest r) {
        Tenant tenant = tenant(tenantId);
        tenant.setBillingEnabled(r.billingEnabled()); tenant.setBillingLegalName(blankToNull(r.legalName()));
        tenant.setBillingTaxId(blankToNull(r.taxId())); tenant.setBillingEmail(blankToNull(r.email())); tenant.setBillingDueDays(r.dueDays());
        if (r.billingPlanId() != null) {
            BillingPlan p = plans.findById(r.billingPlanId()).orElseThrow(() -> NotFoundException.of("Billing plan", r.billingPlanId()));
            tenant.setBillingPlanId(p.getId());
            // Assignment copies current commercial terms. Later plan edits must never rewrite a negotiated tenant price silently.
            tenant.setMonthlyFee(p.getMonthlyFee()); tenant.setIncludedMinutes(p.getIncludedMinutes());
            tenant.setOveragePerMinute(p.getOveragePerMinute()); tenant.setMonthlyMinuteCap(p.getMonthlyMinuteCap()); tenant.setMaxConcurrentCalls(p.getMaxConcurrentCalls());
        } else tenant.setBillingPlanId(null);
        return tenants.save(tenant);
    }

    @Transactional(readOnly = true)
    public List<BillingInvoiceResponse> invoicesFor(UUID tenantId) { return invoices.findByTenantIdOrderByPeriodDesc(tenantId).stream().map(BillingInvoiceResponse::from).toList(); }

    /** Every tenant's invoice for one period, newest-highest-first - who owes what, platform-wide. */
    @Transactional(readOnly = true)
    public List<BillingInvoiceResponse> invoicesForPeriod(String period) {
        List<BillingInvoice> rows = invoices.findByPeriodOrderByTotalAmountDesc(period(period));
        Map<UUID, Tenant> byTenant = tenants.findAllById(rows.stream().map(BillingInvoice::getTenantId).toList())
                .stream().collect(Collectors.toMap(Tenant::getId, t -> t));
        return rows.stream()
                .map(i -> {
                    Tenant t = byTenant.get(i.getTenantId());
                    return BillingInvoiceResponse.from(i, t != null ? t.getName() : "?", t != null ? t.getSubdomain() : null);
                })
                .toList();
    }

    /** Takes a one-time financial snapshot. Existing locked invoices are intentionally untouched. */
    @Transactional
    public BillingInvoiceResponse generate(UUID tenantId, String requestedPeriod) {
        String period = period(requestedPeriod); Tenant tenant = tenant(tenantId);
        BillingInvoice existing = invoices.findByTenantIdAndPeriod(tenantId, period).orElse(null);
        if (existing != null && existing.getLockedAt() != null) return BillingInvoiceResponse.from(existing);
        UsageReport report = usage.reportFor(tenantId, period);
        BillingInvoice i = existing != null ? existing : new BillingInvoice();
        if (existing == null) { i.setId(UUID.randomUUID()); i.setTenantId(tenantId); i.setPeriod(period); }
        i.setBillingPlanId(tenant.getBillingPlanId()); i.setMonthlyFee(report.plan().monthlyFee());
        i.setIncludedMinutes(report.plan().includedMinutes()); i.setOverageMinutes(report.plan().overageMinutes());
        i.setOveragePerMinute(report.plan().overagePerMinute()); i.setUsageMinutes(report.usage().minutes());
        i.setProviderCost(report.cost().total()); i.setTotalAmount(report.invoiceAzn());
        i.setDueDate(YearMonth.parse(period).atEndOfMonth().plusDays(dueDays(tenant)));
        i.setUpdatedAt(Instant.now());
        return BillingInvoiceResponse.from(invoices.save(i));
    }

    @Transactional
    public BillingInvoiceResponse setStatus(UUID id, InvoiceStatus status) {
        BillingInvoice i = invoice(id); if (i.getLockedAt() != null && status == InvoiceStatus.DRAFT) throw new IllegalStateException("Kilidlənmiş dövr qaralama ola bilməz.");
        i.setStatus(status); i.setUpdatedAt(Instant.now()); if (status == InvoiceStatus.SENT && i.getSentAt() == null) i.setSentAt(Instant.now());
        if (status == InvoiceStatus.PAID && i.getPaidAt() == null) i.setPaidAt(Instant.now()); return BillingInvoiceResponse.from(invoices.save(i));
    }

    @Transactional
    public BillingInvoiceResponse lock(UUID id) {
        BillingInvoice i = invoice(id); if (i.getStatus() == InvoiceStatus.DRAFT) i.setStatus(InvoiceStatus.SENT);
        if (i.getLockedAt() == null) i.setLockedAt(Instant.now()); i.setUpdatedAt(Instant.now()); return BillingInvoiceResponse.from(invoices.save(i));
    }

    /** How many trailing months to check on every run - covers a missed run or a backlog from before this job existed. */
    private static final int AUTO_CLOSE_LOOKBACK_MONTHS = 12;

    /**
     * Runs daily and snapshots+locks every elapsed month's invoice for every billing-enabled
     * tenant. This is what makes "past month" mean something: {@link #generate} already refuses
     * to touch a locked invoice, but nothing generated or locked one automatically before this -
     * a month with no invoice row had nothing to protect it, so any view needing a number for it
     * fell back to a live recompute against whatever the tenant's rate happens to be TODAY. A
     * tariff change would then silently rewrite an already-invoiced month.
     *
     * <p>Checks the trailing {@value #AUTO_CLOSE_LOOKBACK_MONTHS} months, not just the one that
     * just elapsed: idempotent (skips whatever is already locked) and self-healing, so a missed
     * run or a backlog of months that predate this job both close out on the next run. Never
     * touches the current (still-open) month.
     */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void closeElapsedMonths() {
        YearMonth currentMonth = YearMonth.now();
        List<Tenant> billable = tenants.findAll().stream().filter(Tenant::isBillingEnabled).toList();
        int closed = 0;
        for (int i = 1; i <= AUTO_CLOSE_LOOKBACK_MONTHS; i++) {
            String period = currentMonth.minusMonths(i).toString();
            for (Tenant t : billable) {
                BillingInvoice existing = invoices.findByTenantIdAndPeriod(t.getId(), period).orElse(null);
                if (existing != null && existing.getLockedAt() != null) continue;
                try {
                    lock(generate(t.getId(), period).id());
                    closed++;
                } catch (Exception e) {
                    log.warn("Could not auto-close {} invoice for tenant {}", period, t.getId(), e);
                }
            }
        }
        if (closed > 0) log.info("Auto-closed {} tenant invoice(s) across the trailing {} months", closed, AUTO_CLOSE_LOOKBACK_MONTHS);
    }

    /**
     * A past month's revenue is whatever was actually invoiced, not what today's rate would
     * produce - so for any month before the current one, a tenant with a locked invoice
     * contributes that frozen amount instead of a fresh {@link UsageService} calculation. Only
     * tenants with no locked invoice yet for that period (e.g. before {@link #closeElapsedMonths}
     * existed) fall back to a live number, and the still-open current month is always live.
     */
    private BigDecimal revenueFor(YearMonth month, YearMonth currentMonth, Set<UUID> onPlanIds) {
        if (!month.isBefore(currentMonth)) {
            return usage.reportForAll(month.toString()).stream()
                    .filter(rep -> onPlanIds.contains(rep.tenantId()))
                    .map(UsageReport::invoiceAzn)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        Map<UUID, BillingInvoice> lockedByTenant = invoices.findByPeriodOrderByTotalAmountDesc(month.toString()).stream()
                .filter(inv -> onPlanIds.contains(inv.getTenantId()) && inv.getLockedAt() != null)
                .collect(Collectors.toMap(BillingInvoice::getTenantId, inv -> inv, (a, b) -> a));
        BigDecimal invoiced = lockedByTenant.values().stream()
                .map(BillingInvoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liveForUninvoiced = usage.reportForAll(month.toString()).stream()
                .filter(rep -> onPlanIds.contains(rep.tenantId()) && !lockedByTenant.containsKey(rep.tenantId()))
                .map(UsageReport::invoiceAzn)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return invoiced.add(liveForUninvoiced);
    }

    private void apply(BillingPlan p, BillingPlanRequest r) { p.setName(r.name().trim()); p.setMonthlyFee(r.monthlyFee()); p.setIncludedMinutes(r.includedMinutes()); p.setOveragePerMinute(r.overagePerMinute()); p.setMonthlyMinuteCap(r.monthlyMinuteCap()); p.setMaxConcurrentCalls(r.maxConcurrentCalls()); p.setDueDays(r.dueDays()); p.setActive(r.active()); p.setUpdatedAt(Instant.now()); }
    private Tenant tenant(UUID id) { return tenants.findById(id).orElseThrow(() -> NotFoundException.of("Tenant", id)); }
    private BillingInvoice invoice(UUID id) { return invoices.findById(id).orElseThrow(() -> NotFoundException.of("Invoice", id)); }
    private int dueDays(Tenant t) { return t.getBillingDueDays() != null ? t.getBillingDueDays() : t.getBillingPlanId() != null ? plans.findById(t.getBillingPlanId()).map(BillingPlan::getDueDays).orElse(15) : 15; }
    private String period(String raw) { try { return (raw == null || raw.isBlank() ? YearMonth.now() : YearMonth.parse(raw.trim())).toString(); } catch (DateTimeException e) { throw new IllegalArgumentException("Dövr YYYY-MM olmalıdır."); } }
    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
