package com.wifisense.speedtest;

/** The user ran too many connection tests in a short time. */
public class SpeedTestLimitException extends RuntimeException {

    public SpeedTestLimitException() {
        super("Hiciste muchas pruebas seguidas. Espera unos minutos e inténtalo de nuevo.");
    }
}
