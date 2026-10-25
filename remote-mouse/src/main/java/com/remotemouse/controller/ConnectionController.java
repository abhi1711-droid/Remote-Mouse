package com.remotemouse.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.remotemouse.model.ConnectionInfo;
import com.remotemouse.service.NetworkService;
import com.remotemouse.service.RobotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * REST endpoints for client discovery and QR-code-based pairing.
 */
@RestController
@RequestMapping("/api")
public class ConnectionController {

    private static final Logger log = LoggerFactory.getLogger(ConnectionController.class);
    private static final int QR_SIZE = 300;

    private final NetworkService networkService;
    private final RobotService robotService;

    @Value("${server.port:8080}")
    private int serverPort;

    public ConnectionController(NetworkService networkService, RobotService robotService) {
        this.networkService = networkService;
        this.robotService = robotService;
    }

    /**
     * Returns connection metadata so the client knows how to reach this server.
     *
     * @return JSON with ip, port, url, screenWidth, screenHeight
     */
    @GetMapping("/connection-info")
    public ConnectionInfo getConnectionInfo() {
        String ip = networkService.getLocalIpAddress();
        String url = networkService.getConnectionUrl(serverPort);
        Dimension screen = robotService.getScreenSize();
        return new ConnectionInfo(ip, serverPort, url, screen.width, screen.height);
    }

    /**
     * Generates a QR code PNG that encodes the connection URL.
     * Clients can scan this to connect without manually entering an IP.
     *
     * @return 300×300 PNG image
     */
    @GetMapping("/qr-code")
    public ResponseEntity<byte[]> getQrCode() {
        String url = networkService.getConnectionUrl(serverPort);
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);

            log.debug("Generated QR code for URL: {}", url);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(out.toByteArray());
        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
