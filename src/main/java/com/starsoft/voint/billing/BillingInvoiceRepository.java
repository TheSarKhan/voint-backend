package com.starsoft.voint.billing;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {
    Optional<BillingInvoice> findByTenantIdAndPeriod(UUID tenantId, String period);
    List<BillingInvoice> findByPeriodOrderByTotalAmountDesc(String period);
    List<BillingInvoice> findByTenantIdOrderByPeriodDesc(UUID tenantId);
}
