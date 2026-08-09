package com.starsoft.voint.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.starsoft.voint.billing.BillingPlan;

public record BillingPlanResponse(UUID id, String name, BigDecimal monthlyFee, int includedMinutes,
        BigDecimal overagePerMinute, int monthlyMinuteCap, int maxConcurrentCalls, int dueDays, boolean active, Instant createdAt) {
    public static BillingPlanResponse from(BillingPlan p) {
        return new BillingPlanResponse(p.getId(), p.getName(), p.getMonthlyFee(), p.getIncludedMinutes(),
                p.getOveragePerMinute(), p.getMonthlyMinuteCap(), p.getMaxConcurrentCalls(), p.getDueDays(), p.isActive(), p.getCreatedAt());
    }
}
