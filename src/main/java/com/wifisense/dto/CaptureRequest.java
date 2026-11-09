package com.wifisense.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What an administrator asks for: the authorized purpose (why this capture is needed, kept with it and in the audit)
 * and its limits. The bytes limit is the server's own; the configured maximums are checked by the service.
 */
public record CaptureRequest(
        @NotBlank(message = "Escribe el propósito de la captura.")
        @Size(min = 10, max = 200, message = "El propósito debe tener entre 10 y 200 caracteres.") String purpose,
        @Min(value = 5, message = "La captura dura al menos 5 segundos.")
        @Max(value = 300, message = "La captura dura como máximo 300 segundos.") Integer maxSeconds,
        @Min(value = 100, message = "La captura guarda al menos 100 paquetes.")
        @Max(value = 100000, message = "La captura guarda como máximo 100 000 paquetes.") Integer maxPackets) {
}
