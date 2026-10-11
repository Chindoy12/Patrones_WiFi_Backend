package com.wifisense.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * End users must not learn that team-only features exist: for them a forbidden endpoint answers 404,
 * exactly like an endpoint that does not exist. Team members still get an honest 403.
 */
public class StaffOnlyAccessDeniedHandler implements AccessDeniedHandler {

    private static final String END_USER = "ROLE_USER";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        boolean endUser = isEndUser(SecurityContextHolder.getContext().getAuthentication());
        HttpStatus status = endUser ? HttpStatus.NOT_FOUND : HttpStatus.FORBIDDEN;
        String detail = endUser ? "El recurso solicitado no existe." : "No tienes permiso para realizar esta acción.";
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Same shape as any other ProblemDetail, so a hidden endpoint is indistinguishable from a missing one.
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail
                + "\",\"instance\":\"" + request.getRequestURI().replace("\"", "") + "\"}");
    }

    private static boolean isEndUser(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> END_USER.equals(authority.getAuthority()));
    }
}
