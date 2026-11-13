package com.wifisense.controller;

import com.wifisense.dto.LiveView;
import com.wifisense.service.LiveService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * «En vivo» for the page (Avance 26). The page asks every 2 seconds while it is open, with the number of the last
 * sample it has ({@code after}); each request also keeps the measurement going. When the page closes or pauses, the
 * agent stops by itself within 30 seconds.
 */
@RestController
public class LiveController {

    private final LiveService live;

    public LiveController(LiveService live) {
        this.live = live;
    }

    /** A network of Mi Wi-Fi, for its owner. */
    @GetMapping("/api/my/networks/{networkId}/live")
    public ResponseEntity<LiveView> home(@PathVariable Long networkId, @RequestParam(defaultValue = "0") long after,
                                         Authentication authentication) {
        return fresh(live.watchHome(authentication.getName(), networkId, after));
    }

    /** A team network with its sensor, for the team. */
    @GetMapping("/api/networks/{networkId}/sensor/live")
    public ResponseEntity<LiveView> team(@PathVariable Long networkId, @RequestParam(defaultValue = "0") long after) {
        return fresh(live.watchTeam(networkId, after));
    }

    private static ResponseEntity<LiveView> fresh(LiveView view) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view);
    }
}
