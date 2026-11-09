package com.wifisense.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Packets and bytes of one protocol in a capture (a row of traffic_capture_protocols). */
@Embeddable
public class ProtocolCount {

    @Column(nullable = false, length = 12)
    private String protocol;

    @Column(nullable = false)
    private int packets;

    @Column(nullable = false)
    private long bytes;

    protected ProtocolCount() {
    }

    public ProtocolCount(String protocol, int packets, long bytes) {
        this.protocol = protocol;
        this.packets = packets;
        this.bytes = bytes;
    }

    public String getProtocol() { return protocol; }
    public int getPackets() { return packets; }
    public long getBytes() { return bytes; }
}
