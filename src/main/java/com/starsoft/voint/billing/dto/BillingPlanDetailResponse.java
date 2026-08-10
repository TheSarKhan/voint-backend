package com.starsoft.voint.billing.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The catalogue plan itself, plus what it actually costs the business and who is on it. */
public record BillingPlanDetailResponse(
        BillingPlanResponse plan,
        int tenantCount,
        BigDecimal currentMonthRevenueAzn,
        long currentMonthCalls,
        BigDecimal currentMonthMinutes,
        QuotaBreakdown quota,
        /** Last 12 calendar months, oldest first. */
        List<MonthRevenue> revenueTrend,
        List<PlanTenant> tenants
) {
    /** How many tenants on this plan are, this month, under / near / at their monthly-minute cap -
     *  see {@link com.starsoft.voint.usage.TenantQuotaService.Status}. Tells whether this plan's
     *  cap is sized right for the tenants actually on it. */
    public record QuotaBreakdown(int ok, int warning, int blocked) {
    }

    public record MonthRevenue(String month, BigDecimal revenueAzn) {
    }

    public record PlanTenant(
            UUID id,
            String name,
            String subdomain,
            /** OK / WARNING / BLOCKED. */
            String quotaStatus,
            BigDecimal currentMonthInvoiceAzn,
            long currentMonthCalls
    ) {
    }
}
