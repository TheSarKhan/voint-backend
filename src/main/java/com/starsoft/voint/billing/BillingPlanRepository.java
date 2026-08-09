package com.starsoft.voint.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, UUID> {
    List<BillingPlan> findAllByOrderByActiveDescNameAsc();
}
