package com.wifisense.service;

public class InvalidPairingCodeException extends RuntimeException {

    public InvalidPairingCodeException() {
        super("El código no es válido o ya venció. Pide uno nuevo en Mi Wi-Fi > Agente WiFiSense.");
    }
}
