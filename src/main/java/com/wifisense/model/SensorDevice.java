package com.wifisense.model;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A device the sensor has seen on a team network (like {@link AgentDevice} for Mi Wi-Fi). An administrator can name it
 * and mark it as not authorized.
 */
@Entity
@Table(name = "network_sensor_devices")
@IdClass(ObservedDevice.Key.class)
public class SensorDevice extends ObservedDevice {

    protected SensorDevice() {
    }

    public SensorDevice(Long networkId, String fingerprint, Instant seenAt) {
        super(networkId, fingerprint, seenAt);
    }
}
