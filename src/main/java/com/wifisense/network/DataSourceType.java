package com.wifisense.network;

/**
 * Where the data of a team network comes from. AGENT is the real one: the WiFiSense agent installed on a computer of
 * that network (its sensor) sends each reading. SIMULATION and SYSTEM only make sense in a local demo (invented data,
 * or the connectivity of the backend's own server). The rest are planned and not implemented.
 */
public enum DataSourceType {
    AGENT, SIMULATION, ROUTER_API, SNMP, SYSTEM, TRAFFIC_CAPTURE;

    /** The readings arrive from the sensor; the backend never goes to collect them. */
    public boolean isPushed() {
        return this == AGENT;
    }

    /** Data that does not describe the team's network: allowed only when demo sources are enabled. */
    public boolean isDemo() {
        return this == SIMULATION || this == SYSTEM;
    }
}
