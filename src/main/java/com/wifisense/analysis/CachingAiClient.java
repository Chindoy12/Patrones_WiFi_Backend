package com.wifisense.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * <b>Caching proxy</b> of the AI service: the object every service gets when it asks for an {@link AiAnalysisClient}.
 * <p>
 * Problem: several answers of the AI are a pure function of what the backend sends (the rules that grade a
 * connection, audit the security of a network, choose a channel or read a scan), yet the same question was asked
 * again and again: every team member that opens the dashboard of a network has its latest measurement graded once
 * more (a new one arrives every 5 minutes), every report of an agent asks for the security audit and the channel of a
 * network that usually did not change since the previous report, and a completed scan never changes but
 * «Analizar con IA» sent it to the AI on every click. Each of those calls crosses the network to Railway and can take
 * seconds when the AI is cold.
 * <p>
 * Solution: this class has the same interface as the real client, so no service changes. It answers a question it
 * already asked with the answer it kept, and only asks the {@link CircuitBreakerAiClient} (the protection proxy, which
 * in turn uses the HTTP adapter) when it has no fresh answer. It keeps {@code qualityScore}, {@code securityAssess},
 * {@code channelRecommend} and {@code nearbyAnalyze}, keyed by the request itself (records compare by value), for
 * {@code ttl}: same data, same answer, because the AI computes them with fixed rules (no model, no clock, no language
 * model). The {@code ttl} bounds how long an answer can outlive a new version of those rules after the AI is
 * redeployed.
 * <p>
 * What always goes to the AI: {@code detectAnomalies} (new measurements every time, and its answer depends on the
 * model in use), {@code modelInfo} and {@code modelEvaluation} (they change when the model is retrained or the AI is
 * redeployed, and {@link com.wifisense.service.AiTrainingService} reads the model in use to restore it), {@code
 * trafficAnalyze} (once per capture, and the backend stores the result), {@code trainModel} (an order, not a
 * question) and {@code isHealthy} (the status page must show the truth). A failure is never kept: the next call asks
 * again.
 * <p>
 * Each kind of question keeps at most {@code maxEntries} answers and drops the least recently used. The answers are
 * Jackson trees that a caller could change, so the proxy keeps its own copy and hands out copies. Only the request
 * decides the answer and nothing about the person travels in it, so two people that send the same data get the answer
 * each of them would get from the AI anyway.
 */
@Component
@Primary
public class CachingAiClient implements AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(CachingAiClient.class);

    private final AiAnalysisClient target;
    private final Duration ttl;
    private final Clock clock;

    private final Answers<AiQualityRequest> quality;
    private final Answers<AiSecurityRequest> security;
    private final Answers<AiChannelRequest> channel;
    private final Answers<AiNearbyRequest> nearby;

    private long hits;
    private long misses;

    @Autowired
    public CachingAiClient(CircuitBreakerAiClient target, @Value("${wifisense.ai.cache.ttl:10m}") Duration ttl,
                           @Value("${wifisense.ai.cache.max-entries:500}") int maxEntries, Clock clock) {
        this((AiAnalysisClient) target, ttl, maxEntries, clock);
    }

    CachingAiClient(AiAnalysisClient target, Duration ttl, int maxEntries, Clock clock) {
        if (ttl.isNegative() || ttl.isZero() || maxEntries < 1) {
            throw new IllegalArgumentException("La caché de la IA necesita un tiempo positivo y al menos 1 respuesta");
        }
        this.target = target;
        this.ttl = ttl;
        this.clock = clock;
        this.quality = new Answers<>(maxEntries);
        this.security = new Answers<>(maxEntries);
        this.channel = new Answers<>(maxEntries);
        this.nearby = new Answers<>(maxEntries);
    }

    @Override
    public AiAnalysisResponse detectAnomalies(AiAnalysisRequest request) {
        return target.detectAnomalies(request);
    }

    @Override
    public JsonNode modelInfo() {
        return target.modelInfo();
    }

    @Override
    public Optional<JsonNode> modelEvaluation() {
        return target.modelEvaluation();
    }

    @Override
    public JsonNode qualityScore(AiQualityRequest request) {
        return cached(quality, request, () -> target.qualityScore(request));
    }

    @Override
    public JsonNode securityAssess(AiSecurityRequest request) {
        return cached(security, request, () -> target.securityAssess(request));
    }

    @Override
    public JsonNode channelRecommend(AiChannelRequest request) {
        return cached(channel, request, () -> target.channelRecommend(request));
    }

    @Override
    public JsonNode trafficAnalyze(AiTrafficRequest request) {
        return target.trafficAnalyze(request);
    }

    @Override
    public JsonNode nearbyAnalyze(AiNearbyRequest request) {
        return cached(nearby, request, () -> target.nearbyAnalyze(request));
    }

    @Override
    public JsonNode trainModel(JsonNode request) {
        return target.trainModel(request);
    }

    @Override
    public boolean isHealthy() {
        return target.isHealthy();
    }

    /** Answers given from memory and answers asked to the AI since the application started. */
    public synchronized CacheStats stats() {
        return new CacheStats(hits, misses);
    }

    public record CacheStats(long hits, long misses) {
    }

    private <K> JsonNode cached(Answers<K> answers, K key, Supplier<JsonNode> ask) {
        JsonNode kept = fresh(answers, key);
        if (kept != null) {
            return kept.deepCopy();
        }
        JsonNode answer = ask.get();  // a failure propagates and nothing is kept
        keep(answers, key, answer);
        return answer.deepCopy();
    }

    private synchronized <K> JsonNode fresh(Answers<K> answers, K key) {
        Answer kept = answers.get(key);
        if (kept != null && clock.instant().isBefore(kept.until())) {
            hits++;
            return kept.value();
        }
        if (kept != null) {
            answers.remove(key);
        }
        misses++;
        return null;
    }

    private synchronized <K> void keep(Answers<K> answers, K key, JsonNode value) {
        if (value == null) {
            return;
        }
        answers.put(key, new Answer(value.deepCopy(), clock.instant().plus(ttl)));
        if (log.isDebugEnabled()) {
            log.debug("AI answer kept for {} s ({} hits, {} asked)", ttl.toSeconds(), hits, misses);
        }
    }

    private record Answer(JsonNode value, Instant until) {
    }

    /** Least recently used answers first out once the map is full (LinkedHashMap in access order). */
    private static final class Answers<K> extends LinkedHashMap<K, Answer> {
        private final int maxEntries;

        Answers(int maxEntries) {
            super(16, 0.75f, true);
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, Answer> eldest) {
            return size() > maxEntries;
        }
    }
}
