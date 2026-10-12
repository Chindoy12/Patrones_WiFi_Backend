package com.wifisense.service;

import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.AiQualityRequest;
import com.wifisense.analysis.InsufficientDataException;
import com.wifisense.dto.NetworkQualityResponse;
import com.wifisense.model.Measurement;
import com.wifisense.model.Network;
import com.wifisense.network.DataSourceType;
import com.wifisense.repository.MeasurementRepository;
import com.wifisense.repository.NetworkRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Health gauge of the team dashboard: the AI grades the latest measurement of a network. */
@Service
public class NetworkQualityService {

    private final NetworkRepository networks;
    private final MeasurementRepository measurements;
    private final AiAnalysisClient aiClient;

    public NetworkQualityService(NetworkRepository networks, MeasurementRepository measurements,
                                 AiAnalysisClient aiClient) {
        this.networks = networks;
        this.measurements = measurements;
        this.aiClient = aiClient;
    }

    @Transactional(readOnly = true)
    public NetworkQualityResponse quality(Long networkId) {
        Network network = networks.findById(networkId)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        Measurement latest = measurements.findByNetworkIdOrderByMeasuredAtDesc(networkId, PageRequest.of(0, 1))
                .stream().findFirst()
                .orElseThrow(() -> new InsufficientDataException("La red aún no tiene mediciones."));
        // The link bandwidth stands for the download speed; upload is not measured for monitored networks.
        var request = new AiQualityRequest(latest.getLatencyMs(), latest.getJitterMs(), latest.getPacketLossPct(),
                latest.getBandwidthMbps(), null, null,
                latest.getSignalStrengthDbm() == null ? null : latest.getSignalStrengthDbm().doubleValue());
        return new NetworkQualityResponse(network.getId(), network.getSsid(), latest.getMeasuredAt(),
                latest.getSource(), latest.getSource() == DataSourceType.SIMULATION, latest.getConnectedDevices(),
                aiClient.qualityScore(request));
    }
}
