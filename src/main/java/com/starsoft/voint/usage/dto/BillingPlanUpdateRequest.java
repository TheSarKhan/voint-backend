package com.starsoft.voint.usage.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Commercial terms for a tenant, set by the platform operator. Amounts in AZN. */
public record BillingPlanUpdateRequest(
        @NotNull @DecimalMin("0.0") BigDecimal monthlyFee,
        @NotNull @Min(0) Integer includedMinutes,
        @NotNull @DecimalMin("0.0") BigDecimal overagePerMinute,
        /** Hard monthly ceiling in minutes; 0 disables it. Optional - omitted leaves it unchanged. */
        @Min(0) Integer monthlyMinuteCap
) {
}
