package com.starsoft.voint.voice;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.voice.dto.ChatCompletionRequest;
import com.starsoft.voint.voice.dto.ChatCompletionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Vapi Custom LLM webhook. Vapi is configured with this URL as its "custom LLM"
 * provider: it POSTs OpenAI-format chat completion requests here and speaks the
 * returned assistant message via TTS.
 */
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
@Tag(name = "Voice", description = "Vapi custom-LLM webhook (OpenAI-compatible chat completions)")
public class VoiceWebhookController {

    private final VoiceWebhookService voiceWebhookService;

    @PostMapping("/webhook")
    @Operation(summary = "Vapi custom-LLM webhook",
            description = """
                    Accepts an OpenAI-compatible chat completion request (model, messages, stream, call metadata) \
                    and returns an OpenAI-compatible chat.completion response: language detection -> pgvector RAG \
                    search -> prompt build (persona + boundaries + tenant context + RAG context) -> Gemini Flash. \
                    Tenant resolution (BOOTSTRAP STAGE): looks for a top-level "tenant_id" field, then \
                    "call.metadata.tenantId"/"tenant_id"; if neither is present it falls back to the seeded CES \
                    tenant (11111111-1111-1111-1111-111111111111) and logs a warning. Real multi-tenant routing \
                    (e.g. by the caller's dialed phone number) is a TODO for a later stage. \
                    Without GEMINI_API_KEY configured, falls back to MockLlmClient ('mock cavab: {last user message}'). \
                    Streaming is not supported yet - stream:true still gets a non-streaming JSON body.""")
    public ChatCompletionResponse webhook(@RequestBody ChatCompletionRequest request) {
        // TODO: verify VAPI_WEBHOOK_SECRET header once real Vapi integration lands.
        return voiceWebhookService.handle(request);
    }
}
