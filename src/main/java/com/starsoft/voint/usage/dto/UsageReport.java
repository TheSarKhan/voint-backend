package com.starsoft.voint.usage.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One tenant's consumption for one calendar month, what it cost us, and what the tenant owes.
 *
 * <p>Costs are what we pay providers; {@code invoice} is what the tenant pays us. Both are in AZN
 * so they can be compared without converting in the UI.
 */
public record UsageReport(
        UUID tenantId,
        String tenantName,
        /** ISO year-month, e.g. "2026-07". */
        String month,
        UsageTotals usage,
        CostBreakdown cost,
        BillingPlan plan,
        /** What the tenant is billed this month: monthly fee + overage beyond included minutes. */
        BigDecimal invoiceAzn,
        /** invoiceAzn - cost.total. Negative means this tenant is being served at a loss. */
        BigDecimal marginAzn,
        /** Margin as a percentage of the invoice; null when nothing is billed. */
        BigDecimal marginPercent
) {

    /** Raw consumption, before any pricing is applied. */
    public record UsageTotals(
            long calls,
            long durationSeconds,
            BigDecimal minutes,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            /** Characters spoken by the agent - the single largest cost driver. */
            long ttsCharacters
    ) {
    }

    /** Provider costs for the month, in AZN. */
    public record CostBreakdown(
            BigDecimal tts,
            BigDecimal llm,
            BigDecimal vapi,
            BigDecimal stt,
            BigDecimal telephony,
            BigDecimal total
    ) {
    }

    /** The commercial terms this tenant is on. */
    public record BillingPlan(
            BigDecimal monthlyFee,
            int includedMinutes,
            BigDecimal overagePerMinute,
            /** Minutes billed on top of the included allowance. */
            BigDecimal overageMinutes
    ) {
    }
}
