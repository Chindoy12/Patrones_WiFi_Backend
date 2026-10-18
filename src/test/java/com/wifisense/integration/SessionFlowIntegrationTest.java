package com.wifisense.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign up, sign in, renew, sign out and close sessions through the real HTTP API and database. */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
class SessionFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    @Test
    void aSessionRenewsItselfOnceAndACopiedTokenClosesThatLogin() throws Exception {
        String name = "Ana.It" + unique();
        JsonNode registered = call(post("/api/auth/register"), null,
                Map.of("username", name, "password", PASSWORD, "email", "  " + name + "@Ejemplo.COM "), 201);
        // the database keeps the email in lowercase whatever the user typed
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE username = ?", String.class, name))
                .isEqualTo(name.toLowerCase() + "@ejemplo.com");

        // the name is found in any case and the session carries it as stored
        JsonNode login = call(post("/api/auth/login"), null,
                Map.of("username", name.toUpperCase(), "password", PASSWORD), 200);
        assertThat(login.at("/user/username").asText()).isEqualTo(name);
        call(get("/api/auth/me"), login.get("token").asText(), null, 200);

        String first = login.get("refreshToken").asText();
        JsonNode renewed = call(post("/api/auth/refresh"), null, Map.of("refreshToken", first), 200);
        String second = renewed.get("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);
        call(get("/api/auth/me"), renewed.get("token").asText(), null, 200);
        // only hashes are stored
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash IN (?, ?)",
                Integer.class, first, second)).isZero();

        // the used token comes back: someone copied it, so that whole login ends (the newest token too)
        call(post("/api/auth/refresh"), null, Map.of("refreshToken", first), 401);
        call(post("/api/auth/refresh"), null, Map.of("refreshToken", second), 401);

        // the session opened by the registration is another login: it still works until signing out
        JsonNode other = call(post("/api/auth/refresh"), null,
                Map.of("refreshToken", registered.get("refreshToken").asText()), 200);
        call(post("/api/auth/logout"), null, Map.of("refreshToken", other.get("refreshToken").asText()), 204);
        call(post("/api/auth/refresh"), null, Map.of("refreshToken", other.get("refreshToken").asText()), 401);
        call(post("/api/auth/refresh"), null, Map.of("refreshToken", "x".repeat(43)), 401);
    }

    @Test
    void changingThePasswordClosesEveryOtherSessionAndKeepsThisOne() throws Exception {
        String name = "Beto.It" + unique();
        call(post("/api/auth/register"), null,
                Map.of("username", name, "password", PASSWORD, "email", name + "@ejemplo.com"), 201);
        JsonNode laptop = call(post("/api/auth/login"), null, Map.of("username", name, "password", PASSWORD), 200);
        JsonNode phone = call(post("/api/auth/login"), null, Map.of("username", name, "password", PASSWORD), 200);

        JsonNode changed = call(put("/api/my/account/password"), laptop.get("token").asText(),
                Map.of("currentPassword", PASSWORD, "newPassword", "Nueva#2026bc"), 200);

        call(post("/api/auth/refresh"), null, Map.of("refreshToken", phone.get("refreshToken").asText()), 401);
        call(post("/api/auth/refresh"), null, Map.of("refreshToken", laptop.get("refreshToken").asText()), 401);
        call(post("/api/auth/refresh"), null, Map.of("refreshToken", changed.get("refreshToken").asText()), 200);
        call(post("/api/auth/login"), null, Map.of("username", name, "password", PASSWORD), 401);
        call(post("/api/auth/login"), null, Map.of("username", name, "password", "Nueva#2026bc"), 200);
    }

    @Test
    void aRoleChangeTakesEffectOnTheNextRequest() throws Exception {
        String admin = call(post("/api/auth/login"), null, Map.of("username", ADMIN, "password", PASSWORD), 200)
                .get("token").asText();
        String name = "Eva.It" + unique();
        long id = call(post("/api/users"), admin,
                Map.of("username", name, "password", PASSWORD, "role", "ANALYST"), 201).get("id").asLong();
        try {
            JsonNode eva = call(post("/api/auth/login"), null, Map.of("username", name, "password", PASSWORD), 200);
            call(get("/api/networks"), eva.get("token").asText(), null, 200);

            call(patch("/api/users/" + id + "/role"), admin, Map.of("role", "VIEWER"), 200);

            // the token still says ANALYST: refused at once, not when it expires
            call(get("/api/networks"), eva.get("token").asText(), null, 401);
            // the browser renews with the rejected token still in its header: it is ignored there
            JsonNode viewer = call(post("/api/auth/refresh"), eva.get("token").asText(),
                    Map.of("refreshToken", eva.get("refreshToken").asText()), 200);
            assertThat(viewer.at("/user/role").asText()).isEqualTo("VIEWER");
            call(get("/api/networks"), viewer.get("token").asText(), null, 200);
            call(post("/api/networks/1/analyses"), viewer.get("token").asText(), Map.of("type", "THRESHOLD"), 403);
        } finally {
            call(delete("/api/users/" + id), admin, null, 204);
        }
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expected)
            throws Exception {
        // a different address each time: the sign-up limit per address does not get in the way
        request.header("X-Forwarded-For", "10.12." + ThreadLocalRandom.current().nextInt(256) + "."
                + ThreadLocalRandom.current().nextInt(256));
        if (token != null) {
            request.header(AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        String response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse()
                .getContentAsString();
        return response.isEmpty() ? json.nullNode() : json.readTree(response);
    }
}
