package com.wifisense.integration;

import com.wifisense.dto.DeviceSessionHistory;
import com.wifisense.dto.WeeklyReport;
import com.wifisense.service.DeviceSessionTracker;
import com.wifisense.service.WeeklyReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** The SQL of Avance 15 against the real schema: connection periods of the devices and the weekly report. */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
class DeviceHistoryAndWeeklyReportIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private WeeklyReportService weekly;

    private long teamNetwork(String ssid) {
        String place = "Sede " + ssid;
        jdbc.update("INSERT INTO locations (name) VALUES (?)", place);
        jdbc.update("INSERT INTO zones (location_id, name) SELECT id, 'Piso' FROM locations WHERE name = ?", place);
        byte[] mac = new byte[5];
        new Random().nextBytes(mac);
        String bssid = String.format("02:%02X:%02X:%02X:%02X:%02X", mac[0], mac[1], mac[2], mac[3], mac[4]);
        return jdbc.queryForObject("INSERT INTO networks (zone_id, ssid, bssid, frequency_band, channel, security_type) "
                + "SELECT z.id, ?, ?, '5GHz', 36, 'WPA2' FROM zones z JOIN locations l ON l.id = z.location_id "
                + "WHERE l.name = ? RETURNING id", Long.class, ssid, bssid, place);
    }

    @Test
    void aDeviceIsConnectedUntilItHasBeenMissingForLongerThanTheGap() {
        long network = teamNetwork("Red sesiones " + unique());
        String phone = "ab".repeat(12);
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(Duration.ofHours(2));
        jdbc.update("INSERT INTO network_sensor_devices (network_id, fingerprint, first_seen_at, last_seen_at) "
                + "VALUES (?, ?, ?, ?)", network, phone, Timestamp.from(start), Timestamp.from(start));
        DeviceSessionTracker tracker = new DeviceSessionTracker(jdbc, Duration.ofMinutes(12),
                Clock.fixed(start.plus(Duration.ofMinutes(90)), ZoneOffset.UTC));
        DeviceSessionTracker.Owner team = DeviceSessionTracker.Owner.TEAM;

        tracker.track(team, network, List.of(phone), start);
        tracker.track(team, network, List.of(phone), start.plus(Duration.ofMinutes(5)));
        tracker.track(team, network, List.of(), start.plus(Duration.ofMinutes(10)));  // a phone at rest: not yet gone
        tracker.track(team, network, List.of(phone), start.plus(Duration.ofMinutes(15)));
        tracker.track(team, network, List.of(), start.plus(Duration.ofMinutes(20)));
        tracker.track(team, network, List.of(), start.plus(Duration.ofMinutes(30)));  // missing 15 min: it left
        tracker.track(team, network, List.of(phone), start.plus(Duration.ofMinutes(60)));

        DeviceSessionHistory history = tracker.history(team, network, phone, 1);
        assertThat(history.connections()).isEqualTo(2);
        DeviceSessionHistory.Period last = history.periods().get(0);
        DeviceSessionHistory.Period first = history.periods().get(1);
        assertThat(first.connectedAt()).isEqualTo(start);
        assertThat(first.endedAt()).isEqualTo(start.plus(Duration.ofMinutes(15)));
        assertThat(first.minutes()).isEqualTo(15);
        // still open at 60 min, and the clock says 90: the sensor stopped reporting, so it ended at its last report
        assertThat(last.connectedAt()).isEqualTo(start.plus(Duration.ofMinutes(60)));
        assertThat(last.ongoing()).isFalse();
        assertThat(last.endedAt()).isEqualTo(start.plus(Duration.ofMinutes(60)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM network_sensor_device_sessions WHERE network_id = ? "
                + "AND disconnected_at IS NULL", Integer.class, network)).isEqualTo(1);

        jdbc.update("DELETE FROM networks WHERE id = ?", network);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM network_sensor_device_sessions WHERE network_id = ?",
                Integer.class, network)).isZero();
    }

    @Test
    void theWeeklyReportCountsEachDayOfColombiaAndComparesWithTheWeekBefore() {
        String ssid = "Red semanal " + unique();
        long network = teamNetwork(ssid);
        LocalDate monday = LocalDate.now(BOGOTA).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
        // last week: Monday 23:30 in Colombia is already Tuesday in UTC, and must count as Monday
        measure(network, monday.atTime(23, 30).atZone(BOGOTA).toInstant(), 40.0);
        measure(network, monday.atTime(20, 0).atZone(BOGOTA).toInstant(), 80.0);
        measure(network, monday.atTime(20, 10).atZone(BOGOTA).toInstant(), 80.0);
        measure(network, monday.atTime(20, 20).atZone(BOGOTA).toInstant(), 80.0);
        measure(network, monday.plusDays(2).atTime(9, 0).atZone(BOGOTA).toInstant(), 20.0);
        // the week before: slower
        measure(network, monday.minusDays(3).atTime(9, 0).atZone(BOGOTA).toInstant(), 120.0);

        WeeklyReport report = weekly.weekly(network, monday.plusDays(3));

        assertThat(report.start()).isEqualTo(monday);
        assertThat(report.end()).isEqualTo(monday.plusDays(6));
        assertThat(report.ssid()).isEqualTo(ssid);
        assertThat(report.week().measurements()).isEqualTo(5);
        assertThat(report.previousWeek().measurements()).isEqualTo(1);
        assertThat(report.days()).extracting(WeeklyReport.Day::date).containsExactly(monday, monday.plusDays(2));
        assertThat(report.days().get(0).measurements()).isEqualTo(4);
        assertThat(report.days().get(0).hoursMeasured()).isEqualTo(2);
        assertThat(report.worstHour().hour()).isEqualTo(20);
        assertThat(report.worstHour().latencyAvgMs()).isEqualTo(80.0);
        assertThat(report.highlights()).anyMatch(line -> line.startsWith("La latencia promedio bajó"));
        assertThat(report.highlights()).anyMatch(line -> line.contains("las 20:00"));

        jdbc.update("DELETE FROM networks WHERE id = ?", network);
    }

    private void measure(long network, Instant at, double latency) {
        jdbc.update("INSERT INTO measurements (network_id, latency_ms, jitter_ms, packet_loss_pct, source, measured_at) "
                + "VALUES (?, ?, 2, 0, 'AGENT', ?)", network, latency, Timestamp.from(at));
    }
}
