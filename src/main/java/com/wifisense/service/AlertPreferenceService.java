package com.wifisense.service;

import com.wifisense.dto.AlertPreferenceResponse;
import com.wifisense.model.AlertPreference;
import com.wifisense.model.User;
import com.wifisense.repository.AlertPreferenceRepository;
import com.wifisense.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The end user's email-alert switches. A missing row means all on. */
@Service
public class AlertPreferenceService {

    private final UserRepository users;
    private final AlertPreferenceRepository preferences;

    public AlertPreferenceService(UserRepository users, AlertPreferenceRepository preferences) {
        this.users = users;
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public AlertPreferenceResponse get(String username) {
        User user = find(username);
        return preferences.findByUserId(user.getId())
                .map(p -> new AlertPreferenceResponse(p.isEmailNewDevice(), p.isEmailSecurity(), p.isEmailAnomaly()))
                .orElse(new AlertPreferenceResponse(true, true, true));
    }

    @Transactional
    public AlertPreferenceResponse update(String username, boolean newDevice, boolean security, boolean anomaly) {
        User user = find(username);
        AlertPreference prefs = preferences.findByUserId(user.getId()).orElseGet(() -> new AlertPreference(user.getId()));
        prefs.set(newDevice, security, anomaly);
        preferences.save(prefs);
        return new AlertPreferenceResponse(newDevice, security, anomaly);
    }

    private User find(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("el usuario", username));
    }
}
