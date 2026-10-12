package com.wifisense.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Only letters, numbers, spaces and a few signs, so names cannot carry markup or scripts. */
public record HomeNetworkRequest(
        @NotBlank(message = "Escribe un nombre para tu red.")
        @Size(min = 2, max = 40, message = "El nombre debe tener entre 2 y 40 caracteres.")
        @Pattern(regexp = "^[\\p{L}\\p{N} ._'-]*$", message = "El nombre solo puede tener letras, números, espacios y . _ ' -")
        String name,
        @Size(max = 60, message = "El proveedor puede tener hasta 60 caracteres.")
        @Pattern(regexp = "^[\\p{L}\\p{N} ._'&-]*$", message = "El proveedor solo puede tener letras, números, espacios y . _ ' & -")
        String provider,
        @Min(value = 1, message = "La velocidad contratada debe estar entre 1 y 10 000 Mbps.")
        @Max(value = 10_000, message = "La velocidad contratada debe estar entre 1 y 10 000 Mbps.")
        Integer contractedMbps) {
}
