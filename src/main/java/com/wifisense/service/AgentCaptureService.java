package com.wifisense.service;

import com.wifisense.dto.AgentCaptureResult;
import com.wifisense.dto.AgentCaptureState;
import com.wifisense.dto.AgentCaptureTask;
import com.wifisense.model.NetworkSensor;
import com.wifisense.model.TrafficCapture;
import com.wifisense.repository.TrafficCaptureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sensor's side of the captures. Only the sensor of a team network takes part: its key decides the network (the
 * request never says it), and the key of a Mi Wi-Fi agent gets "not found", as if captures did not exist. The server
 * only hands out the number and limits of a capture; the agent checks its own consent and tools.
 */
@Service
public class AgentCaptureService {

    private static final Logger log = LoggerFactory.getLogger(AgentCaptureService.class);

    private final NetworkSensorService sensors;
    private final AgentKeyService keys;
    private final TrafficCaptureRepository captures;
    private final CaptureQueue queue;
    private final CaptureRecorder recorder;
    private final CaptureProperties properties;
    private final Clock clock;
    private final Map<Long, Instant> lastPoll = new ConcurrentHashMap<>();

    public AgentCaptureService(NetworkSensorService sensors, AgentKeyService keys, TrafficCaptureRepository captures,
                               CaptureQueue queue, CaptureRecorder recorder, CaptureProperties properties,
                               Clock clock) {
        this.sensors = sensors;
        this.keys = keys;
        this.captures = captures;
        this.queue = queue;
        this.recorder = recorder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * With each accepted report of a sensor: the number of the capture waiting for it, if an administrator asked for
     * one. It is not taken here: the sensor takes it right away with {@link #next}, which hands out its limits (so a
     * sensor that cannot capture never leaves a capture marked as running).
     */
    @Transactional
    public Optional<Long> dueWithReport(String rawKey) {
        return sensors.authenticate(rawKey)
                .flatMap(sensor -> queue.waiting(sensor.getNetwork().getId(), Instant.now(clock)))
                .map(TrafficCapture::getId);
    }

    /** "Is there a capture for me?" between reports. */
    @Transactional
    public Optional<AgentCaptureTask> next(String rawKey) {
        NetworkSensor sensor = sensor(rawKey);
        Instant now = Instant.now(clock);
        lastPoll.compute(sensor.getId(), (id, previous) -> {
            if (previous != null && previous.plus(properties.pollMinInterval()).isAfter(now)) {
                throw new ScanLimitException("El sensor preguntó por capturas demasiado seguido.",
                        Duration.between(now, previous.plus(properties.pollMinInterval())).toSeconds() + 1);
            }
            return now;
        });
        return queue.take(sensor.getNetwork().getId(), now).map(AgentCaptureService::task);
    }

    /** Asked while capturing: whether an administrator asked to stop. A finished capture also means "stop". */
    @Transactional(readOnly = true)
    public AgentCaptureState state(String rawKey, Long captureId) {
        TrafficCapture capture = own(sensor(rawKey), captureId);
        return new AgentCaptureState(capture.getId(), capture.cancelRequested() || !capture.getStatus().active());
    }

    /**
     * The result of a capture of the sensor's own network that is still running. A capture of another network is
     * "not found" (the same answer as one that does not exist), and a finished or late one is refused.
     */
    @Transactional
    public TrafficCapture receive(String rawKey, AgentCaptureResult result) {
        NetworkSensor sensor = sensor(rawKey);
        Instant now = Instant.now(clock);
        TrafficCapture capture = own(sensor, result.captureId());
        if (!capture.getStatus().active()) {
            throw new IllegalStateException("Esa captura ya terminó.");
        }
        if (queue.expired(capture, now)) {
            throw new IllegalStateException("Esa captura ya terminó: el resultado llegó tarde.");
        }
        recorder.apply(capture, result, now);
        log.info("Capture {} of network {}: {} ({} packets)", capture.getId(), sensor.getNetwork().getId(),
                capture.getStatus(), capture.getPacketCount());
        return captures.save(capture);
    }

    private TrafficCapture own(NetworkSensor sensor, Long captureId) {
        return captures.findByIdAndNetworkId(captureId, sensor.getNetwork().getId())
                .orElseThrow(() -> new ResourceNotFoundException("la captura", captureId));
    }

    /** A sensor key. Malformed or unknown: 401; the key of a Mi Wi-Fi agent: 404 (it has no captures). */
    private NetworkSensor sensor(String rawKey) {
        Optional<NetworkSensor> sensor = sensors.authenticate(rawKey);
        if (sensor.isPresent()) {
            return sensor.get();
        }
        keys.authenticate(rawKey);  // throws InvalidAgentKeyException (401) for an unknown key
        throw new ResourceNotFoundException("Las capturas solo existen para el sensor de una red del equipo.");
    }

    private static AgentCaptureTask task(TrafficCapture capture) {
        return new AgentCaptureTask(capture.getId(), capture.getMaxSeconds(), capture.getMaxPackets(),
                capture.getMaxBytes());
    }
}
