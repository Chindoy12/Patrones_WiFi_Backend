package com.wifisense.service;

import com.wifisense.dto.AgentReportRequest;
import com.wifisense.model.AgentCapabilities;
import com.wifisense.model.MonitorMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCapabilitiesServiceTest {

    @Test
    void whatTheAgentDidNotSayIsFalseOrUnknownNeverAssumed() {
        AgentCapabilities.Snapshot snapshot = AgentCapabilitiesService.snapshot(report(
                new AgentReportRequest.Capabilities(null, true, null, null, null, null, null, null)));

        assertThat(snapshot.captureConsent()).isTrue();
        assertThat(snapshot.elevated()).isFalse();
        assertThat(snapshot.npcap()).isFalse();
        assertThat(snapshot.pktmon()).isFalse();
        assertThat(snapshot.monitorMode()).isEqualTo(MonitorMode.UNKNOWN);
        assertThat(snapshot.captureInterfaces()).isZero();
    }

    @Test
    void theReportedToolsAndPlatformAreKept() {
        AgentCapabilities.Snapshot snapshot = AgentCapabilitiesService.snapshot(report(
                new AgentReportRequest.Capabilities(true, false, true, true, false, false, "SUPPORTED", 3)));

        assertThat(snapshot.platform()).isEqualTo("Windows 11");
        assertThat(snapshot.agentVersion()).isEqualTo("2.0.0");
        assertThat(snapshot.npcap()).isTrue();
        assertThat(snapshot.pktmon()).isTrue();
        assertThat(snapshot.monitorMode()).isEqualTo(MonitorMode.SUPPORTED);
        assertThat(snapshot.captureInterfaces()).isEqualTo(3);
    }

    private static AgentReportRequest report(AgentReportRequest.Capabilities capabilities) {
        return new AgentReportRequest("2.0.0", " Windows 11 ", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, 1, null, null, null, null, null, null, null, capabilities);
    }
}
