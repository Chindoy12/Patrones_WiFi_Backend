package com.wifisense.service;

import com.wifisense.dto.AuthResponse;
import com.wifisense.dto.LoginRequest;
import com.wifisense.dto.RegisterRequest;
import com.wifisense.security.InvalidEncryptedPasswordException;
import com.wifisense.security.LoginAttemptService;
import com.wifisense.security.LoginBlockedException;
import com.wifisense.security.PasswordCipher;
import com.wifisense.security.PasswordPolicy;
import com.wifisense.security.RegistrationThrottle;
import com.wifisense.security.TokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserService userService;
    private final LoginAttemptService attempts;
    private final PasswordPolicy policy;
    private final RegistrationThrottle registrations;
    private final PasswordCipher cipher;

    public AuthService(AuthenticationManager authenticationManager, TokenService tokenService,
                       UserService userService, LoginAttemptService attempts, PasswordPolicy policy,
                       RegistrationThrottle registrations, PasswordCipher cipher) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userService = userService;
        this.attempts = attempts;
        this.policy = policy;
        this.registrations = registrations;
        this.cipher = cipher;
    }

    /** Creates an end-user account (role USER) and signs it in right away. */
    public AuthResponse register(RegisterRequest request, String clientAddress) {
        registrations.check(clientAddress);
        String password = cipher.reveal(request.password(), request.challengeId(), request.encryptedPassword());
        userService.register(request.username(), password);
        registrations.record(clientAddress);
        return authenticate(request.username().trim(), password, clientAddress);
    }

    /** Every failure looks the same to the client, so the response never reveals which field was wrong. */
    public AuthResponse login(LoginRequest request, String clientAddress) {
        String username = request.username() == null ? "" : request.username().trim();
        if (attempts.isBlocked(username, clientAddress)) {
            throw new LoginBlockedException();
        }
        String password;
        try {
            password = cipher.reveal(request.password(), request.challengeId(), request.encryptedPassword());
        } catch (InvalidEncryptedPasswordException e) {
            attempts.recordFailure(username, clientAddress);
            throw new BadCredentialsException("Datos incorrectos");
        }
        return authenticate(username, password, clientAddress);
    }

    private AuthResponse authenticate(String username, String password, String clientAddress) {
        if (!policy.isPlausibleLogin(username, password)) {
            attempts.recordFailure(username, clientAddress);
            throw new BadCredentialsException("Datos incorrectos");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException e) {
            attempts.recordFailure(username, clientAddress);
            throw e;
        }
        attempts.recordSuccess(username, clientAddress);
        TokenService.IssuedToken token = tokenService.issue(authentication);
        return new AuthResponse(token.value(), token.expiresAt(), userService.findByUsername(authentication.getName()));
    }
}
