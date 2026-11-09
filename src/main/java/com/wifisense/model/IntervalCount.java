package com.wifisense.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Packets and bytes in one second of a capture (a row of traffic_capture_intervals; second 0 is the start). */
@Embeddable
public class IntervalCount {

    @Column(name = "second_offset", nullable = false)
    private int second;

    @Column(nullable = false)
    private int packets;

    @Column(nullable = false)
    private long bytes;

    protected IntervalCount() {
    }

    public IntervalCount(int second, int packets, long bytes) {
        this.second = second;
        this.packets = packets;
        this.bytes = bytes;
    }

    public int getSecond() { return second; }
    public int getPackets() { return packets; }
    public long getBytes() { return bytes; }
}
