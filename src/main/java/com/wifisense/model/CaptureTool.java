package com.wifisense.model;

/**
 * The capture tools the agent can use (Strategy on the agent's side). None of them puts the Wi-Fi adapter in monitor
 * mode: they see what reaches the sensor's own network interface.
 */
public enum CaptureTool {
    NPCAP("Npcap"),
    PKTMON("pktmon de Windows"),
    RAW_SOCKET("Socket sin procesar de Windows"),
    PACKET_SOCKET("Socket de paquetes de Linux");

    private final String label;

    CaptureTool(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
