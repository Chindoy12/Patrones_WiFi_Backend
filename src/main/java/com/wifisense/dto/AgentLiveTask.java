package com.wifisense.dto;

/**
 * Someone is watching «En vivo»: the agent measures every {@code sampleSeconds} for at most {@code maxSeconds}. Only
 * numbers: the agent never runs commands sent by the server.
 */
public record AgentLiveTask(boolean live, int sampleSeconds, int maxSeconds) {
}
