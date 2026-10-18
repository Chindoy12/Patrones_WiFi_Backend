package com.wifisense.network;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Which data sources a team network may use and which ones the monitoring job collects by itself. */
@Component
public class DataSourcePolicy {

    private final boolean demoEnabled;

    public DataSourcePolicy(DataSourceProperties properties) {
        this.demoEnabled = properties.demoEnabled();
    }

    /** The sensor always; the demo sources only when they are enabled. The planned ones never. */
    public List<DataSourceType> selectable() {
        List<DataSourceType> types = new ArrayList<>(List.of(DataSourceType.AGENT));
        if (demoEnabled) {
            types.add(DataSourceType.SIMULATION);
            types.add(DataSourceType.SYSTEM);
        }
        return List.copyOf(types);
    }

    public void requireSelectable(DataSourceType type) {
        if (!selectable().contains(type)) {
            throw new IllegalArgumentException(type != null && type.isDemo()
                    ? "Las redes simuladas están desactivadas: registra la red con su sensor WiFiSense."
                    : "Esa fuente de datos aún no está disponible: registra la red con su sensor WiFiSense.");
        }
    }

    /** Pulled by the monitoring job: only demo sources, and only when enabled. The sensor pushes its readings. */
    public boolean collectsAutomatically(DataSourceType type) {
        return demoEnabled && type != null && type.isDemo();
    }
}
