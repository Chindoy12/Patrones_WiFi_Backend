package com.wifisense.security;

import com.wifisense.model.User;
import com.wifisense.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ensures the administrator account defined by ADMIN_USERNAME / ADMIN_PASSWORD exists at startup.
 * <ul>
 *   <li>The password is stored only as a BCrypt hash and is re-applied if the variable changes; that closes every
 *       session the administrator had open, as any other password change.</li>
 *   <li>The name is matched regardless of case. If it belongs to an end user's account, that account is never turned
 *       into an administrator: the startup only logs the problem.</li>
 * </ul>
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AccountProperties properties;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final SessionService sessions;
    private final Clock clock;

    public AdminAccountInitializer(AccountProperties properties, UserRepository users, PasswordEncoder encoder,
                                   PasswordPolicy policy, SessionService sessions, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = properties.adminUsername();
        String password = properties.adminPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("ADMIN_USERNAME / ADMIN_PASSWORD no están configurados: no se creó la cuenta de administrador");
            return;
        }
        List<String> violations = new ArrayList<>(policy.usernameViolations(username));
        violations.addAll(policy.passwordViolations(password));
        if (!violations.isEmpty()) {
            throw new IllegalStateException("La cuenta de administrador no cumple la política: " + String.join(" ", violations));
        }

        Optional<User> existing = users.findByUsernameIgnoreCase(username);
        if (existing.isPresent() && !existing.get().getRole().isStaff()) {
            log.error("ADMIN_USERNAME '{}' ya es el nombre de una cuenta de usuario final: no se le dio el rol de "
                    + "administrador. Elige otro ADMIN_USERNAME.", username);
            return;
        }
        User admin = existing.orElseGet(() -> new User(username, PasswordPolicy.emailFor(username),
                encoder.encode(password), User.Role.ADMIN));
        boolean passwordChanged = admin.getId() != null && !encoder.matches(password, admin.getPasswordHash());
        if (passwordChanged) {
            admin.changePassword(encoder.encode(password), Instant.now(clock));
        }
        admin.changeRole(User.Role.ADMIN);
        users.save(admin);
        if (passwordChanged) {
            sessions.endAll(admin.getId());
        }
        log.info("Cuenta de administrador '{}' lista", admin.getUsername());
    }
}
