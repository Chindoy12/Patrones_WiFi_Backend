package com.wifisense.model;

/** Why a completed capture stopped: it reached one of its limits, or an administrator asked to stop it. */
public enum CaptureStopReason {
    DURATION("Llegó a la duración máxima."),
    PACKET_LIMIT("Llegó al límite de paquetes."),
    BYTE_LIMIT("Llegó al límite de bytes."),
    CANCELLED("Un administrador la detuvo; las estadísticas son de lo que alcanzó a capturar.");

    private final String message;

    CaptureStopReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
