package com.wifisense.service;

import com.wifisense.dto.AuditEventResponse;
import com.wifisense.dto.AuditPage;
import com.wifisense.dto.RetentionPolicyResponse;
import com.wifisense.model.AuditAction;
import com.wifisense.model.AuditOutcome;
import com.wifisense.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** What the administrators read about the audit and the retention rules. */
@Service
public class AuditQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository events;
    private final AuditProperties audit;
    private final CaptureProperties captures;
    private final WifiScanProperties scans;
    private final AgentProperties agents;
    private final RetentionProperties monitoring;

    public AuditQueryService(AuditEventRepository events, AuditProperties audit, CaptureProperties captures,
                             WifiScanProperties scans, AgentProperties agents, RetentionProperties monitoring) {
        this.events = events;
        this.audit = audit;
        this.captures = captures;
        this.scans = scans;
        this.agents = agents;
        this.monitoring = monitoring;
    }

    /** Newest first; an unknown action or outcome is a 400, not an empty list. */
    @Transactional(readOnly = true)
    public AuditPage search(String action, String outcome, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Page<AuditEventResponse> found = events.search(parse(AuditAction.class, action),
                        parse(AuditOutcome.class, outcome), PageRequest.of(safePage, safeSize))
                .map(AuditEventResponse::from);
        return new AuditPage(found.getContent(), safePage, safeSize, found.getTotalElements());
    }

    /** The actions the filter offers, with their labels. */
    public List<Map<String, String>> actions() {
        return Arrays.stream(AuditAction.values())
                .map(a -> Map.of("code", a.name(), "label", a.label())).toList();
    }

    public List<RetentionPolicyResponse> policies() {
        return List.of(
                new RetentionPolicyResponse("CAPTURE_FILES", "Archivos de captura", 0L,
                        "El archivo con los paquetes capturados, en el computador del sensor.",
                        "Fijo en el agente",
                        "El agente lo borra apenas termina de contar; si se cerró a mitad, borra los restos de más "
                                + "de una hora al iniciar. Nunca sale del computador."),
                new RetentionPolicyResponse("CAPTURES", "Capturas técnicas", captures.retention().toDays(),
                        "Estadísticas y hallazgos de cada captura (sin direcciones ni contenido).",
                        "CAPTURE_RETENTION", "Se borran cada noche."),
                new RetentionPolicyResponse("SCANS", "Escaneos de redes cercanas", scans.retention().toDays(),
                        "Cada escaneo y lo que vio.", "wifisense.scan.retention",
                        "Se borran cada noche; los puntos de acceso que no se ven hace "
                                + scans.accessPointRetention().toDays() + " días también."),
                new RetentionPolicyResponse("AGENT_REPORTS", "Reportes de los agentes", agents.retention().toDays(),
                        "Reportes y equipos de Mi Wi-Fi y de los sensores.", "wifisense.agent.retention",
                        "Se borran cada noche."),
                new RetentionPolicyResponse("MONITORING", "Monitoreo de las redes del equipo",
                        monitoring.monitoring().toDays(), "Mediciones, tráfico y análisis.", "MONITORING_RETENTION",
                        "Se borran cada noche, por partes."),
                new RetentionPolicyResponse("AUDIT", "Auditoría", audit.effectiveRetention().toDays(),
                        "Quién hizo cada operación sensible y con qué resultado.", "AUDIT_RETENTION",
                        "Se borra cada noche; nunca antes de 31 días y nadie puede modificarla."));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Filtro desconocido: " + value);
        }
    }
}
