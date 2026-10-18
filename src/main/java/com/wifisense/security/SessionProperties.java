package com.wifisense.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * How long a session lasts. {@code refreshTtl}: a refresh token not used for this long expires and the user has to
 * sign in again (each refresh starts the count again). {@code reuseGrace}: a token used up this recently is refused
 * without revoking its family, because it is usually the same browser retrying a refresh whose answer got lost.
 */
@ConfigurationProperties("wifisense.session")
public record SessionProperties(@DefaultValue("14d") Duration refreshTtl,
                                @DefaultValue("10s") Duration reuseGrace) {

    public SessionProperties {
        if (refreshTtl.isNegative() || refreshTtl.isZero()) {
            throw new IllegalStateException("wifisense.session.refresh-ttl must be positive");
        }
    }
}
