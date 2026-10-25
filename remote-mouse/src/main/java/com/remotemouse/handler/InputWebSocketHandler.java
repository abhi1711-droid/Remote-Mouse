package com.remotemouse.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remotemouse.model.InputMessage;
import com.remotemouse.model.StatusMessage;
import com.remotemouse.service.RobotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.awt.*;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Handles raw WebSocket text frames for the Remote Mouse protocol.
 * <p>
 * Incoming JSON messages are deserialized into {@link InputMessage} and
 * routed to the appropriate {@link RobotService} method based on the
 * {@code type} field. Only one client may be connected at a time; subsequent
 * connections are rejected with an explanatory status message.
 */
@Component
public class InputWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(InputWebSocketHandler.class);

    private final RobotService robotService;
    private final ObjectMapper objectMapper;

    /** The single active session (null when nobody is connected). */
    private volatile WebSocketSession activeSession;

    public InputWebSocketHandler(RobotService robotService) {
        this.robotService = robotService;
        this.objectMapper = new ObjectMapper();
    }

    // ────────────────────────── Lifecycle ──────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Single-client guard
        if (activeSession != null && activeSession.isOpen()) {
            log.warn("Rejecting connection from {} – another client is already connected ({})",
                    session.getRemoteAddress(), activeSession.getRemoteAddress());
            sendJson(session, new StatusMessage(false, 0, 0,
                    "Another client is already connected. Disconnect it first."));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        activeSession = session;
        Dimension screen = robotService.getScreenSize();
        log.info("Client connected: {} (session {})", session.getRemoteAddress(), session.getId());

        sendJson(session, new StatusMessage(true, screen.width, screen.height, "Connected successfully"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (activeSession != null && activeSession.getId().equals(session.getId())) {
            activeSession = null;
        }
        log.info("Client disconnected: {} (status {})", session.getRemoteAddress(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error on session {}: {}", session.getId(), exception.getMessage());
        if (activeSession != null && activeSession.getId().equals(session.getId())) {
            activeSession = null;
        }
    }

    // ────────────────────────── Message routing ──────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            InputMessage input = objectMapper.readValue(message.getPayload(), InputMessage.class);
            if (input.type() == null) {
                sendError(session, "Missing 'type' field");
                return;
            }

            switch (input.type().toLowerCase()) {
                case "move" -> handleMove(input);
                case "click" -> handleClick(input);
                case "doubleclick" -> robotService.doubleClick();
                case "scroll" -> handleScroll(input);
                case "drag" -> handleDrag(input);
                case "type" -> handleType(input);
                case "key" -> handleKey(input);
                case "media" -> handleMedia(input);
                case "sensitivity" -> handleSensitivity(input);
                default -> sendError(session, "Unknown message type: " + input.type());
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            sendError(session, "Error: " + e.getMessage());
        }
    }

    // ────────────────────────── Type-specific handlers ──────────────────────────

    private void handleMove(InputMessage msg) {
        int dx = msg.dx() != null ? msg.dx() : 0;
        int dy = msg.dy() != null ? msg.dy() : 0;
        robotService.moveMouse(dx, dy);
    }

    private void handleClick(InputMessage msg) {
        robotService.click(msg.button());
    }

    private void handleScroll(InputMessage msg) {
        int delta = msg.delta() != null ? msg.delta() : 0;
        robotService.scroll(delta);
    }

    private void handleDrag(InputMessage msg) {
        String action = msg.action() != null ? msg.action().toLowerCase() : "";
        switch (action) {
            case "start" -> robotService.dragStart();
            case "move" -> {
                int dx = msg.dx() != null ? msg.dx() : 0;
                int dy = msg.dy() != null ? msg.dy() : 0;
                robotService.dragMove(dx, dy);
            }
            case "end" -> robotService.dragEnd();
            default -> log.warn("Unknown drag action: '{}'", action);
        }
    }

    private void handleType(InputMessage msg) {
        if (msg.text() != null) {
            robotService.typeText(msg.text());
        }
    }

    private void handleKey(InputMessage msg) {
        if (msg.key() != null) {
            robotService.pressKey(msg.key(), msg.modifiers());
        }
    }

    private void handleMedia(InputMessage msg) {
        if (msg.action() != null) {
            robotService.mediaKey(msg.action());
        }
    }

    private void handleSensitivity(InputMessage msg) {
        if (msg.value() != null) {
            robotService.setSensitivity(msg.value());
        }
    }

    // ────────────────────────── Helpers ──────────────────────────

    private void sendJson(WebSocketSession session, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send JSON to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        log.warn("Sending error to client: {}", errorMessage);
        try {
            Map<String, String> error = Collections.singletonMap("error", errorMessage);
            String json = objectMapper.writeValueAsString(error);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send error to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
