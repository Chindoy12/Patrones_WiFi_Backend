package com.wifisense.service;

import com.wifisense.analysis.AnalysisType;
import com.wifisense.analysis.NetworkAnalysisFacade;
import com.wifisense.model.Network;
import com.wifisense.network.DataSourcePolicy;
import com.wifisense.repository.NetworkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Periodic monitoring: collects and runs a threshold analysis for every network whose
 * state-dependent interval has elapsed (critical networks are checked more often). Only the networks whose source
 * the backend collects by itself (the demo ones, when enabled): a network with a sensor gets its readings from it.
 */
@Component
@ConditionalOnProperty(name = "wifisense.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringJob {

    private static final Logger log = LoggerFactory.getLogger(MonitoringJob.class);

    private final NetworkRepository networks;
    private final MeasurementCollectionService collection;
    private final NetworkAnalysisFacade analysis;
    private final DataSourcePolicy sources;
    private final Clock clock;

    public MonitoringJob(NetworkRepository networks, MeasurementCollectionService collection,
                         NetworkAnalysisFacade analysis, DataSourcePolicy sources, Clock clock) {
        this.networks = networks;
        this.collection = collection;
        this.analysis = analysis;
        this.sources = sources;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wifisense.monitoring.tick:PT30S}", initialDelayString = "PT10S")
    public void run() {
        Instant now = Instant.now(clock);
        List<Long> due = networks.findAll().stream()
                .filter(network -> sources.collectsAutomatically(network.getDataSourceType()))
                .filter(network -> network.isCollectionDue(now))
                .map(Network::getId)
                .toList();
        due.forEach(this::monitor);
    }

    private void monitor(Long networkId) {
        try {
            collection.collect(networkId);
            analysis.analyze(networkId, AnalysisType.THRESHOLD, null);
        } catch (RuntimeException e) {
            log.warn("Monitoring of network {} skipped: {}", networkId, e.getMessage());
        }
    }
}
