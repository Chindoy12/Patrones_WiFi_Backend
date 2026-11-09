package com.wifisense.service;

import com.wifisense.dto.CaptureCapabilitiesResponse;
import com.wifisense.dto.SensorStatus;
import com.wifisense.model.AgentCapabilities;
import com.wifisense.model.MonitorMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.wifisense.service.CaptureFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

class CaptureReadinessTest {

    private final CaptureReadiness readiness = new CaptureReadiness(CaptureFixtures.PROPERTIES);

    private static SensorStatus sensor(boolean online, String version) {
        return new SensorStatus(true, online, "wsa_abcd", NOW.minusSeconds(86400), NOW.minusSeconds(60), version,
                "Windows", null, "Sala");
    }

    private static AgentCapabilities caps(boolean consent, boolean npcap, boolean pktmon, int interfaces,
                                          MonitorMode monitor) {
        AgentCapabilities caps = AgentCapabilities.ofSensor(4L);
        caps.update(new AgentCapabilities.Snapshot("Windows", "2.0.0", pktmon, consent, npcap, pktmon, false, false,
                monitor, interfaces), NOW.minusSeconds(60));
        return caps;
    }

    @Test
    void aSensorWithConsentAToolAndAnInterfaceCanCapture() {
        CaptureCapabilitiesResponse ready = readiness.evaluate(sensor(true, "2.0.0"),
                Optional.of(caps(true, true, false, 1, MonitorMode.UNSUPPORTED)), false);

        assertThat(ready.canCapture()).isTrue();
        assertThat(ready.blockers()).isEmpty();
        assertThat(ready.scenarios()).extracting(CaptureCapabilitiesResponse.Scenario::code, CaptureCapabilitiesResponse.Scenario::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("LOCAL_HOST", "AVAILABLE"),
                        org.assertj.core.groups.Tuple.tuple("SHARED_INTERFACE", "NOT_VERIFIABLE"),
                        org.assertj.core.groups.Tuple.tuple("MONITOR_MODE", "UNAVAILABLE"),
                        org.assertj.core.groups.Tuple.tuple("ROUTER", "NOT_IMPLEMENTED"));
        assertThat(ready.tools()).filteredOn(CaptureCapabilitiesResponse.Tool::available)
                .extracting(CaptureCapabilitiesResponse.Tool::code).containsExactly("NPCAP");
    }

    @Test
    void everyReasonThatBlocksACaptureIsListedAtOnce() {
        CaptureCapabilitiesResponse ready = readiness.evaluate(sensor(false, "2.0.0"),
                Optional.of(caps(false, false, false, 0, MonitorMode.UNKNOWN)), true);

        assertThat(ready.canCapture()).isFalse();
        assertThat(ready.blockers()).hasSize(5)
                .anySatisfy(b -> assertThat(b).contains("no ha reportado"))
                .anySatisfy(b -> assertThat(b).contains("capture-consent on"))
                .anySatisfy(b -> assertThat(b).contains("Npcap"))
                .anySatisfy(b -> assertThat(b).contains("interfaz"))
                .anySatisfy(b -> assertThat(b).contains("en curso"));
        assertThat(ready.scenarios().getFirst().status()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void anAgentOlderThanTwoIsNeverTrustedWithCapabilitiesItCannotHave() {
        CaptureCapabilitiesResponse ready = readiness.evaluate(sensor(true, "1.9.0"),
                Optional.of(caps(true, true, true, 2, MonitorMode.SUPPORTED)), false);

        assertThat(ready.canCapture()).isFalse();
        assertThat(ready.agentSupportsCapture()).isFalse();
        assertThat(ready.consent()).isNull();
        assertThat(ready.tools()).noneMatch(CaptureCapabilitiesResponse.Tool::available);
        assertThat(ready.blockers()).singleElement().asString().contains("1.9.0").contains("2.0.0");
    }

    @Test
    void aNetworkWithoutSensorSaysToInstallOne() {
        SensorStatus none = new SensorStatus(false, false, null, null, null, null, null, null, null);

        CaptureCapabilitiesResponse ready = readiness.evaluate(none, Optional.empty(), false);

        assertThat(ready.sensorInstalled()).isFalse();
        assertThat(ready.blockers()).singleElement().asString().contains("no tiene sensor");
    }

    @Test
    void aSensorThatHasNotReportedItsCapabilitiesYetIsAskedToWait() {
        CaptureCapabilitiesResponse ready = readiness.evaluate(sensor(true, "2.0.0"), Optional.empty(), false);

        assertThat(ready.blockers()).singleElement().asString().contains("próximo reporte");
    }

    @Test
    void monitorModeIsReportedButNeverUsed() {
        CaptureCapabilitiesResponse ready = readiness.evaluate(sensor(true, "2.0.0"),
                Optional.of(caps(true, false, true, 1, MonitorMode.SUPPORTED)), false);

        assertThat(ready.monitorMode()).isEqualTo("SUPPORTED");
        assertThat(ready.scenarios().get(2).status()).isEqualTo("NOT_IMPLEMENTED");
        assertThat(ready.scenarios().get(2).explanation()).contains("no lo usa");
    }

    @Test
    void theLimitsNeverGoAboveWhatTheTableAccepts() {
        CaptureProperties generous = new CaptureProperties(30, 5000, 3600, 1_000_000,
                org.springframework.util.unit.DataSize.ofGigabytes(1), CaptureFixtures.PROPERTIES.startTimeout(),
                CaptureFixtures.PROPERTIES.resultGrace(), CaptureFixtures.PROPERTIES.pollMinInterval(), "2.0.0",
                CaptureFixtures.PROPERTIES.retention());

        CaptureCapabilitiesResponse.Limits limits = new CaptureReadiness(generous).limits();

        assertThat(limits.maxSeconds()).isEqualTo(300);
        assertThat(limits.maxPackets()).isEqualTo(100_000);
        assertThat(limits.maxBytes()).isEqualTo(100L * 1024 * 1024);
    }
}
