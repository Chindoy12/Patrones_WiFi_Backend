package com.wifisense.controller;

import com.wifisense.dto.AuditPage;
import com.wifisense.dto.RetentionPolicyResponse;
import com.wifisense.service.AuditQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The audit and the retention rules, for administrators only (/api/admin/** in SecurityConfig). Read-only. */
@RestController
@RequestMapping("/api/admin")
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    /** Newest first, optionally only one action and/or outcome (default 50 per page, at most 100). */
    @GetMapping("/audit")
    public AuditPage audit(@RequestParam(required = false) String action,
                           @RequestParam(required = false) String outcome,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "50") int size) {
        return audit.search(action, outcome, page, size);
    }

    @GetMapping("/audit/actions")
    public List<Map<String, String>> actions() {
        return audit.actions();
    }

    /** How long each kind of data is kept and where that is configured. */
    @GetMapping("/policies")
    public List<RetentionPolicyResponse> policies() {
        return audit.policies();
    }
}
