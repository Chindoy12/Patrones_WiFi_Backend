package com.wifisense.dto;

import jakarta.validation.constraints.NotNull;

public record AlertPreferenceRequest(@NotNull Boolean newDevice, @NotNull Boolean security, @NotNull Boolean anomaly) {
}
