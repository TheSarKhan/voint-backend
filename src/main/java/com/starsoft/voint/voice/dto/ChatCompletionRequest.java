package com.starsoft.voint.voice.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Vapi custom-LLM webhook request (OpenAI-compatible chat completions format).
 * Vapi sends additional fields (call metadata, tools, etc.) - unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        Boolean stream,
        /** Vapi call metadata (call id, customer number, assistant info...). Kept loose at this stage. */
        Map<String, Object> call,
        /**
         * Tenant identifier (UUID). BOOTSTRAP STAGE: optional - if absent (and not found under
         * {@code call.metadata.tenantId} / {@code call.metadata.tenant_id} either), the webhook
         * falls back to the seeded CES tenant. Real routing (e.g. by dialed phone number) is a
         * later-stage TODO. Vapi custom-LLM configs can be set up to pass this via
         * {@code assistant.metadata} -> forwarded here as top-level {@code tenant_id}.
         */
        @JsonProperty("tenant_id") String tenantId
) {
}
