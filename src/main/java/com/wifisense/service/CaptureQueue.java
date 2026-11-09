package com.wifisense.service;

import com.wifisense.model.CaptureFailure;
import com.wifisense.model.CaptureStatus;
import com.wifisense.model.TrafficCapture;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.TrafficCaptureRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The captures waiting for the sensor of each team network. Shared by the administrators (who add captures) and by
 * the sensor (which picks them up): a network has at most one capture waiting or running, and a capture the sensor
 * does not pick up, or does not deliver in time, fails as AGENT_NO_RESPONSE instead of waiting forever.
 * Every method must run inside a transaction.
 */
@Component
public class CaptureQueue {

    static final Set<CaptureStatus> ACTIVE = EnumSet.of(CaptureStatus.PENDING, CaptureStatus.RUNNING);

    private final TrafficCaptureRepository captures;
    private final NetworkRepository networks;
    private final CaptureProperties properties;

    public CaptureQueue(TrafficCaptureRepository captures, NetworkRepository networks, CaptureProperties properties) {
        this.captures = captures;
        this.networks = networks;
        this.properties = properties;
    }

    /** The capture waiting or running on the network; one the sensor left too long fails first. */
    public Optional<TrafficCapture> active(Long networkId, Instant now) {
        Optional<TrafficCapture> active = captures.findFirstByNetworkIdAndStatusIn(networkId, ACTIVE);
        if (active.isPresent() && expired(active.get(), now)) {
            active.get().fail(now, CaptureFailure.AGENT_NO_RESPONSE, null);
            captures.saveAndFlush(active.get());
            return Optional.empty();
        }
        return active;
    }

    /**
     * Waiting: the sensor must pick it up within {@code startTimeout}. Running: it must deliver within the capture's
     * own duration plus {@code resultGrace} (the analysis on the sensor's computer takes a few seconds).
     */
    boolean expired(TrafficCapture capture, Instant now) {
        if (capture.getStatus() == CaptureStatus.PENDING) {
            return !capture.getRequestedAt().plus(properties.startTimeout()).isAfter(now);
        }
        if (capture.getStatus() == CaptureStatus.RUNNING) {
            Instant deadline = capture.getStartedAt().plusSeconds(capture.getMaxSeconds()).plus(properties.resultGrace());
            return !deadline.isAfter(now);
        }
        return false;
    }

    public TrafficCapture enqueue(Long networkId, Long requestedBy, String purpose, TrafficCapture.CaptureLimits limits,
                                  Instant now) {
        if (active(networkId, now).isPresent()) {
            throw new IllegalStateException("Ya hay una captura en curso en esta red.");
        }
        return captures.saveAndFlush(new TrafficCapture(networks.getReferenceById(networkId), requestedBy, purpose,
                limits, now));
    }

    /**
     * The capture waiting for the sensor, without taking it (the answer to a report only says that there is one:
     * the sensor then takes it with GET /api/agent/captures/next, which hands out its limits).
     */
    public Optional<TrafficCapture> waiting(Long networkId, Instant now) {
        return active(networkId, now).filter(capture -> capture.getStatus() == CaptureStatus.PENDING);
    }

    /** The capture the sensor must run now: the waiting one becomes RUNNING. Empty when there is nothing to do. */
    public Optional<TrafficCapture> take(Long networkId, Instant now) {
        Optional<TrafficCapture> active = active(networkId, now)
                .filter(capture -> capture.getStatus() == CaptureStatus.PENDING);
        active.ifPresent(capture -> {
            capture.start(now);
            captures.saveAndFlush(capture);
        });
        return active;
    }
}
