package com.wifisense.dto;

import java.util.List;

/** A page of the audit, newest first. */
public record AuditPage(List<AuditEventResponse> items, int page, int size, long total) {
}
