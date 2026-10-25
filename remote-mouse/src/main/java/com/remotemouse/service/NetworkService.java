package com.remotemouse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Discovers the host machine's LAN IP address so clients on the same
 * network can connect to the Remote Mouse server.
 */
@Service
public class NetworkService {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    /**
     * Returns the most likely LAN IP address of this machine.
     * <p>
     * Strategy:
     * <ol>
     *   <li>Open a UDP socket aimed at a public DNS server – the OS selects
     *       the correct outbound interface without sending any traffic.</li>
     *   <li>Fall back to iterating {@link NetworkInterface}s, skipping
     *       loopback, virtual, and down interfaces.</li>
     *   <li>Final fallback: {@code 127.0.0.1}.</li>
     * </ol>
     *
     * @return LAN IPv4 address string
     */
    public String getLocalIpAddress() {
        List<String> privateCandidates = new ArrayList<>();
        List<String> otherCandidates = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isPointToPoint() || ni.isVirtual()) {
                    continue; // skip loopback and VPN tunnels (utun*, tun*, tailscale*)
                }
                String name = ni.getName().toLowerCase();
                if (name.startsWith("utun") || name.startsWith("tun") || name.startsWith("tap")
                        || name.contains("tailscale") || name.contains("zt")
                        || name.startsWith("awdl") || name.startsWith("llw")
                        || name.startsWith("bridge")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!(addr instanceof java.net.Inet4Address) || addr.isLoopbackAddress()
                            || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    if (isCarrierGradeNat(ip)) {
                        continue; // 100.64.0.0/10 — Tailscale / CGNAT, not reachable from phones
                    }
                    if (addr.isSiteLocalAddress()) {
                        log.debug("Found private LAN candidate on {}: {}", ni.getDisplayName(), ip);
                        privateCandidates.add(ip);
                    } else {
                        otherCandidates.add(ip);
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("NetworkInterface enumeration failed", e);
        }

        if (!privateCandidates.isEmpty()) {
            String ip = privateCandidates.get(0);
            log.info("Selected LAN IP: {}", ip);
            return ip;
        }
        if (!otherCandidates.isEmpty()) {
            String ip = otherCandidates.get(0);
            log.warn("No RFC1918 address found; using {}", ip);
            return ip;
        }

        log.warn("Could not determine LAN IP address – falling back to 127.0.0.1");
        return "127.0.0.1";
    }

    /** RFC 6598 shared address space (100.64.0.0/10) — used by CGNAT and Tailscale. */
    private boolean isCarrierGradeNat(String ip) {
        if (!ip.startsWith("100.")) return false;
        int secondOctet = Integer.parseInt(ip.split("\\.")[1]);
        return secondOctet >= 64 && secondOctet <= 127;
    }

    /**
     * Builds the full HTTP connection URL for the given port.
     *
     * @param port server port
     * @return URL in the form {@code http://<ip>:<port>}
     */
    public String getConnectionUrl(int port) {
        return "http://" + getLocalIpAddress() + ":" + port;
    }
}
