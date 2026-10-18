package com.wifisense.controller;

import com.wifisense.dto.AuthResponse;
import com.wifisense.dto.UserResponse;
import com.wifisense.model.User;
import com.wifisense.security.LoginBlockedException;
import com.wifisense.security.PasswordCipher;
import com.wifisense.security.RecoveryBlockedException;
import com.wifisense.security.RegistrationBlockedException;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.security.InvalidSessionException;
import com.wifisense.security.SessionService;
import com.wifisense.service.AuthService;
import com.wifisense.service.InvalidAccountException;
import com.wifisense.service.InvalidResetLinkException;
import com.wifisense.service.PasswordResetService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private PasswordResetService resets;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private PasswordCipher passwordCipher;
    @MockitoBean
    private SessionService sessions;

    @Test
    void anyoneCanGetAOneTimePasswordChallenge() throws Exception {
        when(passwordCipher.issue()).thenReturn(new PasswordCipher.Challenge("c1", "PUBLIC-KEY",
                Instant.parse("2026-10-11T00:02:00Z")));

        mvc.perform(get("/api/auth/challenge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value("c1"))
                .andExpect(jsonPath("$.publicKey").value("PUBLIC-KEY"));
    }

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

    @Test
    void recoveryAlwaysAnswersTheSameWithoutASession() throws Exception {
        mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"quien@sea.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Si ese correo tiene una cuenta")));
        verify(resets).requestLink(eq("quien@sea.com"), any());
    }

    @Test
    void anExpiredLinkGetsASpanishExplanation() throws Exception {
        doThrow(new InvalidResetLinkException()).when(resets).reset(any(), any(), any(), any());

        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"x\",\"challengeId\":\"c\",\"encryptedPassword\":\"e\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("El enlace no es válido")));
    }

    @Test
    void tooManyRecoveryRequestsAreRejected() throws Exception {
        doThrow(new RecoveryBlockedException()).when(resets).requestLink(any(), any());

        mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@gmail.com\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aSuccessfulResetAnswersNoContent() throws Exception {
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\",\"challengeId\":\"c\",\"encryptedPassword\":\"e\"}"))
                .andExpect(status().isNoContent());
        verify(resets).reset("t", null, "c", "e");
    }

    @Test
    void aRefreshTokenRenewsTheSessionWithoutTheOldAccessToken() throws Exception {
        when(sessions.refresh("r1")).thenReturn(new AuthResponse("jwt2", Instant.parse("2026-10-17T10:00:00Z"),
                new UserResponse(1L, "ana", User.Role.USER), "r2",
                Instant.parse("2026-10-31T10:00:00Z")));

        // the browser may still send its expired access token: on these public endpoints it is ignored
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer expired.access.token")
                        .content("{\"refreshToken\":\"r1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt2"))
                .andExpect(jsonPath("$.refreshToken").value("r2"))
                .andExpect(jsonPath("$.user.username").value("ana"));
    }

    @Test
    void anEndedSessionAnswers401InSpanish() throws Exception {
        when(sessions.refresh(any())).thenThrow(new InvalidSessionException());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"used\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Tu sesión terminó. Inicia sesión de nuevo."));
    }

    @Test
    void signingOutWorksEvenWithAnExpiredAccessToken() throws Exception {
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"r1\"}"))
                .andExpect(status().isNoContent());
        verify(sessions).end("r1");

        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        verify(sessions).end(null);
    }
}
