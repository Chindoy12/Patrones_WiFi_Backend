package com.wifisense.dto;

import java.time.Instant;

/** The only response that carries the key itself; it is never stored and cannot be shown again. */
public record AgentKeyCreated(String key, String prefix, Instant createdAt) {
}
