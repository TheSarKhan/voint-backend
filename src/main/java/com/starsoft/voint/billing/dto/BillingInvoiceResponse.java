package com.starsoft.voint.billing.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import com.starsoft.voint.billing.BillingInvoice;
import com.starsoft.voint.billing.InvoiceStatus;

public record BillingInvoiceResponse(UUID id, UUID tenantId, String period, InvoiceStatus status,
        LocalDate dueDate, BigDecimal monthlyFee, int includedMinutes, BigDecimal overageMinutes,
        BigDecimal overagePerMinute, BigDecimal usageMinutes, BigDecimal providerCost,
        BigDecimal totalAmount, Instant lockedAt, Instant sentAt, Instant paidAt, Instant createdAt) {
    public static BillingInvoiceResponse from(BillingInvoice i) {
        return new BillingInvoiceResponse(i.getId(), i.getTenantId(), i.getPeriod(), i.getStatus(), i.getDueDate(),
                i.getMonthlyFee(), i.getIncludedMinutes(), i.getOverageMinutes(), i.getOveragePerMinute(),
                i.getUsageMinutes(), i.getProviderCost(), i.getTotalAmount(), i.getLockedAt(), i.getSentAt(),
                i.getPaidAt(), i.getCreatedAt());
    }
}
