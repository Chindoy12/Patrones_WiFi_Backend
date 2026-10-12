package com.wifisense.controller;

import com.wifisense.dto.AuthResponse;
import com.wifisense.dto.LoginRequest;
import com.wifisense.dto.RegisterRequest;
import com.wifisense.dto.UserResponse;
import com.wifisense.security.PasswordCipher;
import com.wifisense.service.AuthService;
import com.wifisense.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PasswordCipher cipher;

    public AuthController(AuthService authService, UserService userService, PasswordCipher cipher) {
        this.authService = authService;
        this.userService = userService;
        this.cipher = cipher;
    }

    /** One-time public key the browser uses to seal the password before sending it. */
    @GetMapping("/challenge")
    public PasswordCipher.Challenge challenge() {
        return cipher.issue();
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, clientAddress(http));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest request, HttpServletRequest http) {
        return authService.register(request, clientAddress(http));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }

    /** Behind Railway's proxy the caller's address is the first entry of X-Forwarded-For. */
    private static String clientAddress(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
    }
}
