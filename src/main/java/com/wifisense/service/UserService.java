package com.wifisense.service;

import com.wifisense.dto.CreateUserRequest;
import com.wifisense.dto.UserResponse;
import com.wifisense.model.User;
import com.wifisense.repository.UserRepository;
import com.wifisense.security.AccountProperties;
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

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy policy,
                       AccountProperties accounts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.accounts = accounts;
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

    @Transactional
    public UserResponse register(String username, String password) {
        String name = username == null ? null : username.trim();
        validate(name, password);
        if (RESERVED.contains(name.toLowerCase())) {
            throw new DuplicateResourceException("Ese nombre de usuario no está disponible.");
        }
        return UserResponse.from(save(name, password, User.Role.USER));
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
