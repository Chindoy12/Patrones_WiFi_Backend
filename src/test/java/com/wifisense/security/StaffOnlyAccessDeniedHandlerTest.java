package com.wifisense.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StaffOnlyAccessDeniedHandlerTest {

    private final List<String> heard = new ArrayList<>();
    private final StaffOnlyAccessDeniedHandler handler =
            new StaffOnlyAccessDeniedHandler((user, method, path) -> heard.add(user + " " + method + " " + path));

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aTeamMemberGetsAnHonest403AndTheRefusalIsHeard() throws Exception {
        signIn("Ana.Analista", "ROLE_ANALYST");
        MockHttpServletResponse response = refuse("POST", "/api/networks/4/sensor/captures");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("No tienes permiso");
        assertThat(heard).containsExactly("Ana.Analista POST /api/networks/4/sensor/captures");
    }

    @Test
    void anEndUserCannotTellATeamFeatureFromOneThatDoesNotExist() throws Exception {
        signIn("beto", "ROLE_USER");
        MockHttpServletResponse response = refuse("GET", "/api/networks/4/sensor/captures");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("no existe").doesNotContain("permiso");
    }

    @Test
    void aListenerThatFailsNeverChangesTheAnswer() throws Exception {
        StaffOnlyAccessDeniedHandler failing = new StaffOnlyAccessDeniedHandler((user, method, path) -> {
            throw new IllegalStateException("auditoría caída");
        });
        signIn("Ana.Analista", "ROLE_ANALYST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        failing.handle(new MockHttpServletRequest("GET", "/api/users"), response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private static void signIn(String name, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(name, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private MockHttpServletResponse refuse(String method, String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest(method, path), response, new AccessDeniedException("no"));
        return response;
    }
}
