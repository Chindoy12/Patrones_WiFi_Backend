package com.wifisense.dto;

import jakarta.validation.Valid;
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
        // No size limit: some systems describe the cipher with a long text. It is trimmed (cipherForAi), not rejected.
        String cipher,
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
        @Size(max = 256, message = "Demasiados equipos en un reporte.") @Valid List<DeviceInfo> devices,
        @Size(max = 64) List<@NotNull @Min(1) @Max(65535) Integer> openPorts,
        @Size(max = 60, message = "Demasiados canales en un reporte.") @Valid List<ChannelCount> channelCounts) {

    /**
     * One connected device: its fingerprint and, when the agent could read them, the maker and the name the device
     * announces on the network (never the MAC). {@code privateMac} says the device uses a random address, which
     * phones and tablets do on every network: its maker cannot be known but it is almost surely one of those.
     */
    public record DeviceInfo(
            @NotNull @Pattern(regexp = "^[0-9a-f]{24}$", message = "Huella de equipo inválida.") String id,
            @Size(max = 40) String vendor,
            @Size(max = 63) String name,
            Boolean privateMac) {
    }

    /** Neighbouring access points (not the user's own network) the agent saw on one channel. */
    public record ChannelCount(
            @NotNull @Min(1) @Max(233) Integer channel,
            @NotNull @Min(0) @Max(500) Integer networks) {
    }

    /** All fingerprints, taking them from {@code devices} (new agent) or {@code deviceHashes} (old agent). */
    public List<String> deviceHashesOrEmpty() {
        if (devices != null && !devices.isEmpty()) {
            return devices.stream().map(DeviceInfo::id).toList();
        }
        return deviceHashes == null ? List.of() : deviceHashes;
    }

    /** Maker per fingerprint, blank for the old agent that did not send it. */
    public java.util.Map<String, String> vendorsByFingerprint() {
        return fieldByFingerprint(DeviceInfo::vendor);
    }

    /** Announced name per fingerprint, blank for the old agent that did not send it. */
    public java.util.Map<String, String> namesByFingerprint() {
        return fieldByFingerprint(DeviceInfo::name);
    }

    private java.util.Map<String, String> fieldByFingerprint(
            java.util.function.Function<DeviceInfo, String> field) {
        if (devices == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, String> byId = new java.util.LinkedHashMap<>();
        for (DeviceInfo device : devices) {
            String value = field.apply(device);
            if (value != null && !value.isBlank()) {
                byId.putIfAbsent(device.id(), value.trim());
            }
        }
        return byId;
    }

    /** Fingerprints of the devices that use a private (random) MAC. */
    public java.util.Set<String> privateFingerprints() {
        if (devices == null) {
            return java.util.Set.of();
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (DeviceInfo device : devices) {
            if (Boolean.TRUE.equals(device.privateMac())) {
                ids.add(device.id());
            }
        }
        return ids;
    }

    public List<ChannelCount> channelCountsOrEmpty() {
        return channelCounts == null ? List.of() : channelCounts;
    }

    /** Cipher trimmed to what the AI accepts (20 chars), so a long description is kept, not rejected. */
    public String cipherForAi() {
        if (cipher == null || cipher.isBlank()) {
            return null;
        }
        String value = cipher.trim();
        return value.length() <= 20 ? value : value.substring(0, 20);
    }

    public List<Integer> openPortsOrEmpty() {
        return openPorts == null ? List.of() : openPorts;
    }
}
