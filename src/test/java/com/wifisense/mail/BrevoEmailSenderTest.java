package com.wifisense.mail;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Talks to a local HTTP server that plays the role of Brevo. */
class BrevoEmailSenderTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private volatile int status = 201;
    private final MailProperties properties = new MailProperties("xkeysib-test", "wifisense@gmail.com", "WiFiSense",
            "https://app.example", Duration.ofMinutes(30), false);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = "{\"messageId\":\"<1@brevo>\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private BrevoEmailSender sender() {
        return new BrevoEmailSender(RestClient.builder(), properties, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void sendsTheMessageWithTheApiKeyAndTheVerifiedSender() {
        sender().send(new EmailMessage("ana@gmail.com", "Asunto", "texto", "<p>html</p>"));

        assertThat(apiKey.get()).isEqualTo("xkeysib-test");
        assertThat(body.get()).contains("\"email\":\"wifisense@gmail.com\"").contains("\"email\":\"ana@gmail.com\"")
                .contains("\"subject\":\"Asunto\"").contains("\"htmlContent\":\"<p>html</p>\"");
    }

    @Test
    void aRejectionIsReportedAsUnavailable() {
        status = 401;

        assertThatThrownBy(() -> sender().send(new EmailMessage("ana@gmail.com", "s", "t", "h")))
                .isInstanceOf(EmailUnavailableException.class);
    }

    @Test
    void theRecoveryEmailEscapesTheNameAndStatesTheExpiry() {
        EmailMessage email = RecoveryEmail.build("ana@gmail.com", "<ana>", "https://app.example/restablecer#t=abc",
                Duration.ofMinutes(30));

        assertThat(email.html()).contains("&lt;ana&gt;").doesNotContain("<ana>").contains("30 minutos");
        assertThat(email.text()).contains("https://app.example/restablecer#t=abc");
    }
}
