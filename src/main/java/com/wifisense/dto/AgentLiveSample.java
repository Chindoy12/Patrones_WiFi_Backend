package com.wifisense.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * One live sample of the agent (2.1.0), taken every 2 seconds while someone has «En vivo» open: what the computer is
 * downloading and uploading at that moment (bits per second, from the counters of its network card), the latency to
 * the router and to internet, and its Wi-Fi link. Only numbers of the computer itself: no address, no site, no
 * content. Everything is optional (the agent sends what it could read) and anything out of range is rejected.
 * {@code internetLost} means internet did not answer (a lost sample); {@code gatewayLost} the same for the router.
 */
public record AgentLiveSample(
        @Min(0) @Max(60_000) Integer intervalMs,
        @Min(0) @Max(100_000_000_000L) Long downloadBps,
        @Min(0) @Max(100_000_000_000L) Long uploadBps,
        @DecimalMin("0") @DecimalMax("10000") Double internetMs,
        Boolean internetLost,
        @DecimalMin("0") @DecimalMax("10000") Double gatewayMs,
        Boolean gatewayLost,
        @Min(0) @Max(100) Integer signalPercent,
        @DecimalMin("0") @DecimalMax("100000") Double rxRateMbps,
        @DecimalMin("0") @DecimalMax("100000") Double txRateMbps) {
}
