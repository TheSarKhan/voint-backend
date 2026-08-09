package com.starsoft.voint.billing.dto;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record BillingProfileRequest(
        UUID billingPlanId,
        boolean billingEnabled,
        @Size(max = 200) String legalName,
        @Size(max = 80) String taxId,
        @Email @Size(max = 254) String email,
        @Min(0) @Max(365) Integer dueDays) { }
