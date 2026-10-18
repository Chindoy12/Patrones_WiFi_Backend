package com.wifisense.service;

import com.wifisense.dto.NetworkRequest;
import com.wifisense.dto.NetworkResponse;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkStatus;
import com.wifisense.model.Zone;
import com.wifisense.network.DataSourcePolicy;
import com.wifisense.network.DataSourceType;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NetworkService {

    private final NetworkRepository networks;
    private final ZoneRepository zones;
    private final DataSourcePolicy sources;

    public NetworkService(NetworkRepository networks, ZoneRepository zones, DataSourcePolicy sources) {
        this.networks = networks;
        this.zones = zones;
        this.sources = sources;
    }

    /** Data sources a network can be registered with (the sensor; the demo ones only when enabled). */
    public List<DataSourceType> selectableSources() {
        return sources.selectable();
    }

    public List<NetworkResponse> list(NetworkStatus status) {
        List<Network> result = status == null ? networks.findAllByOrderBySsid() : networks.findByStatusOrderBySsid(status);
        return result.stream().map(NetworkResponse::from).toList();
    }

    public NetworkResponse get(Long id) {
        return NetworkResponse.from(find(id));
    }

    @Transactional
    public NetworkResponse create(NetworkRequest request) {
        sources.requireSelectable(request.dataSourceType());
        if (networks.existsByBssid(request.bssid().toUpperCase())) {
            throw new DuplicateResourceException("Ya existe una red con el BSSID " + request.bssid());
        }
        Network network = new Network(findZone(request.zoneId()), request.ssid(), request.bssid(),
                request.frequencyBand(), request.channel(), request.securityType(), request.dataSourceType());
        return NetworkResponse.from(networks.save(network));
    }

    @Transactional
    public NetworkResponse update(Long id, NetworkRequest request) {
        Network network = find(id);
        if (request.dataSourceType() != network.getDataSourceType()) {
            sources.requireSelectable(request.dataSourceType());  // an old network may keep its source as it is
        }
        network.update(request.ssid(), request.bssid(), request.frequencyBand(), request.channel(),
                request.securityType());
        network.moveTo(findZone(request.zoneId()), request.dataSourceType());
        return NetworkResponse.from(network);
    }

    @Transactional
    public void delete(Long id) {
        networks.delete(find(id));
    }

    private Network find(Long id) {
        return networks.findWithZoneById(id).orElseThrow(() -> new ResourceNotFoundException("la red", id));
    }

    private Zone findZone(Long zoneId) {
        return zones.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("la zona", zoneId));
    }
}
