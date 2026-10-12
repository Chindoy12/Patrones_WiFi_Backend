package com.wifisense.controller;

import com.wifisense.dto.CreateUserRequest;
import com.wifisense.dto.RoleUpdateRequest;
import com.wifisense.dto.UserResponse;
import com.wifisense.security.PasswordCipher;
import com.wifisense.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PasswordCipher cipher;

    public UserController(UserService userService, PasswordCipher cipher) {
        this.userService = userService;
        this.cipher = cipher;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        String password = cipher.reveal(request.password(), request.challengeId(), request.encryptedPassword());
        return userService.create(request.withPassword(password));
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request,
                                   Authentication authentication) {
        return userService.changeRole(id, request.role(), authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        userService.delete(id, authentication.getName());
    }
}
