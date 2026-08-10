package com.starsoft.voint.billing;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
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

/** Commercial catalogue and immutable monthly invoice snapshots. UsageService remains the pricing source. */
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
            BigDecimal revenue = usage.reportForAll(ym.toString()).stream()
                    .filter(rep -> onPlanIds.contains(rep.tenantId()))
                    .map(UsageReport::invoiceAzn)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trend.add(new BillingPlanDetailResponse.MonthRevenue(ym.toString(), revenue));
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

    private void apply(BillingPlan p, BillingPlanRequest r) { p.setName(r.name().trim()); p.setMonthlyFee(r.monthlyFee()); p.setIncludedMinutes(r.includedMinutes()); p.setOveragePerMinute(r.overagePerMinute()); p.setMonthlyMinuteCap(r.monthlyMinuteCap()); p.setMaxConcurrentCalls(r.maxConcurrentCalls()); p.setDueDays(r.dueDays()); p.setActive(r.active()); p.setUpdatedAt(Instant.now()); }
    private Tenant tenant(UUID id) { return tenants.findById(id).orElseThrow(() -> NotFoundException.of("Tenant", id)); }
    private BillingInvoice invoice(UUID id) { return invoices.findById(id).orElseThrow(() -> NotFoundException.of("Invoice", id)); }
    private int dueDays(Tenant t) { return t.getBillingDueDays() != null ? t.getBillingDueDays() : t.getBillingPlanId() != null ? plans.findById(t.getBillingPlanId()).map(BillingPlan::getDueDays).orElse(15) : 15; }
    private String period(String raw) { try { return (raw == null || raw.isBlank() ? YearMonth.now() : YearMonth.parse(raw.trim())).toString(); } catch (DateTimeException e) { throw new IllegalArgumentException("Dövr YYYY-MM olmalıdır."); } }
    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
