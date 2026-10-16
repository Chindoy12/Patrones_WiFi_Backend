package com.wifisense.mail;

/** Sends transactional email (Strategy: Brevo in production, log-only or disabled elsewhere). */
public interface EmailSender {

    /** Throws {@link EmailUnavailableException} when the message could not be handed to the provider. */
    void send(EmailMessage message);
}
