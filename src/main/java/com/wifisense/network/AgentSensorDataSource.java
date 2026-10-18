package com.wifisense.network;

/**
 * The network is measured by its sensor (the WiFiSense agent installed there), which sends every reading itself:
 * there is nothing for the backend to collect, so asking for a reading explains where the data comes from.
 */
public class AgentSensorDataSource implements NetworkDataSource {

    @Override
    public DataSourceType type() {
        return DataSourceType.AGENT;
    }

    @Override
    public NetworkSnapshot collect(NetworkTarget target) {
        throw new DataSourceUnavailableException(
                "Esta red la mide su sensor: las mediciones llegan solas con cada reporte (unas cada 5 minutos).");
    }
}
