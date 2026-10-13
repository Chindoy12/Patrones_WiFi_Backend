package com.wifisense.service;

/** The agent reported again too soon. {@code retryAfterSeconds} goes to the Retry-After header. */
public class AgentReportLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public AgentReportLimitException(long retryAfterSeconds) {
        super("El agente envió reportes demasiado seguidos. Espera un momento.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
