package com.wifisense.service;

import com.wifisense.dto.AgentKeyCreated;
import com.wifisense.dto.AgentPairResponse;
import com.wifisense.dto.AgentPairingCodeResponse;
import com.wifisense.model.AgentPairingCode;
import com.wifisense.model.HomeNetwork;
import com.wifisense.repository.AgentPairingCodeRepository;
import com.wifisense.repository.HomeNetworkRepository;
import com.wifisense.security.RecoveryBlockedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Installs the agent without copying the long key: Mi Wi-Fi shows an 8-character code (15 minutes, one use) and the
 * installer exchanges it for the key. The alphabet leaves out 0/O and 1/I/L so the code is easy to read and type.
 * 32^8 ≈ 10^12 codes and a limit of attempts per address make guessing a code impractical.
 */
@Service
public class AgentPairingService {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;
    static final Duration TTL = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 10;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);

    private final AgentPairingCodeRepository codes;
    private final HomeNetworkRepository networks;
    private final AgentKeyService keys;
    private final NetworkSensorService sensors;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public AgentPairingService(AgentPairingCodeRepository codes, HomeNetworkRepository networks, AgentKeyService keys,
                               NetworkSensorService sensors, Clock clock) {
        this.codes = codes;
        this.networks = networks;
        this.keys = keys;
        this.sensors = sensors;
        this.clock = clock;
    }

    /** A new code for the owner's network; a previous pending code stops working. */
    @Transactional
    public AgentPairingCodeResponse create(String username, Long networkId) {
        HomeNetwork network = networks.findByIdAndOwner_Username(networkId, username)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        codes.deleteByNetworkId(networkId);
        String code = newCode(random);
        Instant now = Instant.now(clock);
        codes.save(new AgentPairingCode(network, hash(code), now, now.plus(TTL)));
        return new AgentPairingCodeResponse(display(code), now.plus(TTL));
    }

    /** Exchanges a valid code for a new agent key of its network; the code is spent either way it is found. */
    @Transactional
    public AgentPairResponse pair(String rawCode, String clientAddress) {
        countAttempt(clientAddress);
        String code = normalize(rawCode);
        if (code == null) {
            throw new InvalidPairingCodeException();
        }
        Optional<AgentPairingCode> found = codes.findByCodeHash(hash(code));
        if (found.isEmpty()) {
            // The same installer serves the sensors of the team networks: their codes come from the team panel.
            return sensors.pair(code).orElseThrow(InvalidPairingCodeException::new);
        }
        AgentPairingCode pairing = found.get();
        HomeNetwork network = pairing.getNetwork();
        Long networkId = network.getId();
        String networkName = network.getName();
        codes.delete(pairing);
        if (!pairing.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new InvalidPairingCodeException();
        }
        AgentKeyCreated key = keys.issue(networkId);
        return new AgentPairResponse(key.key(), key.prefix(), networkName);
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT7M")
    @Transactional
    public void purgeExpired() {
        codes.deleteExpiredBefore(Instant.now(clock));
    }

    static String newCode(SecureRandom random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** "K7QM-4RZD": easier to read aloud and to type. */
    static String display(String code) {
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    /** "k7qm 4rzd", "K7QM-4RZD" and "K7QM4RZD" are the same code; anything else is not a code. */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != LENGTH) {
            return null;
        }
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return null;
            }
        }
        return code;
    }

    private void countAttempt(String address) {
        Instant now = Instant.now(clock);
        Deque<Instant> recent = attempts.computeIfAbsent(address, k -> new ConcurrentLinkedDeque<>());
        synchronized (recent) {
            recent.removeIf(at -> at.isBefore(now.minus(ATTEMPT_WINDOW)));
            if (recent.size() >= MAX_ATTEMPTS) {
                throw new RecoveryBlockedException();
            }
            recent.add(now);
        }
    }

    static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
