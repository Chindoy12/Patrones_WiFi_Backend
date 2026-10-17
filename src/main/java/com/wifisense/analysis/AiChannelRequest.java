package com.wifisense.analysis;

import java.util.List;

/** Contract of POST /api/v1/channel/recommend in WiFiSense-ai: the router's channel and the neighbours per channel. */
public record AiChannelRequest(String band, Integer channel, List<ChannelCount> channelCounts) {

    /** Neighbouring access points the agent saw on one channel (the user's own network is not counted). */
    public record ChannelCount(int channel, int networks) {
    }
}
