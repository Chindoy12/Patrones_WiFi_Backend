package com.wifisense.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * What the sensor delivers: the statistics of what it captured, or why it could not. Only counts: the body has no
 * field for an address, a MAC or packet content. Everything is checked again against the capture's limits.
 */
public record AgentCaptureResult(
        @NotNull Long captureId,
        @NotNull @Pattern(regexp = "^(COMPLETED|FAILED)$", message = "Estado de captura desconocido.") String status,
        @Pattern(regexp = "^(NO_CONSENT|NO_CAPTURE_TOOL|PERMISSION_DENIED|NO_INTERFACE|CAPTURE_ERROR|UNSUPPORTED_OS)$",
                message = "Motivo de falla desconocido.") String failureReason,
        @Pattern(regexp = "^(DURATION|PACKET_LIMIT|BYTE_LIMIT|CANCELLED)$", message = "Motivo de parada desconocido.")
        String stopReason,
        @Pattern(regexp = "^(NPCAP|PKTMON|RAW_SOCKET|PACKET_SOCKET)$", message = "Herramienta desconocida.")
        String captureTool,
        @Size(max = 64) String interfaceName,
        @Pattern(regexp = "^[0-9A-Za-z.+-]{1,20}$", message = "Versión del agente inválida.") String agentVersion,
        @Min(0) @Max(400_000) Integer durationMs,
        @Min(0) @Max(100_000) Integer packetCount,
        @Min(0) @Max(104_857_600L) Long byteCount,
        @Min(0) @Max(100_000_000) Integer droppedByTool,
        @Min(0) @Max(100_000) Integer malformedPackets,
        @Min(0) @Max(104_857_600L) Long inboundBytes,
        @Min(0) @Max(104_857_600L) Long outboundBytes,
        @Min(0) @Max(100_000) Integer broadcastPackets,
        @Min(0) @Max(100_000) Integer multicastPackets,
        @Min(0) @Max(32_767) Integer distinctLocalHosts,
        @Min(0) @Max(100_000) Integer distinctRemoteHosts,
        @Size(max = 19, message = "Demasiados protocolos.") @Valid List<Protocol> protocols,
        @Size(max = 20, message = "Demasiados servicios.") @Valid List<Port> ports,
        @Size(max = 361, message = "Demasiados intervalos.") @Valid List<Interval> intervals) {

    public record Protocol(
            @NotNull @Pattern(regexp = "^(TCP|UDP|ICMP|ICMPV6|ARP|IGMP|DNS|MDNS|DHCP|SSDP|NETBIOS|LLMNR|HTTP|HTTPS|QUIC"
                    + "|NTP|IPV6_OTHER|IPV4_OTHER|OTHER)$", message = "Protocolo desconocido.") String protocol,
            @NotNull @Min(1) @Max(100_000) Integer packets,
            @NotNull @Min(0) @Max(104_857_600L) Long bytes) {
    }

    public record Port(
            @NotNull @Pattern(regexp = "^(TCP|UDP)$", message = "Transporte desconocido.") String transport,
            @NotNull @Min(0) @Max(65535) Integer port,
            @NotNull @Min(1) @Max(100_000) Integer packets,
            @NotNull @Min(0) @Max(104_857_600L) Long bytes) {
    }

    public record Interval(
            @NotNull @Min(0) @Max(360) Integer second,
            @NotNull @Min(1) @Max(100_000) Integer packets,
            @NotNull @Min(0) @Max(104_857_600L) Long bytes) {
    }

    public List<Protocol> protocolsOrEmpty() {
        return protocols == null ? List.of() : protocols;
    }

    public List<Port> portsOrEmpty() {
        return ports == null ? List.of() : ports;
    }

    public List<Interval> intervalsOrEmpty() {
        return intervals == null ? List.of() : intervals;
    }
}
