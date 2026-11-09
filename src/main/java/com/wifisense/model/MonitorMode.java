package com.wifisense.model;

/**
 * Whether the Wi-Fi adapter of an agent's computer declares monitor mode (netsh wlan show wirelesscapabilities).
 * It is only reported: WiFiSense never switches an adapter to monitor mode.
 */
public enum MonitorMode {
    SUPPORTED, UNSUPPORTED, UNKNOWN
}
