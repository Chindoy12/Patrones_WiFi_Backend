package com.wifisense.dto;

/** The answer to a live sample: {@code keepGoing} is false once nobody is watching, and the agent stops. */
public record AgentLiveAck(boolean keepGoing) {
}
