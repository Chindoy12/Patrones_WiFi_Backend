package com.wifisense.model;

import jakarta.persistence.*;

/** Which email alerts a user wants. All on by default; absent row means the defaults. */
@Entity
@Table(name = "alert_preferences")
public class AlertPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email_new_device", nullable = false)
    private boolean emailNewDevice = true;

    @Column(name = "email_security", nullable = false)
    private boolean emailSecurity = true;

    @Column(name = "email_anomaly", nullable = false)
    private boolean emailAnomaly = true;

    protected AlertPreference() {
    }

    public AlertPreference(Long userId) {
        this.userId = userId;
    }

    public boolean wants(AlertType type) {
        return switch (type) {
            case NEW_DEVICE, UNAUTHORIZED_DEVICE -> emailNewDevice;
            case SECURITY -> emailSecurity;
            case ANOMALY -> emailAnomaly;
        };
    }

    public void set(boolean newDevice, boolean security, boolean anomaly) {
        this.emailNewDevice = newDevice;
        this.emailSecurity = security;
        this.emailAnomaly = anomaly;
    }

    public Long getUserId() { return userId; }
    public boolean isEmailNewDevice() { return emailNewDevice; }
    public boolean isEmailSecurity() { return emailSecurity; }
    public boolean isEmailAnomaly() { return emailAnomaly; }
}
