package com.wifisense.service;

import com.wifisense.TestData;
import com.wifisense.dto.WeeklyReport;
import com.wifisense.model.NetworkStatus;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.WeeklyReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WeeklyReportServiceTest {

    // Sunday 18 October 2026, 15:00 in Colombia
    private static final Instant NOW = Instant.parse("2026-10-18T20:00:00Z");
    private final NetworkRepository networks = mock(NetworkRepository.class);
    private final WeeklyReportRepository data = mock(WeeklyReportRepository.class);
    private final WeeklyReportService service = new WeeklyReportService(networks, data, "America/Bogota",
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static WeeklyReport.Totals totals(long measurements, Double latency, long alerts, long critical) {
        return new WeeklyReport.Totals(measurements, latency, 2.0, 0.0, 85.7, 120.5, alerts, critical);
    }

    @Test
    void theWeekGoesFromMondayToSundayInColombia() {
        when(networks.findById(5L)).thenReturn(Optional.of(TestData.network(5L, NetworkStatus.NORMAL)));
        when(data.totals(eq(5L), any(), any())).thenReturn(totals(0, null, 0, 0));

        WeeklyReport report = service.weekly(5L, LocalDate.of(2026, 10, 15));

        assertThat(report.start()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(report.end()).isEqualTo(LocalDate.of(2026, 10, 18));
        Instant monday = LocalDate.of(2026, 10, 12).atStartOfDay(ZoneId.of("America/Bogota")).toInstant();
        assertThat(monday).isEqualTo(Instant.parse("2026-10-12T05:00:00Z"));
        verify(data).totals(5L, monday, Instant.parse("2026-10-19T05:00:00Z"));
        verify(data).totals(5L, Instant.parse("2026-10-05T05:00:00Z"), monday);
        // without a date it is the current week; a week that has not started is refused
        assertThat(service.weekly(5L, null).start()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThatThrownBy(() -> service.weekly(5L, LocalDate.of(2026, 10, 19)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theHighlightsSayWhatChangedInWords() {
        List<WeeklyReport.Day> days = List.of(
                new WeeklyReport.Day(LocalDate.of(2026, 10, 12), 200, 20, 30.0, 2.0, 0.0, -60.0, 5, 10.0, 0),
                new WeeklyReport.Day(LocalDate.of(2026, 10, 13), 250, 22, 45.5, 3.0, 0.5, -61.0, 6, 12.0, 1));

        List<String> lines = WeeklyReportService.highlights(totals(450, 38.6, 2, 1), totals(400, 50.0, 0, 0), days,
                new WeeklyReport.WorstHour(20, 85.2, 12), new WeeklyReport.Devices(9, 2),
                new WeeklyReport.Security(74, "ACCEPTABLE", 60));

        assertThat(lines).containsExactly(
                "El sensor midió el 86 % de las horas de la semana (450 mediciones).",
                "La latencia promedio bajó 23 % frente a la semana anterior (de 50.0 a 38.6 ms).",
                "El día más lento fue el 2026-10-13 (45.5 ms en promedio).",
                "La peor hora fue las 20:00, con 85.2 ms de latencia en promedio.",
                "Aparecieron 2 equipos nuevos en la red.",
                "Hubo 1 alerta crítica.",
                "Seguridad del último reporte: 74/100 (la más baja de la semana fue 60).");
    }

    @Test
    void aWeekWithoutMeasurementsSaysToCheckTheSensor() {
        assertThat(WeeklyReportService.highlights(totals(0, null, 0, 0), totals(0, null, 0, 0), List.of(), null,
                null, null)).containsExactly(
                "No hubo mediciones esta semana: revisa que el sensor de la red esté encendido.");
        assertThat(WeeklyReportService.highlights(totals(10, 30.0, 0, 0), totals(10, 29.5, 0, 0), List.of(), null,
                null, null)).contains("La latencia promedio se mantuvo estable (30.0 ms).", "No hubo alertas.");
    }
}
