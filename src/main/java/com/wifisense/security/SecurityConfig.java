package com.wifisense.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Configuration
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String ANALYST = "ANALYST";
    private static final String VIEWER = "VIEWER";
    private static final String END_USER = "USER";

    /**
     * Public account endpoints: a token in the Authorization header is ignored there. Otherwise an expired access
     * token, sent along with the refresh token that is meant to renew it, would be rejected with 401 before the
     * request even reached /api/auth/refresh.
     */
    static final Set<String> PUBLIC_AUTH_PATHS = Set.of("/api/auth/challenge", "/api/auth/login",
            "/api/auth/register", "/api/auth/refresh", "/api/auth/logout", "/api/auth/password/forgot",
            "/api/auth/password/reset");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        // The refresh token is the credential here: the access token may have already expired.
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/challenge").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password/forgot", "/api/auth/password/reset").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // The agent has no session: AgentReportService checks its own key (X-Agent-Key).
                        .requestMatchers(HttpMethod.POST, "/api/agent/reports", "/api/agent/pair").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/my/**").hasRole(END_USER)
                        .requestMatchers("/api/users/**", "/api/observability/**").hasRole(ADMIN)
                        // Real end-user networks: the team members who support them, never a read-only viewer.
                        .requestMatchers("/api/home-networks/**").hasAnyRole(ADMIN, ANALYST)
                        .requestMatchers(HttpMethod.POST, "/api/networks/*/measurements/collect",
                                "/api/networks/*/analyses").hasAnyRole(ADMIN, ANALYST)
                        .requestMatchers(HttpMethod.PATCH, "/api/alerts/**").hasAnyRole(ADMIN, ANALYST)
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole(ADMIN)
                        .requestMatchers("/api/**").hasAnyRole(ADMIN, ANALYST, VIEWER)
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.accessDeniedHandler(new StaffOnlyAccessDeniedHandler()))
                .oauth2ResourceServer(oauth -> oauth.bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    /** Signature and expiry, plus the account check (deleted, disabled or password changed) when it is available. */
    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties, ObjectProvider<SessionTokenValidator> sessions) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256).build();
        SessionTokenValidator session = sessions.getIfAvailable();
        if (session != null) {
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), session));
        }
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        return request -> PUBLIC_AUTH_PATHS.contains(request.getRequestURI()) ? null : header.resolve(request);
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static SecretKey secretKey(SecurityProperties properties) {
        return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
