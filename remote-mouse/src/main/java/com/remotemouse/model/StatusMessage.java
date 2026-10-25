package com.remotemouse.model;

/**
 * Status message sent to the WebSocket client upon connection
 * or when an error occurs.
 *
 * @param connected    whether the client is successfully connected
 * @param screenWidth  host screen width in pixels
 * @param screenHeight host screen height in pixels
 * @param message      human-readable status or error message
 */
public record StatusMessage(
        boolean connected,
        int screenWidth,
        int screenHeight,
        String message
) {}
