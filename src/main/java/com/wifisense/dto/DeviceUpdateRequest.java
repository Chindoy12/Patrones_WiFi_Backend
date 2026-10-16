package com.wifisense.dto;

import jakarta.validation.constraints.Size;

/** Rename a device and/or mark it authorized. Both optional; null leaves that field unchanged. */
public record DeviceUpdateRequest(@Size(max = 40, message = "El nombre es demasiado largo.") String label,
                                  Boolean authorized) {
}
