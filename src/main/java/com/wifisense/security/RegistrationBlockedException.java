package com.wifisense.security;

public class RegistrationBlockedException extends RuntimeException {

    public RegistrationBlockedException() {
        super("Se crearon demasiadas cuentas desde esta conexión. Intenta de nuevo más tarde.");
    }
}
