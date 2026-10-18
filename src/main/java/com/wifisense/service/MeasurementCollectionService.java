package com.wifisense.service;

import com.wifisense.dto.MeasurementResponse;
import com.wifisense.event.MeasurementCollectedEvent;
import com.wifisense.event.NetworkEventPublisher;
import com.wifisense.model.Measurement;
import com.wifisense.model.Network;
import com.wifisense.model.ProtocolStatistic;
import com.wifisense.model.TrafficObservation;
import com.wifisense.network.DataSourceType;
import com.wifisense.network.NetworkDataSource;
import com.wifisense.network.NetworkDataSourceProvider;
import com.wifisense.network.NetworkSnapshot;
import com.wifisense.network.NetworkTarget;
import com.wifisense.repository.MeasurementRepository;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.ProtocolStatisticRepository;
import com.wifisense.repository.TrafficObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Write side: stores the readings of a network, collected from its data source or sent by its sensor. */
@Service
public class MeasurementCollectionService {

    private final NetworkRepository networks;
    private final MeasurementRepository measurements;
    private final TrafficObservationRepository traffic;
    private final ProtocolStatisticRepository protocols;
    private final NetworkDataSourceProvider dataSources;
    private final NetworkEventPublisher events;

    public MeasurementCollectionService(NetworkRepository networks, MeasurementRepository measurements,
                                        TrafficObservationRepository traffic, ProtocolStatisticRepository protocols,
                                        NetworkDataSourceProvider dataSources, NetworkEventPublisher events) {
        this.networks = networks;
        this.measurements = measurements;
        this.traffic = traffic;
        this.protocols = protocols;
        this.dataSources = dataSources;
        this.events = events;
    }

    /** Asks the network's data source for a reading (pulled sources) and stores it. */
    @Transactional
    public MeasurementResponse collect(Long networkId) {
        Network network = networks.findById(networkId)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        NetworkDataSource source = dataSources.forType(network.getDataSourceType());
        return store(network, source.collect(NetworkTarget.of(network)), source.type());
    }

    /**
     * Stores a reading that arrived by itself: the sensor of the network sent it. Same path as a collected one, so
     * the alerts, the activity log and the network state react the same way.
     */
    @Transactional
    public MeasurementResponse record(Long networkId, NetworkSnapshot snapshot, DataSourceType type) {
        Network network = networks.findById(networkId)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        return store(network, snapshot, type);
    }

    private MeasurementResponse store(Network network, NetworkSnapshot snapshot, DataSourceType type) {
        Measurement measurement = measurements.save(Measurement.from(network, snapshot, type));
        storeTraffic(network, snapshot);
        network.markCollected(snapshot.collectedAt());

        events.publish(new MeasurementCollectedEvent(network.getId(), network.getSsid(), measurement.getId(),
                type, snapshot.latencyMs(), snapshot.packetLossPct(), snapshot.collectedAt()));
        return MeasurementResponse.from(measurement);
    }

    private void storeTraffic(Network network, NetworkSnapshot snapshot) {
        if (snapshot.hasTraffic()) {
            traffic.save(new TrafficObservation(network, snapshot.trafficVolumeMb(), snapshot.packetCount(),
                    snapshot.collectedAt()));
        }
        Instant periodStart = network.getLastCollectedAt() == null
                ? snapshot.collectedAt().minusSeconds(60) : network.getLastCollectedAt();
        snapshot.protocolPackets().forEach((protocol, packets) -> protocols.save(
                new ProtocolStatistic(network, protocol, packets, periodStart, snapshot.collectedAt())));
    }
}
