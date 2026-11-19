package com.wifisense.controller;

import com.wifisense.security.PasswordCipher;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.UserService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Avance 33: the team has two roles, administrator and analyst; the read-only role cannot be given any more. */
@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService users;
    @MockitoBean
    private PasswordCipher cipher;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject("jaider")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void theRemovedViewerRoleCannotBeGivenToAnAccount() throws Exception {
        mvc.perform(patch("/api/users/5/role").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La solicitud no tiene un formato válido."));
        verify(users, never()).changeRole(any(), any(), any());
    }

    @Test
    void norCanAnAccountBeCreatedWithIt() throws Exception {
        mvc.perform(post("/api/users").with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"lector\",\"password\":\"Redes#2026a\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test
    void onlyTheAdministratorManagesTheTeamAccounts() throws Exception {
        mvc.perform(get("/api/users").with(as("ANALYST"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(as("ADMIN"))).andExpect(status().isOk());
    }
}
