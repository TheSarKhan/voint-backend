package com.starsoft.voint.usage;

import java.math.BigDecimal;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * What each provider charges us. Kept in configuration rather than the database because these are
 * platform-wide and change when a provider changes its price list, not per tenant.
 *
 * <p>All rates are USD, matching how the providers publish them; {@link #usdToAzn} converts once
 * at the end so there is a single place to fix when the exchange rate moves.
 */
@Component
@Getter
public class BillingRates {

    private final BigDecimal usdToAzn;
    private final BigDecimal ttsPer1kChars;
    private final BigDecimal llmInputPer1mTokens;
    private final BigDecimal llmOutputPer1mTokens;
    private final BigDecimal vapiPerMinute;
    private final BigDecimal sttPerMinute;
    private final BigDecimal telephonyPerMinute;

    /** Month boundaries are local to the business, not UTC - "July" must mean July in Baku. */
    private final ZoneId zone;

    public BillingRates(
            @Value("${voint.billing.usd-to-azn:1.70}") BigDecimal usdToAzn,
            // ElevenLabs pay-as-you-go, multilingual v2 / v3.
            @Value("${voint.billing.tts-per-1k-chars:0.10}") BigDecimal ttsPer1kChars,
            // Gemini 2.5 Flash.
            @Value("${voint.billing.llm-input-per-1m-tokens:0.30}") BigDecimal llmInputPer1mTokens,
            @Value("${voint.billing.llm-output-per-1m-tokens:2.50}") BigDecimal llmOutputPer1mTokens,
            // Vapi platform fee.
            @Value("${voint.billing.vapi-per-minute:0.05}") BigDecimal vapiPerMinute,
            // Soniox real-time STT ($0.12/hour).
            @Value("${voint.billing.stt-per-minute:0.002}") BigDecimal sttPerMinute,
            // Inbound telephony.
            @Value("${voint.billing.telephony-per-minute:0.01}") BigDecimal telephonyPerMinute,
            @Value("${voint.billing.zone:Asia/Baku}") String zone) {
        this.usdToAzn = usdToAzn;
        this.ttsPer1kChars = ttsPer1kChars;
        this.llmInputPer1mTokens = llmInputPer1mTokens;
        this.llmOutputPer1mTokens = llmOutputPer1mTokens;
        this.vapiPerMinute = vapiPerMinute;
        this.sttPerMinute = sttPerMinute;
        this.telephonyPerMinute = telephonyPerMinute;
        this.zone = ZoneId.of(zone);
    }
}
