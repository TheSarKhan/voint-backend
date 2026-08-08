package com.starsoft.voint.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.starsoft.voint.call.CallStatus;

/** Platform-wide overview for the admin dashboard - one screen, everything an operator checks daily. */
public record DashboardResponse(
        int tenantCount,
        long callsToday,
        long callsThisMonth,
        /** Sum across every tenant, current calendar month, in AZN. */
        BigDecimal invoiceAzn,
        BigDecimal costAzn,
        BigDecimal marginAzn,
        /** marginAzn as a percentage of invoiceAzn; null when nothing is billed (invoice is zero). */
        BigDecimal marginPercent,
        /** Held operations waiting on someone's approval, across every tenant. */
        long pendingApprovals,
        /** Knowledge-base gaps not yet closed, across every tenant. */
        long openQuestions,
        /** Pilot requests from the landing page nobody has looked at yet. */
        long newLeads,
        /** Zero-filled, oldest first, last 30 days, UTC day boundaries (matches the per-tenant chart). */
        List<DayCount> callsByDay,
        /** Most recent calls across every tenant, newest first. */
        List<RecentCall> recentCalls,
        /**
         * Top 10 by this month's invoice, biggest first. Not every tenant - the full, sortable list
         * already lives on the Hesablaşma (Usage) page; this is a "what needs a look today" digest,
         * not a replacement for it.
         */
        List<TenantMargin> topTenants
) {
    public record DayCount(LocalDate date, long count) {
    }

    public record RecentCall(
            UUID id,
            UUID tenantId,
            String tenantName,
            String callerNumber,
            CallStatus status,
            Instant startedAt,
            Integer durationSeconds
    ) {
    }

    public record TenantMargin(
            UUID tenantId,
            String tenantName,
            long calls,
            BigDecimal invoiceAzn,
            BigDecimal marginAzn,
            BigDecimal marginPercent
    ) {
    }
}
