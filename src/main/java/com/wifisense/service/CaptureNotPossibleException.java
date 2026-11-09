package com.wifisense.service;

import java.util.List;

/**
 * A capture cannot be asked for now, with every reason (no sensor, sensor offline, no consent on its computer, no
 * capture tool, a capture already running...). The page shows them all, so the administrator fixes them at once.
 */
public class CaptureNotPossibleException extends RuntimeException {

    private final List<String> reasons;

    public CaptureNotPossibleException(List<String> reasons) {
        super(reasons.isEmpty() ? "No se puede capturar ahora." : reasons.getFirst());
        this.reasons = List.copyOf(reasons);
    }

    public List<String> getReasons() {
        return reasons;
    }
}
