package com.wifisense.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MailConfig {

    @Bean
    EmailSender emailSender(MailProperties properties, RestClient.Builder builder) {
        return properties.configured()
                ? new BrevoEmailSender(builder, properties, BrevoEmailSender.DEFAULT_URL)
                : new LogOnlyEmailSender(properties.logLinks());
    }
}
