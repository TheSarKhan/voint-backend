package com.starsoft.voint.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.common.dto.PageRequests;
import com.starsoft.voint.common.dto.PageResponse;
import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.tenant.Tenant;
import com.starsoft.voint.tenant.TenantRepository;
import com.starsoft.voint.usage.dto.BillingPlanUpdateRequest;
import com.starsoft.voint.usage.dto.UsageReport;

import lombok.RequiredArgsConstructor;

/**
 * Turns recorded usage into money: what a tenant consumed in a month, what that cost us at
 * provider rates, and what the tenant owes under its plan.
 *
 * <p>Nothing here is estimated from averages. Minutes come from {@code calls.duration_seconds},
 * tokens and spoken characters from {@code usage_events} - all measured at the time they happened.
 */
@Service
@RequiredArgsConstructor
public class UsageService {

    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TenantRepository tenantRepository;
    private final CallRepository callRepository;
    private final UsageEventRepository usageEventRepository;
    private final BillingRates rates;

    /** Report for a single tenant. */
    @Transactional(readOnly = true)
    public UsageReport reportFor(UUID tenantId, String month) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> NotFoundException.of("Tenant", tenantId));

        YearMonth ym = parseMonth(month);
        Instant from = startOf(ym);
        Instant to = startOf(ym.plusMonths(1));

        CallRepository.CallTotals calls = callRepository.sumForTenant(tenantId, from, to);
        UsageEventRepository.AiTotals ai = usageEventRepository.sumForTenant(tenantId, from, to);

        return build(tenant, ym, value(calls.getCallCount()), value(calls.getTotalSeconds()),
                value(ai.getPromptTokens()), value(ai.getCompletionTokens()), value(ai.getTtsCharacters()));
    }

    /**
     * Report for every tenant, including ones with no activity - a tenant that made no calls but
     * still owes a monthly fee must not silently disappear from the billing list.
     */
    @Transactional(readOnly = true)
    public List<UsageReport> reportForAll(String month) {
        YearMonth ym = parseMonth(month);
        Instant from = startOf(ym);
        Instant to = startOf(ym.plusMonths(1));

        Map<UUID, CallRepository.TenantCallTotals> callsByTenant =
                callRepository.sumGroupedByTenant(from, to).stream()
                        .collect(Collectors.toMap(CallRepository.TenantCallTotals::getTenantId,
                                Function.identity()));
        Map<UUID, UsageEventRepository.TenantAiTotals> aiByTenant =
                usageEventRepository.sumGroupedByTenant(from, to).stream()
                        .collect(Collectors.toMap(UsageEventRepository.TenantAiTotals::getTenantId,
                                Function.identity()));

        return tenantRepository.findAll().stream()
                .map(tenant -> {
                    CallRepository.TenantCallTotals calls = callsByTenant.get(tenant.getId());
                    UsageEventRepository.TenantAiTotals ai = aiByTenant.get(tenant.getId());
                    return build(tenant, ym,
                            calls != null ? value(calls.getCallCount()) : 0L,
                            calls != null ? value(calls.getTotalSeconds()) : 0L,
                            ai != null ? value(ai.getPromptTokens()) : 0L,
                            ai != null ? value(ai.getCompletionTokens()) : 0L,
                            ai != null ? value(ai.getTtsCharacters()) : 0L);
                })
                // Biggest bills first - that is the order the operator wants to review them in.
                .sorted(Comparator.comparing(UsageReport::invoiceAzn).reversed())
                .toList();
    }

    /** Columns the billing table may sort by; all of them are computed, none exist in SQL. */
    public static final Set<String> SORTABLE = Set.of(
            "tenantName", "calls", "minutes", "ttsCharacters", "totalTokens",
            "cost", "invoice", "margin");

    public static final String DEFAULT_SORT = "invoice";

    /**
     * Sorted and paginated in memory, deliberately.
     *
     * <p>Every sortable column here - cost, invoice, margin - is derived from provider rates and a
     * billing plan, not stored anywhere, so none of it can be ordered by SQL without duplicating
     * the pricing rules into the query and letting the two drift apart. The set being ordered is
     * the tenant list, which is tens of rows; when that stops being true the fix is a materialised
     * monthly summary table, not a cleverer query.
     */
    @Transactional(readOnly = true)
    public PageResponse<UsageReport> pagedReport(String month, String sort, String direction,
                                                 Integer page, Integer size) {
        List<UsageReport> all = reportForAll(month);

        String sortKey = PageRequests.resolveSort(sort, SORTABLE, DEFAULT_SORT);
        String dir = PageRequests.resolveDirection(direction);
        Comparator<UsageReport> comparator = comparatorFor(sortKey);
        if ("desc".equals(dir)) {
            comparator = comparator.reversed();
        }

        int pageNumber = PageRequests.resolvePage(page);
        int pageSize = PageRequests.resolveSize(size);
        List<UsageReport> sorted = all.stream().sorted(comparator).toList();

        int from = Math.min(pageNumber * pageSize, sorted.size());
        int to = Math.min(from + pageSize, sorted.size());

        return PageResponse.of(sorted.subList(from, to), pageNumber, pageSize, sorted.size(), sortKey, dir);
    }

    private Comparator<UsageReport> comparatorFor(String key) {
        return switch (key) {
            case "tenantName" -> Comparator.comparing(UsageReport::tenantName, String.CASE_INSENSITIVE_ORDER);
            case "calls" -> Comparator.comparingLong(r -> r.usage().calls());
            case "minutes" -> Comparator.comparing(r -> r.usage().minutes());
            case "ttsCharacters" -> Comparator.comparingLong(r -> r.usage().ttsCharacters());
            case "totalTokens" -> Comparator.comparingLong(r -> r.usage().totalTokens());
            case "cost" -> Comparator.comparing(r -> r.cost().total());
            case "margin" -> Comparator.comparing(UsageReport::marginAzn);
            default -> Comparator.comparing(UsageReport::invoiceAzn);
        };
    }

    @Transactional
    public Tenant updateBillingPlan(UUID tenantId, BillingPlanUpdateRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> NotFoundException.of("Tenant", tenantId));
        tenant.setMonthlyFee(request.monthlyFee());
        tenant.setIncludedMinutes(request.includedMinutes());
        tenant.setOveragePerMinute(request.overagePerMinute());
        if (request.monthlyMinuteCap() != null) {
            tenant.setMonthlyMinuteCap(request.monthlyMinuteCap());
        }
        return tenantRepository.save(tenant);
    }

    // ---------------------------------------------------------------- calculation

    private UsageReport build(Tenant tenant, YearMonth ym, long callCount, long durationSeconds,
                              long promptTokens, long completionTokens, long ttsCharacters) {
        BigDecimal minutes = BigDecimal.valueOf(durationSeconds)
                .divide(SECONDS_PER_MINUTE, 2, RoundingMode.HALF_UP);

        // --- provider costs, computed in USD then converted once ---
        BigDecimal ttsUsd = BigDecimal.valueOf(ttsCharacters)
                .divide(THOUSAND, 6, RoundingMode.HALF_UP)
                .multiply(rates.getTtsPer1kChars());
        BigDecimal llmUsd = BigDecimal.valueOf(promptTokens)
                .divide(MILLION, 9, RoundingMode.HALF_UP)
                .multiply(rates.getLlmInputPer1mTokens())
                .add(BigDecimal.valueOf(completionTokens)
                        .divide(MILLION, 9, RoundingMode.HALF_UP)
                        .multiply(rates.getLlmOutputPer1mTokens()));
        BigDecimal vapiUsd = minutes.multiply(rates.getVapiPerMinute());
        BigDecimal sttUsd = minutes.multiply(rates.getSttPerMinute());
        BigDecimal telephonyUsd = minutes.multiply(rates.getTelephonyPerMinute());

        BigDecimal tts = azn(ttsUsd);
        BigDecimal llm = azn(llmUsd);
        BigDecimal vapi = azn(vapiUsd);
        BigDecimal stt = azn(sttUsd);
        BigDecimal telephony = azn(telephonyUsd);
        BigDecimal totalCost = tts.add(llm).add(vapi).add(stt).add(telephony);

        // --- what the tenant owes ---
        BigDecimal overageMinutes = minutes
                .subtract(BigDecimal.valueOf(tenant.getIncludedMinutes()))
                .max(BigDecimal.ZERO);
        BigDecimal invoice = tenant.getMonthlyFee()
                .add(overageMinutes.multiply(tenant.getOveragePerMinute()))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal margin = invoice.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal marginPercent = invoice.signum() == 0
                ? null
                : margin.multiply(HUNDRED).divide(invoice, 1, RoundingMode.HALF_UP);

        return new UsageReport(
                tenant.getId(),
                tenant.getName(),
                ym.toString(),
                new UsageReport.UsageTotals(callCount, durationSeconds, minutes, promptTokens,
                        completionTokens, promptTokens + completionTokens, ttsCharacters),
                new UsageReport.CostBreakdown(tts, llm, vapi, stt, telephony, totalCost),
                new UsageReport.BillingPlan(tenant.getMonthlyFee(), tenant.getIncludedMinutes(),
                        tenant.getOveragePerMinute(), overageMinutes,
                        tenant.getMonthlyMinuteCap(), capPercent(minutes, tenant.getMonthlyMinuteCap())),
                invoice,
                margin,
                marginPercent);
    }

    /** Null when no ceiling is set - there is nothing to be a percentage of. */
    private BigDecimal capPercent(BigDecimal minutes, int cap) {
        if (cap <= 0) {
            return null;
        }
        return minutes.multiply(HUNDRED).divide(BigDecimal.valueOf(cap), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal azn(BigDecimal usd) {
        return usd.multiply(rates.getUsdToAzn()).setScale(2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- helpers

    /** Blank month means "this month" in the business's own timezone, not UTC. */
    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(rates.getZone());
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("month must be in YYYY-MM format, got: " + month);
        }
    }

    private Instant startOf(YearMonth ym) {
        return ym.atDay(1).atStartOfDay(rates.getZone()).toInstant();
    }

    private long value(Long raw) {
        return raw != null ? raw : 0L;
    }
}
