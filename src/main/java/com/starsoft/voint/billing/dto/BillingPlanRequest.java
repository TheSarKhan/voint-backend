package com.starsoft.voint.billing.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record BillingPlanRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("0.0") BigDecimal monthlyFee,
        @Min(0) int includedMinutes,
        @NotNull @DecimalMin("0.0") BigDecimal overagePerMinute,
        @Min(0) int monthlyMinuteCap,
        @Min(1) int maxConcurrentCalls,
        @Min(0) @Max(365) int dueDays,
        boolean active) { }
