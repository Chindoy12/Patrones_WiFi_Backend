package com.wifisense.dto;

/** What the installer receives in exchange for a valid code: the agent key (only time it travels) and the network. */
public record AgentPairResponse(String key, String prefix, String networkName) {
}
