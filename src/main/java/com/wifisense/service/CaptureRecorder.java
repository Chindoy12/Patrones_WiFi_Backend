package com.wifisense.service;

import com.wifisense.dto.AgentCaptureResult;
import com.wifisense.model.CaptureFailure;
import com.wifisense.model.CaptureStopReason;
import com.wifisense.model.CaptureTool;
import com.wifisense.model.IntervalCount;
import com.wifisense.model.PortCount;
import com.wifisense.model.ProtocolCount;
import com.wifisense.model.TrafficCapture;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns what the sensor delivered into the capture's result, after checking that it is consistent with itself and
 * with the limits the administrator set: a forged or broken result is refused (400) instead of storing numbers that
 * cannot be true. The table checks the limits again.
 */
@Component
public class CaptureRecorder {

    /** Margin over the capture's duration for the time the tool takes to start and stop. */
    static final int DURATION_MARGIN_MS = 60_000;

    public void apply(TrafficCapture capture, AgentCaptureResult result, Instant now) {
        TrafficCapture.CaptureDetails details = new TrafficCapture.CaptureDetails(
                result.captureTool() == null ? null : CaptureTool.valueOf(result.captureTool()),
                SsidText.cleanLabel(result.interfaceName(), 64), result.agentVersion(), result.durationMs());
        if ("FAILED".equals(result.status())) {
            capture.fail(now, result.failureReason() == null ? CaptureFailure.CAPTURE_ERROR
                    : CaptureFailure.valueOf(result.failureReason()), details);
            return;
        }
        check(capture, result);
        List<ProtocolCount> protocols = result.protocolsOrEmpty().stream()
                .map(p -> new ProtocolCount(p.protocol(), p.packets(), p.bytes())).toList();
        List<PortCount> ports = result.portsOrEmpty().stream()
                .map(p -> new PortCount(p.transport(), p.port(), p.packets(), p.bytes())).toList();
        List<IntervalCount> intervals = result.intervalsOrEmpty().stream()
                .map(i -> new IntervalCount(i.second(), i.packets(), i.bytes())).toList();
        TrafficCapture.CaptureTotals totals = new TrafficCapture.CaptureTotals(result.packetCount(),
                result.byteCount(), result.droppedByTool(), result.malformedPackets(), result.inboundBytes(),
                result.outboundBytes(), result.broadcastPackets(), result.multicastPackets(),
                result.distinctLocalHosts(), result.distinctRemoteHosts());
        capture.complete(now, CaptureStopReason.valueOf(result.stopReason()), details, totals, protocols, ports,
                intervals);
    }

    static void check(TrafficCapture capture, AgentCaptureResult r) {
        require(r.stopReason() != null, "Una captura completada dice por qué se detuvo.");
        require(r.captureTool() != null, "Una captura completada dice con qué herramienta se hizo.");
        require(r.packetCount() != null && r.byteCount() != null, "Faltan los totales de la captura.");
        int packets = r.packetCount();
        long bytes = r.byteCount();
        require(packets <= capture.getMaxPackets(), "La captura tiene más paquetes que su límite.");
        require(bytes <= capture.getMaxBytes(), "La captura tiene más bytes que su límite.");
        require(r.durationMs() == null || r.durationMs() <= capture.getMaxSeconds() * 1000 + DURATION_MARGIN_MS,
                "La captura duró más que su límite.");
        require(packets > 0 || bytes == 0, "Una captura sin paquetes no tiene bytes.");
        require(at(r.broadcastPackets()) + at(r.multicastPackets()) <= packets,
                "Hay más paquetes de difusión que paquetes.");
        require(at(r.malformedPackets()) <= packets, "Hay más paquetes ilegibles que paquetes.");
        require(at(r.inboundBytes()) + at(r.outboundBytes()) <= bytes, "Entrada y salida suman más que el total.");

        Set<String> names = new HashSet<>();
        long protocolPackets = 0;
        for (AgentCaptureResult.Protocol p : r.protocolsOrEmpty()) {
            require(names.add(p.protocol()), "El protocolo " + p.protocol() + " aparece dos veces.");
            protocolPackets += p.packets();
        }
        require(protocolPackets <= packets, "Los protocolos suman más paquetes que la captura.");

        Set<String> services = new HashSet<>();
        long portPackets = 0;
        for (AgentCaptureResult.Port p : r.portsOrEmpty()) {
            require(services.add(p.transport() + "/" + p.port()), "Un servicio aparece dos veces.");
            portPackets += p.packets();
        }
        require(portPackets <= packets, "Los servicios suman más paquetes que la captura.");

        Set<Integer> seconds = new HashSet<>();
        long intervalPackets = 0;
        int lastSecond = capture.getMaxSeconds() + DURATION_MARGIN_MS / 1000;
        for (AgentCaptureResult.Interval i : r.intervalsOrEmpty()) {
            require(seconds.add(i.second()), "Un segundo aparece dos veces.");
            require(i.second() <= lastSecond, "Hay tráfico después del final de la captura.");
            intervalPackets += i.packets();
        }
        require(intervalPackets <= packets, "Los segundos suman más paquetes que la captura.");
    }

    private static long at(Number value) {
        return value == null ? 0 : value.longValue();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
