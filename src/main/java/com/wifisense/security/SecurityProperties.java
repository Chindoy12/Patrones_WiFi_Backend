package com.wifisense.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("wifisense.security")
public record SecurityProperties(String jwtSecret,
                                 @DefaultValue("8h") Duration tokenTtl,
                                 @DefaultValue("http://localhost:5173") List<String> allowedOrigins,
                                 @DefaultValue("true") boolean plainPasswordsAllowed) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes().length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and contain at least 32 bytes");
        }
    }
}
