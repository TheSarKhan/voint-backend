package com.starsoft.voint.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "billing_plans")
@Getter
@Setter
@NoArgsConstructor
public class BillingPlan {
    @Id private UUID id;
    @Column(nullable = false) private String name;
    @Column(name = "monthly_fee", nullable = false) private BigDecimal monthlyFee;
    @Column(name = "included_minutes", nullable = false) private int includedMinutes;
    @Column(name = "overage_per_minute", nullable = false) private BigDecimal overagePerMinute;
    @Column(name = "monthly_minute_cap", nullable = false) private int monthlyMinuteCap;
    @Column(name = "max_concurrent_calls", nullable = false) private int maxConcurrentCalls = 1;
    @Column(name = "due_days", nullable = false) private int dueDays = 15;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
}
