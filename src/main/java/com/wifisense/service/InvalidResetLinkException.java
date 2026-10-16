package com.wifisense.service;

/** The link is malformed, unknown, already used or expired; always the same message. */
public class InvalidResetLinkException extends RuntimeException {

    public InvalidResetLinkException() {
        super("El enlace no es válido o ya venció. Pide uno nuevo desde «¿Olvidaste tu contraseña?».");
    }
}
