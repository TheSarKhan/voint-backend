package com.starsoft.voint.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.tenant.Tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Decides whether a tenant may still be answered this month.
 *
 * <p>This is a cost guard, not a billing rule. A customer who exceeds its included minutes simply
 * pays overage; a customer that has blown through its hard ceiling is assumed to have something
 * wrong on its side, and answering more calls would burn credits nobody asked for.
 *
 * <p>Minutes are read from the {@code calls} table, which is written at end-of-call. A call in
 * progress therefore does not count yet - deliberately: the alternative is metering mid-call,
 * which buys precision we do not need for a monthly ceiling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantQuotaService {

    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Above this share of the cap, the admin panel warns before the ceiling is actually hit. */
    private static final BigDecimal WARN_AT_PERCENT = BigDecimal.valueOf(80);

    private final CallRepository callRepository;
    private final BillingRates rates;

    public enum Status {
        /** Under the ceiling, or no ceiling set. */
        OK,
        /** Past the warning threshold but still answering. */
        WARNING,
        /** Ceiling reached - new calls are declined. */
        BLOCKED
    }

    /**
     * @param minutesUsed month-to-date, from completed calls
     * @param cap         0 when the tenant has no ceiling
     * @param percentUsed null when there is no ceiling to be a percentage of
     */
    public record Quota(Status status, BigDecimal minutesUsed, int cap, BigDecimal percentUsed) {

        public boolean blocked() {
            return status == Status.BLOCKED;
        }
    }

    @Transactional(readOnly = true)
    public Quota check(Tenant tenant) {
        BigDecimal used = minutesThisMonth(tenant.getId());
        int cap = tenant.getMonthlyMinuteCap();

        if (cap <= 0) {
            return new Quota(Status.OK, used, 0, null);
        }

        BigDecimal percent = used.multiply(HUNDRED)
                .divide(BigDecimal.valueOf(cap), 1, RoundingMode.HALF_UP);

        Status status;
        if (used.compareTo(BigDecimal.valueOf(cap)) >= 0) {
            status = Status.BLOCKED;
        } else if (percent.compareTo(WARN_AT_PERCENT) >= 0) {
            status = Status.WARNING;
        } else {
            status = Status.OK;
        }
        return new Quota(status, used, cap, percent);
    }

    @Transactional(readOnly = true)
    public BigDecimal minutesThisMonth(UUID tenantId) {
        YearMonth month = YearMonth.now(rates.getZone());
        Instant from = month.atDay(1).atStartOfDay(rates.getZone()).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(rates.getZone()).toInstant();

        CallRepository.CallTotals totals = callRepository.sumForTenant(tenantId, from, to);
        Long seconds = totals.getTotalSeconds();
        return BigDecimal.valueOf(seconds != null ? seconds : 0L)
                .divide(SECONDS_PER_MINUTE, 2, RoundingMode.HALF_UP);
    }
}
