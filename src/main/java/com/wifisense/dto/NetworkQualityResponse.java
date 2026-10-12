package com.wifisense.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.wifisense.network.DataSourceType;

import java.time.Instant;

/** Quality of a monitored network graded by the AI from its latest measurement. */
public record NetworkQualityResponse(Long networkId, String ssid, Instant measuredAt, DataSourceType source,
                                     boolean simulatedData, Integer connectedDevices, JsonNode quality) {
}
