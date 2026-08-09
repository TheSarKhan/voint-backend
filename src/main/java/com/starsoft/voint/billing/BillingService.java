package com.starsoft.voint.billing;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.starsoft.voint.billing.dto.*;
import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.tenant.Tenant;
import com.starsoft.voint.tenant.TenantRepository;
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

    @Transactional(readOnly = true)
    public List<BillingInvoiceResponse> invoicesForPeriod(String period) { return invoices.findByPeriodOrderByTotalAmountDesc(period(period)).stream().map(BillingInvoiceResponse::from).toList(); }

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
