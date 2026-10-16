package com.wifisense.mail;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Brevo's transactional email API (https://api.brevo.com/v3/smtp/email); works where SMTP ports are blocked. */
public class BrevoEmailSender implements EmailSender {

    static final String DEFAULT_URL = "https://api.brevo.com";

    private final RestClient client;
    private final MailProperties properties;

    public BrevoEmailSender(RestClient.Builder builder, MailProperties properties, String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("api-key", properties.brevoApiKey()).build();
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", properties.fromName(), "email", properties.from()),
                "to", List.of(Map.of("email", message.to())),
                "subject", message.subject(),
                "textContent", message.text(),
                "htmlContent", message.html());
        try {
            client.post().uri("/v3/smtp/email").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            // The provider's answer may echo the address; only the cause type goes to the log.
            throw new EmailUnavailableException("Brevo rejected or did not answer: " + e.getClass().getSimpleName(), e);
        }
    }
}
