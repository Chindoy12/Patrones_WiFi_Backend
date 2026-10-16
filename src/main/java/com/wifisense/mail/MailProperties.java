package com.wifisense.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Outgoing email (Brevo's HTTP API) and the recovery link. {@code brevoApiKey} and {@code from} come from the
 * environment (BREVO_API_KEY, MAIL_FROM); without them no email is sent. {@code frontendUrl} is where the link
 * points. {@code logLinks} prints the link in the log instead of sending it: only for local development.
 */
@ConfigurationProperties("wifisense.mail")
public record MailProperties(
        String brevoApiKey,
        String from,
        @DefaultValue("WiFiSense") String fromName,
        @DefaultValue("http://localhost:5173") String frontendUrl,
        @DefaultValue("30m") Duration resetTtl,
        @DefaultValue("false") boolean logLinks) {

    public boolean configured() {
        return brevoApiKey != null && !brevoApiKey.isBlank() && from != null && !from.isBlank();
    }
}
