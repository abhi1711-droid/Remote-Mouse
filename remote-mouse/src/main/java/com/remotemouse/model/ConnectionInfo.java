package com.remotemouse.model;

/**
 * Connection information returned to clients via REST.
 *
 * @param ip           server LAN IP address
 * @param port         server port
 * @param url          full connection URL (http://ip:port)
 * @param screenWidth  host screen width in pixels
 * @param screenHeight host screen height in pixels
 */
public record ConnectionInfo(
        String ip,
        int port,
        String url,
        int screenWidth,
        int screenHeight
) {}
