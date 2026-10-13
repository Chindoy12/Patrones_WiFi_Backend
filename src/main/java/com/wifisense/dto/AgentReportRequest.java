package com.wifisense.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * What the WiFiSense agent sends. Everything except the version and the device count is optional because the agent
 * reports only what it could read; anything out of range is rejected so a forged report cannot store nonsense.
 */
public record AgentReportRequest(
        @NotBlank(message = "Falta la versión del agente.")
        @Pattern(regexp = "^[0-9A-Za-z.+-]{1,20}$", message = "Versión del agente inválida.")
        String agentVersion,
        @Size(max = 20) String platform,
        @Size(max = 64, message = "El nombre de la red es demasiado largo.") String ssid,
        @Pattern(regexp = "^(OPEN|WEP|WPA|WPA2|WPA3|UNKNOWN)$", message = "Tipo de seguridad desconocido.")
        String authentication,
        @Size(max = 20) String cipher,
        @Pattern(regexp = "^(2\\.4|5|6)$", message = "Banda desconocida.") String band,
        @Min(1) @Max(233) Integer channel,
        @Min(value = -100, message = "La señal debe estar entre -100 y 0 dBm.")
        @Max(value = 0, message = "La señal debe estar entre -100 y 0 dBm.") Integer signalDbm,
        @Min(0) @Max(100) Integer signalPercent,
        @DecimalMin("0") @DecimalMax("100000") Double rxRateMbps,
        @DecimalMin("0") @DecimalMax("100000") Double txRateMbps,
        @Min(0) @Max(500) Integer neighborsSameChannel,
        @Min(0) @Max(500) Integer neighborsTotal,
        @Pattern(regexp = "^LOCATION_PERMISSION$") String wifiNote,
        @DecimalMin("0") @DecimalMax("10000") Double latencyMs,
        @DecimalMin("0") @DecimalMax("10000") Double jitterMs,
        @DecimalMin("0") @DecimalMax("100") Double packetLossPct,
        @DecimalMin("0") @DecimalMax("10000") Double gatewayLatencyMs,
        @NotNull(message = "Falta la cantidad de equipos.") @Min(0) @Max(1024) Integer deviceCount,
        @Size(max = 256, message = "Demasiados equipos en un reporte.")
        List<@NotNull @Pattern(regexp = "^[0-9a-f]{24}$", message = "Huella de equipo inválida.") String> deviceHashes,
        @Size(max = 64) List<@NotNull @Min(1) @Max(65535) Integer> openPorts) {

    public List<String> deviceHashesOrEmpty() {
        return deviceHashes == null ? List.of() : deviceHashes;
    }

    public List<Integer> openPortsOrEmpty() {
        return openPorts == null ? List.of() : openPorts;
    }
}
