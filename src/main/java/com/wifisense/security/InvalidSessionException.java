package com.wifisense.security;

/** The refresh token cannot renew the session: unknown, expired, used, revoked, or the account changed. */
public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Tu sesión terminó. Inicia sesión de nuevo.");
    }
}
