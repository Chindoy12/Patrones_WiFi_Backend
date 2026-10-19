package com.wifisense.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One week (Monday to Sunday, in the zone of the team) of a team network: totals compared with the week before, each
 * day, the worst hour, the protocols, the devices its sensor saw and the security audit, plus the highlights in words.
 */
public record WeeklyReport(Long networkId, String ssid, String zone, LocalDate start, LocalDate end, Totals week,
                           Totals previousWeek, List<Day> days, WorstHour worstHour, List<Protocol> protocols,
                           Devices devices, Security security, List<String> highlights) {

    /** {@code coveragePct}: share of the hours of the period with at least one measurement. */
    public record Totals(long measurements, Double latencyAvgMs, Double jitterAvgMs, Double packetLossAvgPct,
                         Double coveragePct, Double trafficMb, long alerts, long criticalAlerts) {
    }

    public record Day(LocalDate date, long measurements, int hoursMeasured, Double latencyAvgMs, Double jitterAvgMs,
                      Double packetLossAvgPct, Double signalAvgDbm, Integer maxDevices, Double trafficMb,
                      long alerts) {
    }

    public record WorstHour(int hour, double latencyAvgMs, long measurements) {
    }

    public record Protocol(String protocol, long packets) {
    }

    /** Only for networks with sensor; {@code newOnes} excludes the devices learned with its first report. */
    public record Devices(long seen, long newOnes) {
    }

    public record Security(Integer latestScore, String latestGrade, Integer lowestScore) {
    }
}
