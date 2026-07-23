package com.starsoft.voint.voice;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
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
    private final VapiEventService vapiEventService;

    @PostMapping({"/webhook", "/chat/completions"})
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
                    Streaming is not supported yet - stream:true still gets a non-streaming JSON body. \
                    Also mounted at /chat/completions since Vapi's custom-LLM integration treats the configured \
                    URL as an OpenAI-client baseURL and appends that path itself.""")
    public ChatCompletionResponse webhook(@RequestBody ChatCompletionRequest request) {
        // Vapi's shared-secret header is verified upstream by VapiWebhookAuthFilter (see SecurityConfig).
        return voiceWebhookService.handle(request);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Vapi server event webhook (call lifecycle)",
            description = """
                    Configured as the assistant's "server" URL (separate from the custom-LLM completions \
                    endpoint above). Vapi POSTs call lifecycle events here, wrapped as {"message": {...}}. \
                    Only "end-of-call-report" is currently handled: it carries the full transcript and AI \
                    summary once a call ends, which get written to the calls/call_transcripts tables so the \
                    CRM panel reflects real calls. Every other event type is acknowledged (200 OK) and ignored.""")
    public void events(@RequestBody JsonNode body) {
        // Same VapiWebhookAuthFilter secret check as /webhook and /chat/completions (see SecurityConfig).
        vapiEventService.handle(body);
    }
}
