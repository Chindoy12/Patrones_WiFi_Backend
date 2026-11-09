package com.wifisense.service;

import com.wifisense.model.CaptureFinding;
import com.wifisense.model.CaptureStopReason;
import com.wifisense.model.FindingSeverity;
import com.wifisense.model.FindingSource;
import com.wifisense.model.IntervalCount;
import com.wifisense.model.PortCount;
import com.wifisense.model.ProtocolCount;
import com.wifisense.model.TrafficCapture;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The backend's own reading of a completed capture. It always produces the facts (OBSERVED: counts computed from the
 * captured packets, with the numbers as evidence); when the AI is not available it also adds the one inference it
 * can make on its own, a traffic burst, with a moderate confidence. It never says a device is malicious.
 */
@Component
public class CaptureFindingRules {

    /** Protocols and ports that travel without encryption. */
    static final Set<String> CLEARTEXT_PROTOCOLS = Set.of("HTTP");
    static final Set<Integer> CLEARTEXT_TCP_PORTS = Set.of(21, 23, 80, 110, 143);

    public List<CaptureFinding> facts(TrafficCapture c) {
        List<CaptureFinding> facts = new ArrayList<>();
        int packets = c.getPacketCount() == null ? 0 : c.getPacketCount();
        facts.add(CaptureFinding.observed("CAPTURE_SCOPE", FindingSeverity.INFO, "Qué vio esta captura",
                "Tráfico del computador del sensor y difusión de la red durante "
                        + seconds(c.getDurationMs()) + ": " + packets + " paquetes, " + bytes(c.getByteCount()) + ".",
                null, FindingSource.RULES));
        if (packets == 0) {
            facts.add(CaptureFinding.observed("EMPTY_CAPTURE", FindingSeverity.WARNING, "La captura no vio paquetes",
                    "0 paquetes en " + seconds(c.getDurationMs()) + " con " + c.getCaptureTool().label() + ".",
                    "Revisa que el computador del sensor esté conectado y que la interfaz elegida sea la de la red; "
                            + "vuelve a capturar mientras usas la red.", FindingSource.RULES));
            return facts;
        }
        int dropped = c.getDroppedByTool() == null ? 0 : c.getDroppedByTool();
        if (c.getDroppedByTool() != null) {
            double share = 100.0 * dropped / (packets + dropped);
            facts.add(CaptureFinding.observed("TOOL_DROPS", share > 1 ? FindingSeverity.WARNING : FindingSeverity.INFO,
                    dropped == 0 ? "La herramienta no perdió paquetes" : "La herramienta perdió paquetes",
                    "La herramienta de captura informó " + dropped + " paquetes que no alcanzó a guardar ("
                            + pct(share) + "). No es pérdida de la red: es del computador que captura.",
                    share > 1 ? "Repite con un límite de paquetes menor o con el computador menos ocupado." : null,
                    FindingSource.RULES));
        }
        int broadcast = nonNull(c.getBroadcastPackets()) + nonNull(c.getMulticastPackets());
        double broadcastShare = 100.0 * broadcast / packets;
        facts.add(CaptureFinding.observed("BROADCAST_SHARE",
                broadcastShare > 30 ? FindingSeverity.WARNING : FindingSeverity.INFO,
                broadcastShare > 30 ? "Mucha difusión en la red" : "Difusión de la red",
                broadcast + " de " + packets + " paquetes (" + pct(broadcastShare)
                        + ") fueron de difusión o multidifusión (para todos los equipos de la red).",
                broadcastShare > 30 ? "Revisa equipos que anuncian servicios sin parar (impresoras, televisores, "
                        + "cámaras) y el ARP de la red." : null, FindingSource.RULES));
        c.getProtocols().stream().max(Comparator.comparingInt(ProtocolCount::getPackets)).ifPresent(top ->
                facts.add(CaptureFinding.observed("TOP_PROTOCOL", FindingSeverity.INFO, "Protocolo principal: "
                        + top.getProtocol(), top.getPackets() + " de " + packets + " paquetes ("
                        + pct(100.0 * top.getPackets() / packets) + ") fueron " + top.getProtocol() + ".", null,
                        FindingSource.RULES)));
        int cleartext = c.getProtocols().stream().filter(p -> CLEARTEXT_PROTOCOLS.contains(p.getProtocol()))
                .mapToInt(ProtocolCount::getPackets).sum()
                + c.getPorts().stream().filter(p -> "TCP".equals(p.getTransport())
                        && CLEARTEXT_TCP_PORTS.contains(p.getPort()) && p.getPort() != 80)
                .mapToInt(PortCount::getPackets).sum();
        if (cleartext > 0) {
            facts.add(CaptureFinding.observed("CLEARTEXT_TRAFFIC", FindingSeverity.WARNING, "Tráfico sin cifrar",
                    "Al menos " + cleartext + " paquetes usaron protocolos sin cifrado (HTTP, FTP, Telnet, POP3 o IMAP).",
                    "Busca qué programa o página los usa y cámbialo por su versión cifrada (HTTPS, SFTP, SSH).",
                    FindingSource.RULES));
        }
        int malformed = nonNull(c.getMalformedPackets());
        if (malformed > 0) {
            facts.add(CaptureFinding.observed("MALFORMED_PACKETS", FindingSeverity.INFO, "Paquetes ilegibles",
                    malformed + " paquetes (" + pct(100.0 * malformed / packets)
                            + ") tenían cabeceras que el agente no pudo leer; no entran en los protocolos.", null,
                    FindingSource.RULES));
        }
        if (c.getStopReason() == CaptureStopReason.PACKET_LIMIT || c.getStopReason() == CaptureStopReason.BYTE_LIMIT) {
            facts.add(CaptureFinding.observed("STOPPED_BY_LIMIT", FindingSeverity.INFO, "Se detuvo por su límite",
                    c.getStopReason().message() + " Las estadísticas cubren " + seconds(c.getDurationMs())
                            + ", no toda la duración pedida.", "Si necesitas más tiempo, sube el límite de paquetes.",
                    FindingSource.RULES));
        }
        return facts;
    }

    /** The inference the backend can make without the AI: one second far above the others. */
    public List<CaptureFinding> fallbackInferences(TrafficCapture c) {
        List<IntervalCount> intervals = c.getIntervals();
        if (intervals.size() < 5) {
            return List.of();
        }
        int[] counts = intervals.stream().mapToInt(IntervalCount::getPackets).sorted().toArray();
        double median = counts.length % 2 == 1 ? counts[counts.length / 2]
                : (counts[counts.length / 2 - 1] + counts[counts.length / 2]) / 2.0;
        IntervalCount peak = intervals.stream().max(Comparator.comparingInt(IntervalCount::getPackets)).orElseThrow();
        if (median <= 0 || peak.getPackets() < 50 || peak.getPackets() < 5 * median) {
            return List.of();
        }
        return List.of(CaptureFinding.inferred("TRAFFIC_BURST", FindingSeverity.WARNING, "Ráfaga de tráfico",
                "El segundo " + peak.getSecond() + " tuvo " + peak.getPackets() + " paquetes, "
                        + String.format(Locale.ROOT, "%.1f", peak.getPackets() / median)
                        + " veces la mediana (" + String.format(Locale.ROOT, "%.0f", median) + ").",
                "Puede ser una descarga o una actualización. Repite la captura a la misma hora para ver si se repite.",
                0.5, FindingSource.RULES));
    }

    private static int nonNull(Integer value) {
        return value == null ? 0 : value;
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.1f %%", value);
    }

    private static String seconds(Integer ms) {
        return ms == null ? "la captura" : String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
    }

    private static String bytes(Long value) {
        if (value == null) {
            return "? bytes";
        }
        return value >= 1024 * 1024 ? String.format(Locale.ROOT, "%.1f MB", value / 1048576.0)
                : value >= 1024 ? String.format(Locale.ROOT, "%.1f KB", value / 1024.0) : value + " bytes";
    }
}
