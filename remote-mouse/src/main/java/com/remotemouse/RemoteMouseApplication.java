package com.remotemouse;

import com.remotemouse.service.NetworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Remote Mouse server.
 * <p>
 * On startup, logs the LAN connection URL and a reminder to grant
 * macOS Accessibility permissions so that {@link java.awt.Robot} can
 * control the mouse and keyboard.
 */
@SpringBootApplication
public class RemoteMouseApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RemoteMouseApplication.class);

    private final NetworkService networkService;

    @Value("${server.port:8080}")
    private int serverPort;

    public RemoteMouseApplication(NetworkService networkService) {
        this.networkService = networkService;
    }

    public static void main(String[] args) {
        SpringApplication.run(RemoteMouseApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String ip = networkService.getLocalIpAddress();
        String url = networkService.getConnectionUrl(serverPort);

        log.info("""
                
                ╔══════════════════════════════════════════════════════╗
                ║              🖱  Remote Mouse Server                ║
                ╠══════════════════════════════════════════════════════╣
                ║                                                      ║
                ║  URL:  {}
                ║  IP:   {}
                ║  Port: {}
                ║                                                      ║
                ║  Open the URL above on your phone/tablet browser     ║
                ║  (must be on the same Wi-Fi network)                 ║
                ║                                                      ║
                ╚══════════════════════════════════════════════════════╝
                """, url, ip, serverPort);

        log.warn("""
                ⚠  macOS Accessibility Permission Required!
                   Go to: System Settings → Privacy & Security → Accessibility
                   and enable access for your terminal / IDE.
                   Without this, mouse and keyboard control will not work.
                """);
    }
}
