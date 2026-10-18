package com.wifisense.service;

import com.wifisense.dto.NetworkReport;
import com.wifisense.model.Network;
import com.wifisense.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Basic aggregated reports and side-by-side comparison of networks. The statistics are computed by PostgreSQL
 * (MeasurementStatsRepository): the measurements of the period are never loaded into memory.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final NetworkRepository networks;
    private final MeasurementStatsRepository measurements;
    private final AlertRepository alerts;
    private final AnalysisResultRepository analyses;
    private final AiPredictionRepository predictions;
    private final Clock clock;

    public ReportService(NetworkRepository networks, MeasurementStatsRepository measurements, AlertRepository alerts,
                         AnalysisResultRepository analyses, AiPredictionRepository predictions, Clock clock) {
        this.networks = networks;
        this.measurements = measurements;
        this.alerts = alerts;
        this.analyses = analyses;
        this.predictions = predictions;
        this.clock = clock;
    }

    public NetworkReport report(Long networkId, Instant from, Instant to) {
        Instant end = to == null ? Instant.now(clock) : to;
        Instant start = from == null ? end.minus(Duration.ofHours(24)) : from;
        if (start.isAfter(end) || Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("El periodo del reporte debe ser válido y de máximo 31 días");
        }
        Network network = networks.findById(networkId)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        MeasurementStatsRepository.Stats stats = measurements.summarize(networkId, start, end);

        return new NetworkReport(networkId, network.getSsid(), start, end, (int) stats.samples(),
                stats.latency(), stats.jitter(), stats.packetLoss(), stats.bandwidth(), stats.signal(),
                alerts.countByNetworkIdAndCreatedAtBetween(networkId, start, end),
                analyses.countByNetworkIdAndCreatedAtBetween(networkId, start, end),
                predictions.countByNetworkIdAndAnomalyDetectedTrueAndCreatedAtBetween(networkId, start, end),
                stats.simulated());
    }

    /** Duplicated ids are ignored; order of first appearance is kept. */
    public List<NetworkReport> compare(List<Long> networkIds, Instant from, Instant to) {
        Set<Long> unique = new LinkedHashSet<>(networkIds);
        if (unique.size() < 2 || unique.size() > 6) {
            throw new IllegalArgumentException("Selecciona entre 2 y 6 redes distintas para comparar");
        }
        return unique.stream().map(id -> report(id, from, to)).toList();
    }
}
