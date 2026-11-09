package com.wifisense.model;

/**
 * Why a capture failed, as the sensor found out on its computer (or the server, when the sensor never answered),
 * with the sentence the page shows and what to do about it. None is invented: each one comes from what the system
 * of the sensor's computer answered.
 */
public enum CaptureFailure {
    NO_CONSENT("En el computador del sensor no se han permitido las capturas técnicas. Quien administra ese computador "
            + "debe ejecutar «WiFiSenseAgente.exe capture-consent on»."),
    NO_CAPTURE_TOOL("El computador del sensor no tiene una herramienta de captura que el agente pueda usar: instala "
            + "Npcap (npcap.com) o ejecuta el agente como administrador para usar pktmon de Windows."),
    PERMISSION_DENIED("El sistema no dio permiso para capturar: pktmon y los sockets sin procesar necesitan que el "
            + "agente corra como administrador; Npcap puede estar limitado a administradores."),
    NO_INTERFACE("No se encontró una interfaz de red en la que capturar (¿el computador está desconectado?)."),
    CAPTURE_ERROR("La herramienta de captura falló a mitad de la captura; no se entregaron estadísticas."),
    UNSUPPORTED_OS("El sistema operativo del sensor no permite capturas con el agente."),
    AGENT_NO_RESPONSE("El sensor no entregó la captura a tiempo: revisa que el computador esté encendido y el agente "
            + "abierto, y vuelve a intentar.");

    private final String message;

    CaptureFailure(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
