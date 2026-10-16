package com.wifisense.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends email in the background. The recovery endpoint answers just as fast whether or not an account exists (and
 * whether or not Brevo is slow), so its response time does not reveal which emails are registered.
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);
    private final EmailSender sender;

    public MailDispatcher(EmailSender sender) {
        this.sender = sender;
    }

    public void sendLater(EmailMessage message) {
        Thread.ofVirtual().name("mail").start(() -> {
            try {
                sender.send(message);
            } catch (RuntimeException e) {
                log.error("Recovery email could not be sent: {}", e.getMessage());
            }
        });
    }
}
