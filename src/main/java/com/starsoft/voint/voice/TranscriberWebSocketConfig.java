package com.starsoft.voint.voice;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;

/** Registers the custom-transcriber bridge at the path Vapi's assistant config points to. */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class TranscriberWebSocketConfig implements WebSocketConfigurer {

    private final CustomTranscriberWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/v1/voice/transcriber");
    }
}
