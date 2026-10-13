package com.wifisense.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** What the browser measured. Ranges match the database so a tampered request cannot store absurd values. */
public record ConnectionTestRequest(
        @NotNull(message = "Falta la latencia.")
        @DecimalMin(value = "0.1", message = "La latencia debe estar entre 0,1 y 10 000 ms.")
        @DecimalMax(value = "10000", message = "La latencia debe estar entre 0,1 y 10 000 ms.")
        Double latencyMs,

        @NotNull(message = "Falta el jitter.")
        @DecimalMin(value = "0", message = "El jitter debe estar entre 0 y 10 000 ms.")
        @DecimalMax(value = "10000", message = "El jitter debe estar entre 0 y 10 000 ms.")
        Double jitterMs,

        @NotNull(message = "Falta la velocidad de bajada.")
        @DecimalMin(value = "0", message = "La velocidad de bajada debe estar entre 0 y 10 000 Mbps.")
        @DecimalMax(value = "10000", message = "La velocidad de bajada debe estar entre 0 y 10 000 Mbps.")
        Double downloadMbps,

        @NotNull(message = "Falta la velocidad de subida.")
        @DecimalMin(value = "0", message = "La velocidad de subida debe estar entre 0 y 10 000 Mbps.")
        @DecimalMax(value = "10000", message = "La velocidad de subida debe estar entre 0 y 10 000 Mbps.")
        Double uploadMbps) {
}
