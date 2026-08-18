package com.starsoft.voint.voice;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;
import com.starsoft.voint.settings.PlatformSettingsService;
import com.starsoft.voint.settings.SettingKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridges Vapi's "custom transcriber" WebSocket protocol to the REAL Google Cloud Speech-to-Text
 * (dedicated {@code az-AZ} mode) - not Vapi's own "google" transcriber option, which turned out to
 * be Gemini-based with no dedicated Azerbaijani support. See docs.vapi.ai/customization/custom-transcriber.
 *
 * <p>Protocol: Vapi opens one WebSocket per call and sends a JSON {@code {"type":"start",...}}
 * message describing the audio format, then a continuous stream of binary linear16 PCM frames. We
 * forward that audio into a Google streaming-recognize call and write each result back as
 * {@code {"type":"transcriber-response","transcription":...,"channel":...,"transcriptType":...}}.
 *
 * <p>Trial-stage scope, deliberately not production-hardened yet: one {@link SpeechClient} is built
 * lazily and reused for the process lifetime (a credential rotation needs a restart to take
 * effect), and a Google streaming call is capped at roughly 5 minutes by Google's own API limit -
 * a call running longer than that will lose transcription rather than reconnect. Both are
 * acceptable for comparing transcription quality against Soniox; neither is acceptable to leave
 * unaddressed if this becomes the permanent path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomTranscriberWebSocketHandler extends AbstractWebSocketHandler {

    private static final String LANGUAGE_CODE = "az-AZ";

    private final PlatformSettingsService settings;
    private final ObjectMapper objectMapper;

    private final Map<String, GoogleStream> activeStreams = new ConcurrentHashMap<>();
    private volatile SpeechClient speechClient;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Custom transcriber connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        if (!"start".equals(node.path("type").asText())) {
            return;
        }
        int sampleRate = node.path("sampleRate").asInt(16000);
        int channels = Math.max(1, node.path("channels").asInt(1));
        try {
            GoogleStream stream = new GoogleStream(session, channels);
            stream.open(sampleRate, channels);
            activeStreams.put(session.getId(), stream);
            log.info("Google STT stream opened for {} ({}Hz, {}ch)", session.getId(), sampleRate, channels);
        } catch (Exception e) {
            log.error("Could not open Google STT stream for {}", session.getId(), e);
            session.close(CloseStatus.SERVER_ERROR.withReason("google-stt-unavailable"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        GoogleStream stream = activeStreams.get(session.getId());
        if (stream != null) {
            stream.sendAudio(message.getPayload().array());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        GoogleStream stream = activeStreams.remove(session.getId());
        if (stream != null) {
            stream.close();
        }
        log.info("Custom transcriber disconnected: {} ({})", session.getId(), status);
    }

    /** Built once, reused across every call - see the class javadoc for the credential-rotation caveat. */
    private SpeechClient speechClient() {
        SpeechClient client = speechClient;
        if (client != null) {
            return client;
        }
        synchronized (this) {
            if (speechClient == null) {
                String json = settings.get(SettingKey.GOOGLE_STT_CREDENTIALS_JSON);
                if (!StringUtils.hasText(json)) {
                    throw new IllegalStateException("Google STT kredensialı təyin olunmayıb");
                }
                try {
                    GoogleCredentials credentials = GoogleCredentials
                            .fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
                    SpeechSettings speechSettings = SpeechSettings.newBuilder()
                            .setCredentialsProvider(com.google.api.gax.core.FixedCredentialsProvider.create(credentials))
                            .build();
                    speechClient = SpeechClient.create(speechSettings);
                } catch (Exception e) {
                    throw new IllegalStateException("Google STT client qurula bilmədi: " + e.getMessage(), e);
                }
            }
            return speechClient;
        }
    }

    /** One Google streaming-recognize call, bound to one Vapi WebSocket session. */
    private final class GoogleStream {
        private final WebSocketSession session;
        private final int channels;
        private ClientStream<StreamingRecognizeRequest> clientStream;

        GoogleStream(WebSocketSession session, int channels) {
            this.session = new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024);
            this.channels = channels;
        }

        void open(int sampleRate, int channels) {
            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(sampleRate)
                    .setLanguageCode(LANGUAGE_CODE)
                    .setAudioChannelCount(channels)
                    .setEnableSeparateRecognitionPerChannel(channels > 1)
                    .build();
            StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(recognitionConfig)
                    .setInterimResults(true)
                    .build();

            clientStream = speechClient().streamingRecognizeCallable().splitCall(responseObserver());
            clientStream.send(StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingConfig)
                    .build());
        }

        void sendAudio(byte[] pcm) {
            if (clientStream == null) {
                return;
            }
            try {
                clientStream.send(StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(pcm))
                        .build());
            } catch (Exception e) {
                log.warn("Failed to forward audio to Google STT for {}: {}", session.getId(), e.getMessage());
            }
        }

        void close() {
            if (clientStream != null) {
                try {
                    clientStream.closeSend();
                } catch (Exception e) {
                    log.debug("Google STT stream close for {}: {}", session.getId(), e.getMessage());
                }
            }
        }

        private ResponseObserver<StreamingRecognizeResponse> responseObserver() {
            return new ResponseObserver<>() {
                @Override
                public void onStart(StreamController controller) {
                }

                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    for (StreamingRecognitionResult result : response.getResultsList()) {
                        if (result.getAlternativesCount() == 0) {
                            continue;
                        }
                        String text = result.getAlternatives(0).getTranscript();
                        if (!StringUtils.hasText(text)) {
                            continue;
                        }
                        // Google's channelTag is 1-indexed; channel 1 is assumed to be the caller
                        // (matches how the audio is normally laid out) and channel 2 the agent -
                        // flip this mapping if a live test shows it backwards.
                        String channel = channels > 1 && result.getChannelTag() == 2 ? "assistant" : "customer";
                        sendToVapi(text, channel, result.getIsFinal());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("Google STT stream error for {}", session.getId(), t);
                }

                @Override
                public void onComplete() {
                }
            };
        }

        private void sendToVapi(String text, String channel, boolean isFinal) {
            try {
                Map<String, Object> payload = Map.of(
                        "type", "transcriber-response",
                        "transcription", text,
                        "channel", channel,
                        "transcriptType", isFinal ? "final" : "partial");
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (Exception e) {
                log.warn("Failed to relay transcript to Vapi for {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
