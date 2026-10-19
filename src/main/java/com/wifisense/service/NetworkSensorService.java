package com.wifisense.service;

import com.wifisense.dto.AgentPairResponse;
import com.wifisense.dto.AgentPairingCodeResponse;
import com.wifisense.dto.SensorStatus;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkSensor;
import com.wifisense.model.NetworkSensorPairingCode;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.NetworkSensorPairingCodeRepository;
import com.wifisense.repository.NetworkSensorRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The sensor of a team network: the same WiFiSense agent the end users install, on a computer connected to that
 * network. The panel shows an 8-character code (15 minutes, one use); the installer exchanges it for the sensor key,
 * and from then on every report of that key belongs to that network. Keys and codes are stored only as SHA-256.
 */
@Service
public class NetworkSensorService {

    private final NetworkRepository networks;
    private final NetworkSensorRepository sensors;
    private final NetworkSensorPairingCodeRepository codes;
    private final AgentReportThrottle throttle;
    private final AgentProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public NetworkSensorService(NetworkRepository networks, NetworkSensorRepository sensors,
                                NetworkSensorPairingCodeRepository codes, AgentReportThrottle throttle,
                                AgentProperties properties, Clock clock) {
        this.networks = networks;
        this.sensors = sensors;
        this.codes = codes;
        this.throttle = throttle;
        this.properties = properties;
        this.clock = clock;
    }

    /** A new code to install the sensor of the network; a previous pending code stops working. */
    @Transactional
    public AgentPairingCodeResponse createPairingCode(Long networkId) {
        Network network = networks.findById(networkId)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        if (!network.getDataSourceType().isPushed()) {
            throw new IllegalStateException(
                    "Esta red no se mide con sensor. Edítala y elige «Sensor WiFiSense» como fuente de datos.");
        }
        codes.deleteByNetworkId(networkId);
        String code = AgentPairingService.newCode(random);
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(AgentPairingService.TTL);
        codes.save(new NetworkSensorPairingCode(network, AgentPairingService.hash(code), now, expiresAt));
        return new AgentPairingCodeResponse(AgentPairingService.display(code), expiresAt);
    }

    /**
     * Exchanges a code of the team panel for a new sensor key (the previous sensor of that network stops working).
     * Empty when the code is not a sensor code. Called by {@link AgentPairingService}, which already counted the
     * attempt and normalized the code.
     */
    @Transactional
    Optional<AgentPairResponse> pair(String code) {
        Optional<NetworkSensorPairingCode> found = codes.findByCodeHash(AgentPairingService.hash(code));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        NetworkSensorPairingCode pairing = found.get();
        Long networkId = pairing.getNetwork().getId();
        String ssid = pairing.getNetwork().getSsid();
        codes.delete(pairing);
        if (!pairing.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new InvalidPairingCodeException();
        }
        sensors.findByNetworkId(networkId).ifPresent(old -> throttle.forgetSensor(old.getId()));
        sensors.deleteByNetworkId(networkId);  // clears the persistence context: the network is referenced again
        String key = AgentKeyService.generate(random);
        NetworkSensor sensor = sensors.saveAndFlush(new NetworkSensor(networks.getReferenceById(networkId),
                AgentKeyService.hash(key), AgentKeyService.visiblePart(key)));
        return Optional.of(new AgentPairResponse(key, sensor.getKeyPrefix(), ssid));
    }

    /** The sensor that owns a key, with its network; empty for malformed keys and keys that are not of a sensor. */
    @Transactional(readOnly = true)
    public Optional<NetworkSensor> authenticate(String key) {
        if (!AgentKeyService.wellFormed(key)) {
            return Optional.empty();
        }
        return sensors.findByKeyHash(AgentKeyService.hash(key));
    }

    @Transactional
    public void markSeen(Long sensorId, Instant at, String agentVersion, String platform, String ssid) {
        sensors.markSeen(sensorId, at, agentVersion, platform, ssid);
    }

    @Transactional(readOnly = true)
    public SensorStatus status(Long networkId) {
        if (!networks.existsById(networkId)) {
            throw new ResourceNotFoundException("la red", networkId);
        }
        Instant now = Instant.now(clock);
        Instant pending = codes.findByNetworkId(networkId).map(NetworkSensorPairingCode::getExpiresAt)
                .filter(expires -> expires.isAfter(now)).orElse(null);
        return sensors.findByNetworkId(networkId)
                .map(s -> new SensorStatus(true, s.getLastSeenAt() != null
                        && s.getLastSeenAt().isAfter(now.minus(properties.onlineWindow())), s.getKeyPrefix(),
                        s.getCreatedAt(), s.getLastSeenAt(), s.getAgentVersion(), s.getPlatform(), pending, s.getLastSsid()))
                .orElse(new SensorStatus(false, false, null, null, null, null, null, pending, null));
    }

    /** The sensor stops being accepted (for example, the computer was lost); a pending code is cancelled too. */
    @Transactional
    public void revoke(Long networkId) {
        if (!networks.existsById(networkId)) {
            throw new ResourceNotFoundException("la red", networkId);
        }
        sensors.findByNetworkId(networkId).ifPresent(old -> throttle.forgetSensor(old.getId()));
        sensors.deleteByNetworkId(networkId);
        codes.deleteByNetworkId(networkId);
    }

    /** Hourly cleanup of expired codes. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT9M")
    @Transactional
    public void purgeExpiredCodes() {
        codes.deleteExpiredBefore(Instant.now(clock));
    }
}
