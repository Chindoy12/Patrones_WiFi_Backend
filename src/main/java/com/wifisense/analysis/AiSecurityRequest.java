package com.wifisense.analysis;

import java.util.List;

/** Contract of POST /api/v1/security/assess in WiFiSense-ai: what the agent saw, every field optional. */
public record AiSecurityRequest(String authentication, String cipher, List<Integer> openPorts, Integer deviceCount,
                                Integer newDeviceCount, String band, Integer channel, Integer neighborsSameChannel) {
}
