package com.wifisense.service;

import com.wifisense.dto.RegisterRequest;
import com.wifisense.dto.UserResponse;
import com.wifisense.model.User;
import com.wifisense.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** Self-registration always creates a VIEWER; only an ADMIN can grant higher roles. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already in use");
        }
        if (users.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already in use");
        }
        User user = new User(request.username(), request.email(), passwordEncoder.encode(request.password()),
                User.Role.VIEWER);
        return UserResponse.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findByUsername(String username) {
        return users.findByUsername(username).map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse changeRole(Long id, User.Role role) {
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.changeRole(role);
        return UserResponse.from(user);
    }
}
