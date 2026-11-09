package com.wifisense.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * <b>Proxy</b> of the AI service that applies the <b>Circuit Breaker</b> pattern.
 * <p>
 * Problem: when the AI service does not answer (Railway redeploys it, or it is starting), every call waits for the
 * whole timeout (10 s). A sensor report asks the AI up to four times, so a report could take 40 s and the agent (which
 * waits 30 s) would give up, while the backend keeps one thread busy per report.
 * <p>
 * Solution: this class has the same interface as the real client ({@link HttpAiAnalysisClient}), so nobody that uses
 * {@link AiAnalysisClient} changes. It counts the calls in a row that fail because the service is unreachable. After
 * {@code failureThreshold} of them the circuit <i>opens</i>: for {@code openFor} every call fails at once with the
 * same {@link AiServiceUnavailableException} the callers already handle (the report is saved without verdicts, the
 * analysis answers 503). Then one call is let through as a test (<i>half open</i>): if it works the circuit
 * <i>closes</i> again; if not, it stays open for another period.
 * <p>
 * A 4xx answer means the service is up and refused the request (for example a validation error): it does not count
 * as a failure of availability.
 */
@Component
@Primary
public class CircuitBreakerAiClient implements AiAnalysisClient {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerAiClient.class);

    private final AiAnalysisClient target;
    private final int failureThreshold;
    private final Duration openFor;
    private final Clock clock;

    private State state = State.CLOSED;
    private int failuresInARow;
    private Instant openedAt;
    private boolean probeInFlight;

    @Autowired
    public CircuitBreakerAiClient(HttpAiAnalysisClient target,
                                  @Value("${wifisense.ai.circuit.failures:2}") int failureThreshold,
                                  @Value("${wifisense.ai.circuit.open-for:30s}") Duration openFor, Clock clock) {
        this((AiAnalysisClient) target, failureThreshold, openFor, clock);
    }

    CircuitBreakerAiClient(AiAnalysisClient target, int failureThreshold, Duration openFor, Clock clock) {
        if (failureThreshold < 1 || openFor.isNegative() || openFor.isZero()) {
            throw new IllegalArgumentException("El cortacircuitos necesita al menos 1 falla y un tiempo abierto positivo");
        }
        this.target = target;
        this.failureThreshold = failureThreshold;
        this.openFor = openFor;
        this.clock = clock;
    }

    @Override
    public AiAnalysisResponse detectAnomalies(AiAnalysisRequest request) {
        return call(() -> target.detectAnomalies(request));
    }

    @Override
    public JsonNode modelInfo() {
        return call(target::modelInfo);
    }

    @Override
    public Optional<JsonNode> modelEvaluation() {
        return call(target::modelEvaluation);
    }

    @Override
    public JsonNode qualityScore(AiQualityRequest request) {
        return call(() -> target.qualityScore(request));
    }

    @Override
    public JsonNode securityAssess(AiSecurityRequest request) {
        return call(() -> target.securityAssess(request));
    }

    @Override
    public JsonNode channelRecommend(AiChannelRequest request) {
        return call(() -> target.channelRecommend(request));
    }

    @Override
    public JsonNode trafficAnalyze(AiTrafficRequest request) {
        return call(() -> target.trafficAnalyze(request));
    }

    @Override
    public JsonNode nearbyAnalyze(AiNearbyRequest request) {
        return call(() -> target.nearbyAnalyze(request));
    }

    @Override
    public JsonNode trainModel(JsonNode request) {
        return call(() -> target.trainModel(request));
    }

    /** The health check always goes to the service: it is how the status page shows the truth, and it never throws. */
    @Override
    public boolean isHealthy() {
        return target.isHealthy();
    }

    public synchronized State state() {
        return state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openFor)) ? State.HALF_OPEN : state;
    }

    private <T> T call(Supplier<T> action) {
        boolean probe = permit();
        try {
            T answer = action.get();
            succeeded(probe);
            return answer;
        } catch (AiServiceUnavailableException e) {
            if (e.getCause() instanceof HttpClientErrorException) {
                succeeded(probe);  // the service answered: it is available, the request was wrong
            } else {
                failed(probe);
            }
            throw e;
        } catch (RuntimeException e) {
            failed(probe);
            throw e;
        }
    }

    /** Whether this call may reach the service; true when it is the test call of a half-open circuit. */
    private synchronized boolean permit() {
        if (state == State.CLOSED) {
            return false;
        }
        Instant now = clock.instant();
        if (state == State.OPEN && !now.isBefore(openedAt.plus(openFor))) {
            state = State.HALF_OPEN;
        }
        if (state == State.HALF_OPEN && !probeInFlight) {
            probeInFlight = true;
            return true;
        }
        long seconds = Math.max(1, Duration.between(now, openedAt.plus(openFor)).toSeconds());
        throw new AiServiceUnavailableException("El servicio de IA no respondió en los últimos intentos; se volverá a "
                + "probar en " + (state == State.HALF_OPEN ? "unos segundos" : seconds + " s") + ".");
    }

    private synchronized void succeeded(boolean probe) {
        if (state != State.CLOSED) {
            log.info("AI service answers again: circuit closed");
        }
        state = State.CLOSED;
        failuresInARow = 0;
        if (probe) {
            probeInFlight = false;
        }
    }

    private synchronized void failed(boolean probe) {
        if (probe) {
            probeInFlight = false;
            open("the test call failed");
            return;
        }
        failuresInARow++;
        if (state == State.CLOSED && failuresInARow >= failureThreshold) {
            open(failuresInARow + " calls in a row failed");
        }
    }

    private void open(String why) {
        state = State.OPEN;
        openedAt = clock.instant();
        log.warn("AI service unavailable ({}): circuit open, calls fail at once for {} s", why, openFor.toSeconds());
    }
}
