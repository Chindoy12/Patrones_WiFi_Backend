package com.wifisense.service;

import com.wifisense.dto.WeeklyReport;
import com.wifisense.model.Network;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.WeeklyReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Weekly report of a team network: Monday to Sunday in the zone of the team ({@code wifisense.reports.zone},
 * Colombia by default), compared with the week before, and its highlights written for people.
 */
@Service
public class WeeklyReportService {

    private final NetworkRepository networks;
    private final WeeklyReportRepository data;
    private final ZoneId zone;
    private final Clock clock;

    public WeeklyReportService(NetworkRepository networks, WeeklyReportRepository data,
                               @Value("${wifisense.reports.zone:America/Bogota}") String zone, Clock clock) {
        this.networks = networks;
        this.data = data;
        this.zone = ZoneId.of(zone);
        this.clock = clock;
    }

    /** {@code day}: any day of the wanted week (the current week when null); a week in the future is refused. */
    @Transactional(readOnly = true)
    public WeeklyReport weekly(Long networkId, LocalDate day) {
        Network network = networks.findById(networkId)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate monday = (day == null ? today : day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (monday.isAfter(today)) {
            throw new IllegalArgumentException("La semana del reporte todavía no empieza");
        }
        Instant from = monday.atStartOfDay(zone).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(zone).toInstant();
        Instant previousFrom = monday.minusWeeks(1).atStartOfDay(zone).toInstant();
        String zoneId = zone.getId();

        WeeklyReport.Totals week = data.totals(networkId, from, to);
        WeeklyReport.Totals previous = data.totals(networkId, previousFrom, from);
        List<WeeklyReport.Day> days = data.days(networkId, from, to, zoneId);
        WeeklyReport.WorstHour worstHour = data.worstHour(networkId, from, to, zoneId);
        WeeklyReport.Devices devices = data.devices(networkId, from, to);
        WeeklyReport.Security security = data.security(networkId, from, to);
        return new WeeklyReport(networkId, network.getSsid(), zoneId, monday, monday.plusDays(6), week, previous,
                days, worstHour, data.protocols(networkId, from, to), devices, security,
                highlights(week, previous, days, worstHour, devices, security));
    }

    /** What a person should take from the week, in short sentences. */
    static List<String> highlights(WeeklyReport.Totals week, WeeklyReport.Totals previous, List<WeeklyReport.Day> days,
                                   WeeklyReport.WorstHour worstHour, WeeklyReport.Devices devices,
                                   WeeklyReport.Security security) {
        List<String> lines = new ArrayList<>();
        if (week.measurements() == 0) {
            lines.add("No hubo mediciones esta semana: revisa que el sensor de la red esté encendido.");
            return lines;
        }
        lines.add(String.format(Locale.ROOT, "El sensor midió el %.0f %% de las horas de la semana (%d mediciones).",
                week.coveragePct(), week.measurements()));
        if (week.latencyAvgMs() != null && previous.latencyAvgMs() != null && previous.latencyAvgMs() > 0) {
            double change = (week.latencyAvgMs() - previous.latencyAvgMs()) / previous.latencyAvgMs() * 100;
            if (Math.abs(change) >= 5) {
                lines.add(String.format(Locale.ROOT, "La latencia promedio %s %.0f %% frente a la semana anterior "
                                + "(de %.1f a %.1f ms).", change > 0 ? "subió" : "bajó", Math.abs(change),
                        previous.latencyAvgMs(), week.latencyAvgMs()));
            } else {
                lines.add(String.format(Locale.ROOT, "La latencia promedio se mantuvo estable (%.1f ms).",
                        week.latencyAvgMs()));
            }
        }
        days.stream().filter(d -> d.latencyAvgMs() != null).max(Comparator.comparing(WeeklyReport.Day::latencyAvgMs))
                .filter(d -> days.size() > 1)
                .ifPresent(d -> lines.add(String.format(Locale.ROOT, "El día más lento fue el %s (%.1f ms en promedio).",
                        d.date(), d.latencyAvgMs())));
        if (worstHour != null) {
            lines.add(String.format(Locale.ROOT, "La peor hora fue las %02d:00, con %.1f ms de latencia en promedio.",
                    worstHour.hour(), worstHour.latencyAvgMs()));
        }
        if (week.packetLossAvgPct() != null && week.packetLossAvgPct() >= 1) {
            lines.add(String.format(Locale.ROOT, "Hubo pérdida de paquetes: %.1f %% en promedio.",
                    week.packetLossAvgPct()));
        }
        if (devices != null && devices.newOnes() > 0) {
            lines.add(devices.newOnes() == 1 ? "Apareció 1 equipo nuevo en la red."
                    : "Aparecieron " + devices.newOnes() + " equipos nuevos en la red.");
        }
        if (week.criticalAlerts() > 0) {
            lines.add(week.criticalAlerts() == 1 ? "Hubo 1 alerta crítica." : "Hubo " + week.criticalAlerts()
                    + " alertas críticas.");
        } else {
            lines.add(week.alerts() == 0 ? "No hubo alertas." : "Sin alertas críticas (" + week.alerts()
                    + (week.alerts() == 1 ? " alerta en total)." : " alertas en total)."));
        }
        if (security != null) {
            lines.add("Seguridad del último reporte: " + security.latestScore() + "/100"
                    + (security.lowestScore() < security.latestScore() ? " (la más baja de la semana fue "
                    + security.lowestScore() + ")." : "."));
        }
        return lines;
    }
}
