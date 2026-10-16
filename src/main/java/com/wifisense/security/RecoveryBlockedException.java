package com.wifisense.security;

public class RecoveryBlockedException extends RuntimeException {

    public RecoveryBlockedException() {
        super("Hiciste muchas solicitudes seguidas. Espera unos minutos e intenta de nuevo.");
    }
}
