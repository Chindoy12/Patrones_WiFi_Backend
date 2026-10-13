package com.wifisense.speedtest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Map;

/**
 * Endpoints the browser uses to measure the connection of an end user: a tiny ping for latency and jitter, a
 * download and an upload for speed. Only end users reach them (/api/my/**), and each transfer counts against the
 * user's budget.
 */
@RestController
@RequestMapping("/api/my/speedtest")
public class SpeedTestController {

    static final int MIN_DOWNLOAD_BYTES = 64 * 1024;
    private static final int BLOCK_SIZE = 64 * 1024;
    /** Random bytes do not compress, so no proxy can make the download look faster than it is. */
    private static final byte[] BLOCK = new byte[BLOCK_SIZE];

    static {
        new SecureRandom().nextBytes(BLOCK);
    }

    private final SpeedTestProperties properties;
    private final SpeedTestThrottle throttle;

    public SpeedTestController(SpeedTestProperties properties, SpeedTestThrottle throttle) {
        this.properties = properties;
        this.throttle = throttle;
    }

    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download(@RequestParam(defaultValue = "1048576") long bytes,
                                                          Authentication authentication) {
        throttle.acquireTransfer(authentication.getName());
        long size = Math.max(MIN_DOWNLOAD_BYTES, Math.min(bytes, properties.maxDownloadBytes()));
        StreamingResponseBody body = out -> {
            long left = size;
            while (left > 0) {
                int chunk = (int) Math.min(BLOCK_SIZE, left);
                out.write(BLOCK, 0, chunk);
                left -= chunk;
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(size)
                .cacheControl(CacheControl.noStore().noTransform())
                .body(body);
    }

    @PostMapping(path = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Map<String, Long> upload(HttpServletRequest request, Authentication authentication) throws IOException {
        throttle.acquireTransfer(authentication.getName());
        // RequestSizeLimitFilter stops the body at the configured maximum; here it is only read and discarded.
        long received = 0;
        byte[] buffer = new byte[BLOCK_SIZE];
        try (InputStream in = request.getInputStream()) {
            for (int read; (read = in.read(buffer)) != -1; ) {
                received += read;
            }
        }
        return Map.of("receivedBytes", received, "maxBytes", properties.maxUploadBytes());
    }
}
