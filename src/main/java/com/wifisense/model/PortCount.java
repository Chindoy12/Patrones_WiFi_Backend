package com.wifisense.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Packets and bytes of one service (transport and port) in a capture (a row of traffic_capture_ports). */
@Embeddable
public class PortCount {

    @Column(nullable = false, length = 4)
    private String transport;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private int packets;

    @Column(nullable = false)
    private long bytes;

    protected PortCount() {
    }

    public PortCount(String transport, int port, int packets, long bytes) {
        this.transport = transport;
        this.port = port;
        this.packets = packets;
        this.bytes = bytes;
    }

    public String getTransport() { return transport; }
    public int getPort() { return port; }
    public int getPackets() { return packets; }
    public long getBytes() { return bytes; }
}
