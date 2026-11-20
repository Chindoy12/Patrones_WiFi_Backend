package com.wifisense.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The agent of a home of Mi Wi-Fi, end to end against the real schema: the user installs it with a code, its report is
 * stored in agent_reports and its devices in agent_devices (whose network column is home_network_id), and the user
 * names a device and marks it as not theirs. The same steps of a team network are in {@link SensorFlowIntegrationTest}.
 */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
class HomeAgentFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private String username;

    @AfterEach
    void deleteUser() {
        jdbc.update("DELETE FROM users WHERE username = ?", username);  // the network, its reports and devices go too
    }

    @Test
    void theAgentOfAHomeStoresItsReportAndDevicesAndTheOwnerEditsADevice() throws Exception {
        username = "Ana.Casa" + unique();
        String ana = call(post("/api/auth/register"), null,
                Map.of("username", username, "password", PASSWORD, "email", username + "@ejemplo.com"), 201)
                .get("token").asText();
        long casa = call(post("/api/my/networks"), ana, Map.of("name", "Casa de Ana", "contractedMbps", 200), 201)
                .get("id").asLong();

        String code = call(post("/api/my/networks/" + casa + "/agent/pairing"), ana, null, 201).get("code").asText();
        String key = call(post("/api/agent/pair"), null, Map.of("code", code), 200).get("key").asText();

        String phone = "c".repeat(24);
        String laptop = "d".repeat(24);
        Map<String, Object> report = new HashMap<>(Map.of("agentVersion", "2.0.0", "platform", "Windows",
                "ssid", "Casa de Ana", "authentication", "WPA2", "band", "5", "channel", 44, "signalDbm", -58,
                "latencyMs", 18.2, "jitterMs", 2.1, "packetLossPct", 0));
        report.put("deviceCount", 2);
        report.put("deviceHashes", List.of(phone, laptop));
        report.put("devices", List.of(Map.of("id", phone, "privateMac", true),
                Map.of("id", laptop, "vendor", "Lenovo", "name", "PC-Ana", "privateMac", false)));
        JsonNode ack = call(post("/api/agent/reports").header("X-Agent-Key", key), null, report, 201);
        assertThat(ack.get("deviceCount").asInt()).isEqualTo(2);

        Map<String, Object> stored = jdbc.queryForMap("SELECT ssid, channel, signal_dbm, latency_ms, device_count "
                + "FROM agent_reports WHERE home_network_id = ?", casa);
        assertThat(stored).containsEntry("ssid", "Casa de Ana").containsEntry("latency_ms", 18.2)
                .containsEntry("device_count", 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_devices WHERE home_network_id = ?", Long.class,
                casa)).isEqualTo(2);

        // what Mi Wi-Fi shows: the latest report and both devices, connected now
        JsonNode overview = call(get("/api/my/networks/" + casa + "/agent"), ana, null, 200);
        assertThat(overview.get("latest").get("ssid").asText()).isEqualTo("Casa de Ana");
        assertThat(overview.get("history")).hasSize(1);
        assertThat(overview.get("devices")).hasSize(2);
        assertThat(overview.get("devices").findValuesAsText("hostname")).contains("PC-Ana");
        assertThat(overview.get("devices").findValues("online")).allMatch(JsonNode::asBoolean);

        // the owner names the laptop and marks it as not theirs; a blank name removes it again
        JsonNode edited = call(put("/api/my/networks/" + casa + "/agent/devices/" + laptop), ana,
                Map.of("label", "  Portátil del primo  ", "authorized", false), 200);
        assertThat(edited.get("label").asText()).isEqualTo("Portátil del primo");
        assertThat(edited.get("authorized").asBoolean()).isFalse();
        assertThat(jdbc.queryForMap("SELECT label, authorized FROM agent_devices WHERE home_network_id = ? "
                + "AND fingerprint = ?", casa, laptop))
                .containsEntry("label", "Portátil del primo").containsEntry("authorized", false);
        call(put("/api/my/networks/" + casa + "/agent/devices/" + laptop), ana, Map.of("label", " "), 200);
        assertThat(jdbc.queryForObject("SELECT label FROM agent_devices WHERE home_network_id = ? AND fingerprint = ?",
                String.class, casa, laptop)).isNull();

        // the device not authorized is listed first, and when it was connected comes from its sessions
        assertThat(call(get("/api/my/networks/" + casa + "/agent"), ana, null, 200).get("devices").get(0).get("id")
                .asText()).isEqualTo(laptop);
        assertThat(call(get("/api/my/networks/" + casa + "/agent/devices/" + laptop + "/sessions"), ana, null, 200)
                .get("connections").asInt()).isEqualTo(1);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expected)
            throws Exception {
        // a different address each time: the sign-up limit per address does not get in the way
        request.header("X-Forwarded-For", "10.14." + ThreadLocalRandom.current().nextInt(256) + "."
                + ThreadLocalRandom.current().nextInt(256));
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
}
