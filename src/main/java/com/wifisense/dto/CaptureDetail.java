package com.wifisense.dto;

import com.wifisense.model.CaptureFinding;
import com.wifisense.model.TrafficCapture;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * A capture with everything it measured and what the analysis concluded. Three different "losses" never mix:
 * {@code droppedByTool} is what the capture tool reported it could not keep; the packet loss of the connection tests
 * is a separate measurement of the network; and an anomaly is an inference of the findings.
 */
public record CaptureDetail(CaptureResponse capture, Totals totals, List<Count> protocols, List<Port> ports,
                            List<Interval> intervals, List<Finding> observed, List<Finding> inferred, String scope) {

    /** What the capture can and cannot see, said with every result. */
    public static final String SCOPE = "Captura local: el tráfico del computador del sensor y la difusión de la red. "
            + "No incluye lo que los demás equipos envían a Internet ni el contenido de ningún paquete: solo cabeceras "
            + "contadas en el computador del sensor.";

    public record Totals(Integer packets, Long bytes, Integer droppedByTool, Integer malformed, Long inboundBytes,
                         Long outboundBytes, Integer broadcast, Integer multicast, Integer localHosts,
                         Integer remoteHosts) {
    }

    public record Count(String protocol, int packets, long bytes) {
    }

    public record Port(String transport, int port, String service, int packets, long bytes) {
    }

    public record Interval(int second, int packets, long bytes) {
    }

    public record Finding(String code, String severity, String title, String evidence, String recommendation,
                          BigDecimal confidence, String source) {

        static Finding of(CaptureFinding f) {
            return new Finding(f.getCode(), f.getSeverity().name(), f.getTitle(), f.getEvidence(),
                    f.getRecommendation(), f.getConfidence(), f.getSource().name());
        }
    }

    public static CaptureDetail from(TrafficCapture c) {
        Comparator<CaptureFinding> bySeverity = Comparator.comparing((CaptureFinding f) -> f.getSeverity().ordinal())
                .reversed();
        return new CaptureDetail(CaptureResponse.from(c),
                new Totals(c.getPacketCount(), c.getByteCount(), c.getDroppedByTool(), c.getMalformedPackets(),
                        c.getInboundBytes(), c.getOutboundBytes(), c.getBroadcastPackets(), c.getMulticastPackets(),
                        c.getDistinctLocalHosts(), c.getDistinctRemoteHosts()),
                c.getProtocols().stream().map(p -> new Count(p.getProtocol(), p.getPackets(), p.getBytes())).toList(),
                c.getPorts().stream().map(p -> new Port(p.getTransport(), p.getPort(),
                        WellKnownPorts.name(p.getTransport(), p.getPort()), p.getPackets(), p.getBytes())).toList(),
                c.getIntervals().stream().map(i -> new Interval(i.getSecond(), i.getPackets(), i.getBytes()))
                        .sorted(Comparator.comparingInt(Interval::second)).toList(),
                c.getFindings().stream().filter(f -> f.getCategory().name().equals("OBSERVED")).sorted(bySeverity)
                        .map(Finding::of).toList(),
                c.getFindings().stream().filter(f -> f.getCategory().name().equals("INFERRED")).sorted(bySeverity)
                        .map(Finding::of).toList(),
                SCOPE);
    }
}
