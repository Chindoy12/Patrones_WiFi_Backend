package com.wifisense.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A team network measured by its sensor, end to end: the admin registers it, generates the code, the installer
 * exchanges it for the key, and the agent's report becomes a real measurement with traffic and protocols.
 */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
class SensorFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    @Test
    void theSensorOfATeamNetworkSendsRealMeasurementsWithTraffic() throws Exception {
        String admin = call(post("/api/auth/login"), null, Map.of("username", ADMIN, "password", PASSWORD), 200)
                .get("token").asText();
        String place = "Sede Sensor " + unique();
        long location = call(post("/api/locations"), admin, Map.of("name", place, "city", "Pasto"), 201)
                .get("id").asLong();
        long zone = call(post("/api/locations/" + location + "/zones"), admin, Map.of("name", "Piso 1", "floor", 1), 201)
                .get("id").asLong();

        // production: the form only offers the sensor, and a simulated network is refused
        assertThat(call(get("/api/networks/sources"), admin, null, 200).toString()).isEqualTo("[\"AGENT\"]");
        Map<String, Object> network = Map.of("zoneId", zone, "ssid", place, "bssid", randomMac(),
                "frequencyBand", "5GHz", "channel", 36, "securityType", "WPA3", "dataSourceType", "SIMULATION");
        call(post("/api/networks"), admin, network, 400);
        long id = call(post("/api/networks"), admin, with(network, "dataSourceType", "AGENT"), 201).get("id").asLong();
        assertThat(call(get("/api/networks/" + id + "/sensor"), admin, null, 200).get("installed").asBoolean())
                .isFalse();

        String code = call(post("/api/networks/" + id + "/sensor/pairing"), admin, null, 201).get("code").asText();
        JsonNode paired = call(post("/api/agent/pair"), null, Map.of("code", code.toLowerCase()), 200);
        assertThat(paired.get("networkName").asText()).isEqualTo(place);
        String key = paired.get("key").asText();
        call(post("/api/agent/pair"), null, Map.of("code", code), 400);  // a code works once

        JsonNode ack = report(key, Map.of("trafficBytes", 10_485_760, "trafficPackets", 9_000,
                "protocolPackets", List.of(Map.of("protocol", "TCP", "packets", 8_000),
                        Map.of("protocol", "UDP", "packets", 950), Map.of("protocol", "ICMP", "packets", 50))), 201);
        assertThat(ack.get("summary").asText()).startsWith("Sensor de «" + place + "»: medición guardada.");

        Map<String, Object> stored = jdbc.queryForMap("SELECT latency_ms, signal_strength_dbm, bandwidth_mbps, "
                + "connected_devices, source FROM measurements WHERE network_id = ?", id);
        assertThat(stored).containsEntry("latency_ms", 23.4).containsEntry("signal_strength_dbm", -61)
                .containsEntry("bandwidth_mbps", 433.3).containsEntry("connected_devices", 6)
                .containsEntry("source", "AGENT");
        assertThat(jdbc.queryForObject("SELECT traffic_volume_mb FROM traffic_observations WHERE network_id = ?",
                Double.class, id)).isEqualTo(10.0);
        assertThat(jdbc.queryForList("SELECT protocol || '=' || packet_count FROM protocol_statistics "
                + "WHERE network_id = ? ORDER BY protocol", String.class, id))
                .containsExactly("ICMP=50", "TCP=8000", "UDP=950");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_results WHERE network_id = ?", Long.class, id))
                .isEqualTo(1);

        // the panel: the sensor is connected, and asking the backend to collect explains where the data comes from
        JsonNode sensor = call(get("/api/networks/" + id + "/sensor"), admin, null, 200);
        assertThat(sensor.get("online").asBoolean()).isTrue();
        assertThat(sensor.get("agentVersion").asText()).isEqualTo("1.4.0");
        assertThat(call(get("/api/networks/" + id + "/measurements"), admin, null, 200).get(0).get("source").asText())
                .isEqualTo("AGENT");
        assertThat(call(post("/api/networks/" + id + "/measurements/collect"), admin, null, 503).get("detail")
                .asText()).startsWith("Esta red la mide su sensor");

        // a second report right away is refused until the agent's interval
        report(key, Map.of(), 429);

        // removing the sensor: its key stops working
        call(delete("/api/networks/" + id + "/sensor"), admin, null, 204);
        report(key, Map.of(), 401);
        call(delete("/api/networks/" + id), admin, null, 204);
    }

    private JsonNode report(String key, Map<String, Object> traffic, int expected) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("agentVersion", "1.4.0", "platform", "Windows",
                "authentication", "WPA3", "band", "5", "channel", 36, "signalDbm", -61, "rxRateMbps", 433.3,
                "latencyMs", 23.4, "jitterMs", 1.8, "packetLossPct", 0));
        body.put("deviceCount", 6);
        body.putAll(traffic);
        MockHttpServletRequestBuilder request = post("/api/agent/reports").header("X-Agent-Key", key);
        return call(request, null, body, expected);
    }

    private static Map<String, Object> with(Map<String, Object> map, String key, Object value) {
        Map<String, Object> copy = new java.util.HashMap<>(map);
        copy.put(key, value);
        return copy;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expected)
            throws Exception {
        request.header("X-Forwarded-For", "10.13." + new Random().nextInt(256) + "." + new Random().nextInt(256));
        if (token != null) {
            request.header(AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        String response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse()
                .getContentAsString();
        return response.isEmpty() ? json.nullNode() : json.readTree(response);
    }

    private static String randomMac() {
        byte[] bytes = new byte[6];
        new Random().nextBytes(bytes);
        return HexFormat.ofDelimiter(":").withUpperCase().formatHex(bytes);
    }
}
