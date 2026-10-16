package com.wifisense.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when Brevo is not configured. By default it sends nothing and only warns; with wifisense.mail.log-links=true
 * (local development only) it prints the message, recovery link included, so the flow can be tried without email.
 */
public class LogOnlyEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogOnlyEmailSender.class);
    private final boolean printMessages;

    public LogOnlyEmailSender(boolean printMessages) {
        this.printMessages = printMessages;
    }

    @Override
    public void send(EmailMessage message) {
        if (printMessages) {
            log.info("[correo de desarrollo] para {}: {}\n{}", message.to(), message.subject(), message.text());
        } else {
            log.warn("Email not sent: set BREVO_API_KEY and MAIL_FROM to send recovery emails");
        }
    }
}
