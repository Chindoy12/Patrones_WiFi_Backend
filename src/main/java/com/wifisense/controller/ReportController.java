package com.wifisense.controller;

import com.wifisense.dto.NetworkReport;
import com.wifisense.dto.WeeklyReport;
import com.wifisense.service.ReportService;
import com.wifisense.service.WeeklyReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final WeeklyReportService weeklyReports;

    public ReportController(ReportService reportService, WeeklyReportService weeklyReports) {
        this.reportService = reportService;
        this.weeklyReports = weeklyReports;
    }

    /** Monday to Sunday of the week that contains {@code week} (the current week by default). */
    @GetMapping("/networks/{networkId}/weekly")
    public WeeklyReport weekly(@PathVariable Long networkId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                               LocalDate week) {
        return weeklyReports.weekly(networkId, week);
    }

    @GetMapping("/networks/{networkId}")
    public NetworkReport report(
            @PathVariable Long networkId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.report(networkId, from, to);
    }

    @GetMapping("/comparison")
    public List<NetworkReport> compare(
            @RequestParam List<Long> networkIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.compare(networkIds, from, to);
    }
}
