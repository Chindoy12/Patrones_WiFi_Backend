package com.wifisense.service;

/** The agent sent no key, a malformed one, or one that was revoked. Always the same message: nothing to probe. */
public class InvalidAgentKeyException extends RuntimeException {

    public InvalidAgentKeyException() {
        super("La clave del agente no es válida o fue revocada.");
    }
}
