package com.wifisense.mail;

/** An email with a plain-text version (for clients that do not show HTML) and an HTML version. */
public record EmailMessage(String to, String subject, String text, String html) {
}
