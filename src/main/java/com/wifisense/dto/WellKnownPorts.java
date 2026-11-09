package com.wifisense.dto;

import java.util.Map;

/** The name of the common services, so the page says "HTTPS" next to TCP 443. Unknown ports have no name. */
public final class WellKnownPorts {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("TCP/20", "FTP (datos)"), Map.entry("TCP/21", "FTP"), Map.entry("TCP/22", "SSH"),
            Map.entry("TCP/23", "Telnet"), Map.entry("TCP/25", "SMTP"), Map.entry("UDP/53", "DNS"),
            Map.entry("TCP/53", "DNS"), Map.entry("UDP/67", "DHCP"), Map.entry("UDP/68", "DHCP"),
            Map.entry("TCP/80", "HTTP"), Map.entry("UDP/123", "NTP"), Map.entry("UDP/137", "NetBIOS"),
            Map.entry("UDP/138", "NetBIOS"), Map.entry("TCP/139", "NetBIOS"), Map.entry("TCP/143", "IMAP"),
            Map.entry("TCP/443", "HTTPS"), Map.entry("UDP/443", "QUIC"), Map.entry("TCP/445", "SMB"),
            Map.entry("UDP/1900", "SSDP"), Map.entry("TCP/3389", "Escritorio remoto"), Map.entry("UDP/5353", "mDNS"),
            Map.entry("UDP/5355", "LLMNR"), Map.entry("TCP/8080", "HTTP alterno"), Map.entry("TCP/993", "IMAPS"),
            Map.entry("TCP/587", "SMTP (envío)"), Map.entry("UDP/3478", "STUN (llamadas)"));

    private WellKnownPorts() {
    }

    public static String name(String transport, int port) {
        return NAMES.get(transport + "/" + port);
    }
}
