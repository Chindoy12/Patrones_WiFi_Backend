package com.wifisense.controller;

import com.wifisense.dto.AuthResponse;
import com.wifisense.dto.UserResponse;
import com.wifisense.model.User;
import com.wifisense.security.LoginBlockedException;
import com.wifisense.security.RegistrationBlockedException;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.AuthService;
import com.wifisense.service.InvalidAccountException;
import com.wifisense.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void wrongCredentialsGiveOneGenericSpanishMessage() throws Exception {
        when(authService.login(any(), any())).thenThrow(new BadCredentialsException("bad"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"analyst\",\"password\":\"Wrong#2026a\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Datos incorrectos."));
    }

    @Test
    void tooManyAttemptsAreRejected() throws Exception {
        when(authService.login(any(), any())).thenThrow(new LoginBlockedException());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"analyst\",\"password\":\"Wrong#2026a\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void malformedBodyIsRejectedWithoutDetails() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La solicitud no tiene un formato válido."));
    }

    @Test
    void oversizedBodyIsRejected() throws Exception {
        String huge = "{\"username\":\"" + "a".repeat(20_000) + "\",\"password\":\"x\"}";

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void anyoneCanRegisterAsEndUser() throws Exception {
        when(authService.register(any(), any())).thenReturn(new AuthResponse("token", Instant.parse("2026-10-11T00:00:00Z"),
                new UserResponse(9L, "ana", User.Role.USER)));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"Redes#2026a\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void registrationErrorsAreListedInSpanish() throws Exception {
        when(authService.register(any(), any())).thenThrow(new InvalidAccountException(
                List.of("Debe incluir al menos una letra mayúscula.")));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"redes#2026a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("Debe incluir al menos una letra mayúscula."));
    }

    @Test
    void tooManyRegistrationsAreRejected() throws Exception {
        when(authService.register(any(), any())).thenThrow(new RegistrationBlockedException());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"Redes#2026a\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
