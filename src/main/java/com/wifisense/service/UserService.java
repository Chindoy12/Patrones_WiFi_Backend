package com.wifisense.service;

import com.wifisense.dto.CreateUserRequest;
import com.wifisense.dto.UserResponse;
import com.wifisense.model.User;
import com.wifisense.repository.UserRepository;
import com.wifisense.security.AccountProperties;
import com.wifisense.security.ContactPolicy;
import com.wifisense.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Team accounts (ADMIN, ANALYST, VIEWER) are created by an administrator, up to a fixed maximum.
 * End users register themselves and always get the USER role.
 */
@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy policy;
    private final AccountProperties accounts;

    private final ContactPolicy contacts;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy policy,
                       AccountProperties accounts, ContactPolicy contacts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.accounts = accounts;
        this.contacts = contacts;
    }

    private static final Set<String> RESERVED = Set.of("admin", "administrador", "root", "soporte", "sistema", "wifisense");

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (!request.role().isStaff()) {
            throw new IllegalArgumentException("Las cuentas de usuario final se crean desde el registro.");
        }
        validate(request.username(), request.password());
        if (users.countByRoleIn(User.Role.STAFF) >= accounts.maxUsers()) {
            throw new UserLimitException(accounts.maxUsers());
        }
        return UserResponse.from(save(request.username(), request.password(), request.role()));
    }

    /**
     * Creates an end-user account. The recovery email is the username when it is an email; otherwise it must be
     * given. The phone is optional. Every problem is reported at once, in Spanish.
     */
    @Transactional
    public UserResponse register(String username, String password, String email, String phone) {
        String name = username == null ? null : username.trim();
        List<String> errors = new ArrayList<>(policy.usernameViolations(name));
        errors.addAll(policy.passwordViolations(password));
        boolean emailGiven = email != null && !email.isBlank();
        String recoveryEmail = null;
        if (emailGiven || (name != null && name.contains("@"))) {
            recoveryEmail = contacts.email(emailGiven ? email : name).orElse(null);
            if (recoveryEmail == null) {
                errors.add(ContactPolicy.EMAIL_ERROR);
            }
        } else {
            errors.add("Escribe tu correo: lo usamos solo para que puedas recuperar tu contraseña.");
        }
        String normalizedPhone = null;
        if (phone != null && !phone.isBlank()) {
            normalizedPhone = contacts.phone(phone).orElse(null);
            if (normalizedPhone == null) {
                errors.add(ContactPolicy.PHONE_ERROR);
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidAccountException(errors);
        }
        if (isReserved(name) || users.existsByUsernameIgnoreCase(name)) {
            throw new DuplicateResourceException("Ese nombre de usuario no está disponible.");
        }
        if (users.findByEmailIgnoreCase(recoveryEmail).isPresent()) {
            throw new DuplicateResourceException("Ese correo ya tiene una cuenta. Si es tuyo, recupera la contraseña.");
        }
        User user = new User(name, recoveryEmail, passwordEncoder.encode(password), User.Role.USER);
        user.changeContact(recoveryEmail, normalizedPhone);
        return UserResponse.from(users.save(user));
    }

    /** Generic staff names and the administrator's own name (ADMIN_USERNAME), in any case, are not for end users. */
    private boolean isReserved(String name) {
        String admin = accounts.adminUsername();
        return RESERVED.contains(name.toLowerCase(java.util.Locale.ROOT))
                || (admin != null && name.equalsIgnoreCase(admin.trim()));
    }

    private void validate(String username, String password) {
        List<String> errors = new ArrayList<>(policy.usernameViolations(username));
        errors.addAll(policy.passwordViolations(password));
        if (!errors.isEmpty()) {
            throw new InvalidAccountException(errors);
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("Ese nombre de usuario no está disponible.");
        }
    }

    private User save(String username, String password, User.Role role) {
        return users.save(new User(username, PasswordPolicy.emailFor(username), passwordEncoder.encode(password), role));
    }

    @Transactional(readOnly = true)
    public UserResponse findByUsername(String username) {
        return users.findByUsername(username).map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("el usuario", username));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findByRoleInOrderByUsername(User.Role.STAFF).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse changeRole(Long id, User.Role role, String currentUsername) {
        if (!role.isStaff()) {
            throw new IllegalArgumentException("Solo se pueden asignar roles del equipo.");
        }
        User user = findStaff(id);
        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalStateException("No puedes cambiar tu propio rol.");
        }
        user.changeRole(role);
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(Long id, String currentUsername) {
        User user = findStaff(id);
        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalStateException("No puedes eliminar tu propia cuenta.");
        }
        users.delete(user);
    }

    private User findStaff(Long id) {
        return users.findById(id).filter(user -> user.getRole().isStaff())
                .orElseThrow(() -> new ResourceNotFoundException("el usuario", id));
    }
}
