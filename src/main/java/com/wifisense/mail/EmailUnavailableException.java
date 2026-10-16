package com.wifisense.mail;

public class EmailUnavailableException extends RuntimeException {

    public EmailUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
