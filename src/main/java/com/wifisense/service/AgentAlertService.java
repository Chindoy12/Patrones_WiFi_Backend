package com.wifisense.service;

import com.wifisense.mail.AlertEmail;
import com.wifisense.mail.MailDispatcher;
import com.wifisense.model.AgentAlert;
import com.wifisense.model.AlertPreference;
import com.wifisense.model.AlertType;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.User;
import com.wifisense.repository.AgentAlertRepository;
import com.wifisense.repository.AlertPreferenceRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Raises the alerts of a home network and emails them when the user wants them. Each alert is recorded (a short
 * history in Mi Wi-Fi); the email is sent only if the owner has a real email, left the alert on, and no email of that
 * type went out recently, so the agent reporting every 5 minutes does not flood the inbox. Separate from the team's
 * AlertService, which handles the monitored-network alerts of the staff dashboard.
 */
@Service
public class AgentAlertService {

    /** How long before the same kind of alert may be emailed again. */
    static final Map<AlertType, Duration> QUIET = Map.of(
            AlertType.NEW_DEVICE, Duration.ZERO,          // a new device is a distinct event each time
            AlertType.UNAUTHORIZED_DEVICE, Duration.ofHours(12),
            AlertType.SECURITY, Duration.ofHours(12),
            AlertType.ANOMALY, Duration.ofHours(6));

    private final HomeNetworkRepository networks;
    private final AgentAlertRepository alerts;
    private final AlertPreferenceRepository preferences;
    private final MailDispatcher mail;
    private final Clock clock;

    public AgentAlertService(HomeNetworkRepository networks, AgentAlertRepository alerts,
                             AlertPreferenceRepository preferences, MailDispatcher mail, Clock clock) {
        this.networks = networks;
        this.alerts = alerts;
        this.preferences = preferences;
        this.mail = mail;
        this.clock = clock;
    }

    public record Signal(AlertType type, String detail) {
    }

    /** Records the signals of this report and emails the ones that are due. */
    @Transactional
    public void process(Long networkId, List<Signal> signals) {
        if (signals.isEmpty()) {
            return;
        }
        HomeNetwork network = networks.findById(networkId).orElse(null);
        if (network == null) {
            return;
        }
        User owner = network.getOwner();
        AlertPreference prefs = preferences.findByUserId(owner.getId()).orElse(null);
        Instant now = Instant.now(clock);
        for (Signal signal : signals) {
            boolean email = shouldEmail(owner, prefs, networkId, signal.type(), now);
            alerts.save(new AgentAlert(network, signal.type(), signal.detail(), email));
            if (email) {
                mail.sendLater(AlertEmail.build(owner.getEmail(), owner.getUsername(), network.getName(),
                        signal.type(), signal.detail()));
            }
        }
    }

    private boolean shouldEmail(User owner, AlertPreference prefs, Long networkId, AlertType type, Instant now) {
        if (!owner.hasRealEmail() || (prefs != null && !prefs.wants(type))) {
            return false;
        }
        Duration quiet = QUIET.getOrDefault(type, Duration.ofHours(12));
        return quiet.isZero()
                || !alerts.existsByNetworkIdAndTypeAndEmailedTrueAndCreatedAtAfter(networkId, type, now.minus(quiet));
    }
}
