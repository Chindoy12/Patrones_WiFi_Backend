package com.wifisense.service;

import com.wifisense.dto.CaptureCapabilitiesResponse;
import com.wifisense.dto.CaptureDetail;
import com.wifisense.dto.CaptureRequest;
import com.wifisense.dto.CaptureResponse;
import com.wifisense.model.AuditAction;
import com.wifisense.model.AuditEvent.Target;
import com.wifisense.model.AuditOutcome;
import com.wifisense.model.TrafficCapture;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.TrafficCaptureRepository;
import com.wifisense.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The administrators' side of the technical captures of a team network (the role is checked by SecurityConfig: only
 * ADMIN reaches here). Before a capture is created, every reason that would make it fail is checked and explained;
 * every request, refusal, view of results and cancellation leaves a trace in the audit.
 */
@Service
public class TrafficCaptureService {

    private final NetworkRepository networks;
    private final TrafficCaptureRepository captures;
    private final CaptureQueue queue;
    private final CaptureReadiness readiness;
    private final NetworkSensorService sensors;
    private final AgentCapabilitiesService capabilities;
    private final CaptureProperties properties;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public TrafficCaptureService(NetworkRepository networks, TrafficCaptureRepository captures, CaptureQueue queue,
                                 CaptureReadiness readiness, NetworkSensorService sensors,
                                 AgentCapabilitiesService capabilities, CaptureProperties properties,
                                 UserRepository users, AuditService audit, Clock clock) {
        this.networks = networks;
        this.captures = captures;
        this.queue = queue;
        this.readiness = readiness;
        this.sensors = sensors;
        this.capabilities = capabilities;
        this.properties = properties;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /** What the sensor can capture now, and why not when it cannot. */
    @Transactional
    public CaptureCapabilitiesResponse capabilities(Long networkId) {
        requireNetwork(networkId);
        return readiness.evaluate(sensors.status(networkId), capabilities.ofSensor(networkId),
                queue.active(networkId, Instant.now(clock)).isPresent());
    }

    /**
     * Asks the sensor for a capture (202: the page follows it). Refused with every reason when it would fail, and
     * with 400 when a limit is above what this server allows.
     */
    @Transactional
    public CaptureResponse request(String username, Long networkId, CaptureRequest request) {
        requireNetwork(networkId);
        int seconds = request.maxSeconds() == null ? readiness.limits().defaultSeconds() : request.maxSeconds();
        int packets = request.maxPackets() == null ? readiness.limits().defaultPackets() : request.maxPackets();
        String purpose = request.purpose().strip();
        Target target = new Target("NETWORK", networkId, networkId);
        String detail = seconds + " s, " + packets + " paquetes. Propósito: " + purpose;
        if (seconds > properties.secondsCap() || packets > properties.packetsCap()) {
            audit.record(AuditAction.CAPTURE_REQUESTED, AuditOutcome.FAILED, target, "Límites no permitidos. " + detail);
            throw new IllegalArgumentException("Este servidor permite capturas de hasta " + properties.secondsCap()
                    + " segundos y " + properties.packetsCap() + " paquetes.");
        }
        CaptureCapabilitiesResponse ready = capabilities(networkId);
        if (!ready.canCapture()) {
            audit.record(AuditAction.CAPTURE_REQUESTED, AuditOutcome.FAILED, target,
                    ready.blockers().getFirst() + " " + detail);
            throw new CaptureNotPossibleException(ready.blockers());
        }
        Long requestedBy = users.findByUsername(username).map(u -> u.getId()).orElse(null);
        TrafficCapture capture = queue.enqueue(networkId, requestedBy, purpose,
                new TrafficCapture.CaptureLimits(seconds, packets, properties.bytesCap()), Instant.now(clock));
        audit.record(AuditAction.CAPTURE_REQUESTED, AuditOutcome.SUCCESS,
                new Target("CAPTURE", capture.getId(), networkId), detail);
        return CaptureResponse.from(capture);
    }

    /** Captures of the network, newest first (default 20, at most 50). */
    @Transactional
    public List<CaptureResponse> history(Long networkId, int limit) {
        requireNetwork(networkId);
        queue.active(networkId, Instant.now(clock));  // a capture the sensor abandoned shows as failed
        return captures.findByNetworkIdOrderByRequestedAtDesc(networkId, PageRequest.of(0, Math.max(1, Math.min(limit, 50))))
                .stream().map(CaptureResponse::from).toList();
    }

    /** One capture with its statistics and findings; looking at the results is audited. */
    @Transactional
    public CaptureDetail detail(Long networkId, Long captureId) {
        TrafficCapture capture = own(networkId, captureId);
        queue.active(networkId, Instant.now(clock));
        if (capture.getStatus().name().equals("COMPLETED")) {
            audit.record(AuditAction.CAPTURE_VIEWED, AuditOutcome.SUCCESS,
                    new Target("CAPTURE", captureId, networkId), null);
        }
        return CaptureDetail.from(capture);
    }

    /** Stops a capture: a waiting one is cancelled now; a running one is stopped by the sensor on its next check. */
    @Transactional
    public CaptureResponse cancel(Long networkId, Long captureId) {
        TrafficCapture capture = own(networkId, captureId);
        capture.requestCancel(Instant.now(clock));
        captures.save(capture);
        audit.record(AuditAction.CAPTURE_CANCELLED, AuditOutcome.SUCCESS,
                new Target("CAPTURE", captureId, networkId), capture.getStatus().name());
        return CaptureResponse.from(capture);
    }

    private TrafficCapture own(Long networkId, Long captureId) {
        requireNetwork(networkId);
        return captures.findByIdAndNetworkId(captureId, networkId)
                .orElseThrow(() -> new ResourceNotFoundException("la captura", captureId));
    }

    private void requireNetwork(Long networkId) {
        if (!networks.existsById(networkId)) {
            throw new ResourceNotFoundException("la red", networkId);
        }
    }
}
