package com.wifisense.dto;

import java.time.Instant;

/** Code to type in the installer, e.g. "K7QM-4RZD", and when it stops working. */
public record AgentPairingCodeResponse(String code, Instant expiresAt) {
}
