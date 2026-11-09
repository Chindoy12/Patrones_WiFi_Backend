package com.wifisense.model;

/** The sensitive operations that leave a trace in the audit, with the label the audit page shows. */
public enum AuditAction {
    CAPTURE_REQUESTED("Pidió una captura técnica"),
    CAPTURE_CANCELLED("Detuvo una captura técnica"),
    CAPTURE_VIEWED("Consultó los resultados de una captura"),
    USER_CREATED("Creó una cuenta del equipo"),
    USER_ROLE_CHANGED("Cambió el rol de una cuenta"),
    USER_DELETED("Borró una cuenta"),
    DEVICE_BLOCKED("Marcó un equipo como no autorizado"),
    DEVICE_UNBLOCKED("Volvió a autorizar un equipo"),
    SENSOR_PAIRING_CREATED("Generó un código para instalar un sensor"),
    SENSOR_REVOKED("Desconectó el sensor de una red"),
    AGENT_PAIRING_CREATED("Generó un código para instalar su agente"),
    AGENT_KEY_REVOKED("Desconectó el agente de su red"),
    NETWORK_DELETED("Borró una red del equipo"),
    RETENTION_PURGED("Borró datos viejos según la política de retención");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
