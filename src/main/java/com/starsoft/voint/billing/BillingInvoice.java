package com.starsoft.voint.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "billing_invoices")
@Getter @Setter @NoArgsConstructor
public class BillingInvoice {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "billing_plan_id") private UUID billingPlanId;
    @Column(nullable = false) private String period;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private InvoiceStatus status = InvoiceStatus.DRAFT;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "monthly_fee", nullable = false) private BigDecimal monthlyFee;
    @Column(name = "included_minutes", nullable = false) private int includedMinutes;
    @Column(name = "overage_minutes", nullable = false) private BigDecimal overageMinutes;
    @Column(name = "overage_per_minute", nullable = false) private BigDecimal overagePerMinute;
    @Column(name = "usage_minutes", nullable = false) private BigDecimal usageMinutes;
    @Column(name = "provider_cost", nullable = false) private BigDecimal providerCost;
    @Column(name = "total_amount", nullable = false) private BigDecimal totalAmount;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
}
