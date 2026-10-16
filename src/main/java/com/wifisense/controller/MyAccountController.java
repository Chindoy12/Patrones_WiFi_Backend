package com.wifisense.controller;

import com.wifisense.dto.AccountResponse;
import com.wifisense.dto.AuthResponse;
import com.wifisense.dto.ContactUpdateRequest;
import com.wifisense.dto.PasswordChangeRequest;
import com.wifisense.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/** The end user's own account; the owner always comes from the token. */
@RestController
@RequestMapping("/api/my/account")
public class MyAccountController {

    private final AccountService accounts;

    public MyAccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public AccountResponse get(Authentication authentication) {
        return accounts.get(authentication.getName());
    }

    @PutMapping("/contact")
    public AccountResponse updateContact(@RequestBody ContactUpdateRequest request, Authentication authentication,
                                         HttpServletRequest http) {
        return accounts.updateContact(authentication.getName(), request, clientAddress(http));
    }

    /** Answers with a new session: the old ones, this one included, stop working after the change. */
    @PutMapping("/password")
    public AuthResponse changePassword(@RequestBody PasswordChangeRequest request, Authentication authentication,
                                       HttpServletRequest http) {
        return accounts.changePassword(authentication.getName(), request, clientAddress(http));
    }

    private static String clientAddress(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
    }
}
