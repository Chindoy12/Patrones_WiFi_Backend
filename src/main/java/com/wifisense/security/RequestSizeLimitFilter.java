package com.wifisense.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Rejects oversized API bodies (e.g. a huge paste into the login form) before they are parsed. Only the speed-test
 * upload accepts a large body, and it has its own limit.
 */
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 16 * 1024;
    public static final String UPLOAD_PATH = "/api/my/speedtest/upload";

    private final long maxUploadBytes;

    public RequestSizeLimitFilter(@Value("${wifisense.speedtest.max-upload:8MB}") String maxUpload) {
        this.maxUploadBytes = DataSize.parse(maxUpload).toBytes();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = UPLOAD_PATH.equals(request.getRequestURI()) ? maxUploadBytes : MAX_BODY_BYTES;
        if (request.getContentLengthLong() > limit) {
            reject(response);
            return;
        }
        try {
            chain.doFilter(new LimitedBodyRequest(request, limit), response);
        } catch (BodyTooLargeException e) {
            if (!response.isCommitted()) {
                response.reset();
                reject(response);
            }
        }
    }

    static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":413,\"detail\":\"La solicitud es demasiado grande.\"}");
    }
}
