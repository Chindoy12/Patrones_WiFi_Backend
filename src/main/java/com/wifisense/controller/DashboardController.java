package com.wifisense.controller;

import com.wifisense.dto.DashboardSummary;
import com.wifisense.dto.NetworkQualityResponse;
import com.wifisense.dto.SystemStatus;
import com.wifisense.event.ActivityLogObserver;
import com.wifisense.network.decorator.DataSourceMetrics;
import com.wifisense.service.DashboardService;
import com.wifisense.service.NetworkQualityService;
import com.wifisense.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DataSourceMetrics dataSourceMetrics;
    private final ActivityLogObserver activityLog;
    private final NetworkQualityService qualityService;
    private final SystemStatusService statusService;

    public DashboardController(DashboardService dashboardService, DataSourceMetrics dataSourceMetrics,
                               ActivityLogObserver activityLog, NetworkQualityService qualityService,
                               SystemStatusService statusService) {
        this.dashboardService = dashboardService;
        this.dataSourceMetrics = dataSourceMetrics;
        this.activityLog = activityLog;
        this.qualityService = qualityService;
        this.statusService = statusService;
    }

    @GetMapping("/dashboard/summary")
    public DashboardSummary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/dashboard/status")
    public SystemStatus status() {
        return statusService.status();
    }

    @GetMapping("/networks/{networkId}/quality")
    public NetworkQualityResponse quality(@PathVariable Long networkId) {
        return qualityService.quality(networkId);
    }

    @GetMapping("/observability/data-sources")
    public List<DataSourceMetrics.Snapshot> dataSources() {
        return dataSourceMetrics.snapshot();
    }

    @GetMapping("/observability/activity")
    public List<ActivityLogObserver.ActivityEntry> activity() {
        return activityLog.recentActivity();
    }
}
