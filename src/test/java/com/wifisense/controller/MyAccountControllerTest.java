package com.wifisense.controller;

import com.wifisense.dto.AccountResponse;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MyAccountController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class MyAccountControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accounts;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void theOwnerComesFromTheSession() throws Exception {
        when(accounts.get("ana")).thenReturn(new AccountResponse("ana", "ana@gmail.com", null));

        mvc.perform(get("/api/my/account").with(as("ana", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@gmail.com"));
    }

    @Test
    void updatesTheContactOfTheSignedInUser() throws Exception {
        when(accounts.updateContact(eq("ana"), any(), any())).thenReturn(new AccountResponse("ana", "a@b.co", "+573001234567"));

        mvc.perform(put("/api/my/account/contact").with(as("ana", "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"phone\":\"3001234567\",\"challengeId\":\"c\",\"encryptedPassword\":\"e\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+573001234567"));
    }

    @Test
    void withoutASessionThereIsNoAccount() throws Exception {
        mvc.perform(get("/api/my/account")).andExpect(status().isUnauthorized());
        verifyNoInteractions(accounts);
    }
}
