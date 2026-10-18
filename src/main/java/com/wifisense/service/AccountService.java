package com.wifisense.service;

import com.wifisense.dto.AccountResponse;
import com.wifisense.dto.AuthResponse;
import com.wifisense.dto.ContactUpdateRequest;
import com.wifisense.dto.PasswordChangeRequest;
import com.wifisense.model.User;
import com.wifisense.repository.UserRepository;
import com.wifisense.security.ContactPolicy;
import com.wifisense.security.LoginAttemptService;
import com.wifisense.security.LoginBlockedException;
import com.wifisense.security.PasswordCipher;
import com.wifisense.security.PasswordPolicy;
import com.wifisense.security.SessionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The end user's own account: recovery email, phone and password. Every change asks for the current password, and
 * wrong attempts count towards the same lockout as the login.
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final ContactPolicy contacts;
    private final PasswordPolicy policy;
    private final PasswordEncoder encoder;
    private final PasswordCipher cipher;
    private final LoginAttemptService attempts;
    private final SessionService sessions;
    private final Clock clock;

    public AccountService(UserRepository users, ContactPolicy contacts, PasswordPolicy policy, PasswordEncoder encoder,
                          PasswordCipher cipher, LoginAttemptService attempts, SessionService sessions, Clock clock) {
        this.users = users;
        this.contacts = contacts;
        this.policy = policy;
        this.encoder = encoder;
        this.cipher = cipher;
        this.attempts = attempts;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountResponse get(String username) {
        return toResponse(find(username));
    }

    @Transactional
    public AccountResponse updateContact(String username, ContactUpdateRequest request, String clientAddress) {
        User user = find(username);
        confirmPassword(user, cipher.reveal(request.password(), request.challengeId(), request.encryptedPassword()),
                clientAddress);
        List<String> errors = new ArrayList<>();
        String email = contacts.email(request.email()).orElse(null);
        if (email == null) {
            errors.add(ContactPolicy.EMAIL_ERROR);
        }
        String phone = null;
        if (request.phone() != null && !request.phone().isBlank()) {
            phone = contacts.phone(request.phone()).orElse(null);
            if (phone == null) {
                errors.add(ContactPolicy.PHONE_ERROR);
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidAccountException(errors);
        }
        String newEmail = email;
        users.findByEmailIgnoreCase(newEmail).filter(other -> !other.getId().equals(user.getId())).ifPresent(other -> {
            throw new DuplicateResourceException("Ese correo ya está en otra cuenta.");
        });
        user.changeContact(newEmail, phone);
        return toResponse(user);
    }

    /** Changes the password, closes the other sessions and returns a new session for this one. */
    @Transactional
    public AuthResponse changePassword(String username, PasswordChangeRequest request, String clientAddress) {
        User user = find(username);
        String current = cipher.reveal(request.currentPassword(), request.currentChallengeId(),
                request.currentEncryptedPassword());
        confirmPassword(user, current, clientAddress);
        String next = cipher.reveal(request.newPassword(), request.newChallengeId(), request.newEncryptedPassword());
        List<String> errors = new ArrayList<>(policy.passwordViolations(next));
        if (errors.isEmpty() && next.equals(current)) {
            errors.add("La contraseña nueva debe ser distinta de la actual.");
        }
        if (!errors.isEmpty()) {
            throw new InvalidAccountException(errors);
        }
        Instant now = Instant.now(clock);
        user.changePassword(encoder.encode(next), now);
        sessions.endAll(user.getId());
        return sessions.start(user);
    }

    private void confirmPassword(User user, String password, String clientAddress) {
        if (attempts.isBlocked(user.getUsername(), clientAddress)) {
            throw new LoginBlockedException();
        }
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            attempts.recordFailure(user.getUsername(), clientAddress);
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        attempts.recordSuccess(user.getUsername(), clientAddress);
    }

    private User find(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("el usuario", username));
    }

    private static AccountResponse toResponse(User user) {
        return new AccountResponse(user.getUsername(), user.hasRealEmail() ? user.getEmail() : null, user.getPhone());
    }
}
