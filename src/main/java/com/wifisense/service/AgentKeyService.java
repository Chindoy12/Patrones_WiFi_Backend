package com.wifisense.service;

import com.wifisense.dto.AgentKeyCreated;
import com.wifisense.dto.IdentifyModeResponse;
import com.wifisense.model.AgentKey;
import com.wifisense.model.HomeNetwork;
import com.wifisense.repository.AgentKeyRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Keys of the agent. A key is 256 random bits ("wsa_" + base64url); only its SHA-256 is stored, the same way a
 * password would be, except that a key this long does not need a slow hash: it cannot be guessed.
 */
@Service
public class AgentKeyService {

    static final String PREFIX = "wsa_";
    private static final Pattern FORMAT = Pattern.compile("^wsa_[A-Za-z0-9_-]{43}$");
    private static final int VISIBLE_CHARS = 8;

    private final AgentKeyRepository keys;
    private final HomeNetworkRepository networks;
    private final AgentReportThrottle throttle;
    private final java.time.Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AgentKeyService(AgentKeyRepository keys, HomeNetworkRepository networks, AgentReportThrottle throttle,
                           java.time.Clock clock) {
        this.keys = keys;
        this.networks = networks;
        this.throttle = throttle;
        this.clock = clock;
    }

    /** Creates the key of a network, replacing (and so revoking) the previous one. */
    @Transactional
    public AgentKeyCreated create(String username, Long networkId) {
        ownNetwork(username, networkId);
        return issue(networkId);
    }

    /**
     * New key for a network whose ownership was already checked (by the owner's session, or by a pairing code the
     * owner created). Must run inside a transaction.
     */
    AgentKeyCreated issue(Long networkId) {
        keys.findByNetworkId(networkId).ifPresent(old -> throttle.forget(old.getId()));
        keys.deleteByNetworkId(networkId);  // clears the persistence context, so the network is referenced again below
        HomeNetwork network = networks.getReferenceById(networkId);
        String key = generate(random);
        AgentKey saved = keys.saveAndFlush(new AgentKey(network, hash(key), visiblePart(key)));
        return new AgentKeyCreated(key, saved.getKeyPrefix(), saved.getCreatedAt());
    }

    @Transactional
    public void revoke(String username, Long networkId) {
        ownNetwork(username, networkId);
        keys.findByNetworkId(networkId).ifPresent(old -> throttle.forget(old.getId()));
        keys.deleteByNetworkId(networkId);
    }

    /**
     * "Which device is this?": the agent of the network reports every minute for the next ten minutes. Fails when the
     * network has no agent installed.
     */
    @Transactional
    public IdentifyModeResponse identify(String username, Long networkId) {
        ownNetwork(username, networkId);
        java.time.Instant until = java.time.Instant.now(clock).plus(IdentifyMode.LENGTH);
        if (keys.identifyUntil(networkId, until) == 0) {
            throw new IllegalStateException("Esta red aún no tiene el agente instalado.");
        }
        return new IdentifyModeResponse(until, IdentifyMode.REPORT_SECONDS);
    }

    /** The key of the agent that sent a report; malformed, unknown and revoked keys fail the same way. */
    @Transactional(readOnly = true)
    public AgentKey authenticate(String key) {
        if (!wellFormed(key)) {
            throw new InvalidAgentKeyException();
        }
        return keys.findByKeyHash(hash(key)).orElseThrow(InvalidAgentKeyException::new);
    }

    private HomeNetwork ownNetwork(String username, Long networkId) {
        return networks.findByIdAndOwner_Username(networkId, username)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
    }

    /** A new key: 256 random bits. Agents of Mi Wi-Fi and sensors of team networks use the same format. */
    static String generate(SecureRandom random) {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    static String visiblePart(String key) {
        return key.substring(0, VISIBLE_CHARS);
    }

    static boolean wellFormed(String key) {
        return key != null && FORMAT.matcher(key).matches();
    }

    static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
