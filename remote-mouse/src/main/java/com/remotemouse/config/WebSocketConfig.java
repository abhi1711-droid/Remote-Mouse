package com.remotemouse.config;

import com.remotemouse.handler.InputWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the raw (non-STOMP) WebSocket endpoint at {@code /ws}.
 * <p>
 * STOMP is intentionally avoided to minimise protocol overhead and latency
 * for real-time mouse/keyboard event streaming.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final InputWebSocketHandler inputWebSocketHandler;

    public WebSocketConfig(InputWebSocketHandler inputWebSocketHandler) {
        this.inputWebSocketHandler = inputWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(inputWebSocketHandler, "/ws")
                .setAllowedOrigins("*"); // allow any LAN client
    }
}
