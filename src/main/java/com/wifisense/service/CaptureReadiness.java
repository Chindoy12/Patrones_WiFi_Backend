package com.wifisense.service;

import com.wifisense.dto.CaptureCapabilitiesResponse;
import com.wifisense.dto.CaptureCapabilitiesResponse.Limits;
import com.wifisense.dto.CaptureCapabilitiesResponse.Scenario;
import com.wifisense.dto.CaptureCapabilitiesResponse.Tool;
import com.wifisense.dto.SensorStatus;
import com.wifisense.model.AgentCapabilities;
import com.wifisense.model.MonitorMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decides, from the sensor's state and what its computer reported, whether a technical capture is possible and
 * explains each scenario honestly. It never assumes a capability the agent did not report: a sensor older than
 * 2.0.0, or one that never reported its capabilities, cannot capture.
 */
@Component
public class CaptureReadiness {

    private final CaptureProperties properties;

    public CaptureReadiness(CaptureProperties properties) {
        this.properties = properties;
    }

    public CaptureCapabilitiesResponse evaluate(SensorStatus sensor, Optional<AgentCapabilities> reported,
                                                boolean captureActive) {
        boolean supports = AgentVersions.atLeast(sensor.agentVersion(), properties.minAgentVersion());
        AgentCapabilities caps = reported.filter(c -> supports).orElse(null);
        List<String> blockers = new ArrayList<>();
        if (!sensor.installed()) {
            blockers.add("Esta red no tiene sensor: instala el agente WiFiSense en un computador de la red.");
        } else if (!sensor.online()) {
            blockers.add("El sensor no ha reportado en los últimos minutos: revisa que el computador esté encendido "
                    + "y el agente abierto.");
        }
        if (sensor.installed() && !supports) {
            blockers.add("El sensor usa la versión " + (sensor.agentVersion() == null ? "desconocida" : sensor.agentVersion())
                    + " del agente; las capturas necesitan la " + properties.minAgentVersion() + " o una más nueva.");
        } else if (sensor.installed() && caps == null) {
            blockers.add("El sensor todavía no ha informado qué puede hacer su computador: espera su próximo reporte.");
        }
        if (caps != null) {
            if (!caps.isCaptureConsent()) {
                blockers.add("En el computador del sensor no se han permitido las capturas técnicas: quien lo "
                        + "administra debe ejecutar «WiFiSenseAgente.exe capture-consent on».");
            }
            if (!caps.hasCaptureTool()) {
                blockers.add("El computador del sensor no tiene una herramienta de captura que pueda usar: instala "
                        + "Npcap o ejecuta el agente como administrador (para pktmon de Windows).");
            }
            if (caps.getCaptureInterfaces() == 0) {
                blockers.add("El agente no encontró una interfaz de red en la que capturar.");
            }
        }
        if (captureActive) {
            blockers.add("Ya hay una captura en curso en esta red: espera a que termine o detenla.");
        }
        boolean canCapture = blockers.isEmpty();
        return new CaptureCapabilitiesResponse(sensor.installed(), sensor.online(), sensor.lastSeenAt(),
                sensor.agentVersion(), sensor.platform(), supports, caps == null ? null : caps.getReportedAt(),
                caps == null ? null : caps.isElevated(), caps == null ? null : caps.isCaptureConsent(), tools(caps),
                caps == null ? MonitorMode.UNKNOWN.name() : caps.getMonitorMode().name(),
                caps == null ? null : caps.getCaptureInterfaces(), scenarios(caps, canCapture), canCapture,
                List.copyOf(blockers), limits());
    }

    public Limits limits() {
        return new Limits(Math.min(properties.defaultSeconds(), properties.secondsCap()), properties.secondsCap(),
                Math.min(properties.defaultPackets(), properties.packetsCap()), properties.packetsCap(),
                properties.bytesCap());
    }

    private static List<Tool> tools(AgentCapabilities caps) {
        boolean known = caps != null;
        return List.of(
                new Tool("NPCAP", "Npcap", known && caps.isNpcap(),
                        "Controlador de captura que instala el usuario (por ejemplo con Wireshark). WiFiSense no lo "
                                + "instala."),
                new Tool("PKTMON", "pktmon de Windows", known && caps.isPktmon(),
                        "Viene con Windows 10 y 11; necesita que el agente corra como administrador."),
                new Tool("RAW_SOCKET", "Socket sin procesar de Windows", known && caps.isRawSocket(),
                        "Solo paquetes IP del propio computador; necesita administrador."),
                new Tool("PACKET_SOCKET", "Socket de paquetes de Linux", known && caps.isPacketSocket(),
                        "Necesita root o el permiso CAP_NET_RAW."));
    }

    private static List<Scenario> scenarios(AgentCapabilities caps, boolean canCapture) {
        MonitorMode monitor = caps == null ? MonitorMode.UNKNOWN : caps.getMonitorMode();
        return List.of(
                new Scenario("LOCAL_HOST", "A. Tráfico del computador del sensor",
                        canCapture ? "AVAILABLE" : "UNAVAILABLE",
                        "Ve lo que entra y sale del computador del sensor, más la difusión y multidifusión de la red "
                                + "(ARP, DHCP, mDNS...). No ve lo que los demás equipos envían a Internet: en Wi-Fi "
                                + "cada equipo habla con el punto de acceso, no con los demás."),
                new Scenario("SHARED_INTERFACE", "B. Interfaz compartida o puerto espejo", "NOT_VERIFIABLE",
                        "Si el sensor está conectado por cable a un puerto espejo (SPAN) de un switch administrable, "
                                + "la misma captura vería el tráfico copiado a ese puerto. WiFiSense no puede saberlo "
                                + "ni configurarlo: lo hace quien administra el switch."),
                new Scenario("MONITOR_MODE", "C. Tramas inalámbricas en modo monitor",
                        monitor == MonitorMode.SUPPORTED ? "NOT_IMPLEMENTED" : "UNAVAILABLE",
                        monitor == MonitorMode.SUPPORTED
                                ? "El adaptador Wi-Fi declara modo monitor, pero WiFiSense no lo usa: poner el adaptador "
                                + "en ese modo desconecta al sensor de la red y exige controladores especiales."
                                : monitor == MonitorMode.UNSUPPORTED
                                ? "El adaptador Wi-Fi del sensor no admite modo monitor."
                                : "No se sabe si el adaptador admite modo monitor (el agente no pudo leerlo)."),
                new Scenario("ROUTER", "D. Estadísticas del router", "NOT_IMPLEMENTED",
                        "WiFiSense no tiene todavía una integración oficial con el router de esta red; en la sección "
                                + "Router están las instrucciones del fabricante para ver sus clientes."));
    }
}
