package com.wifisense.controller;

import com.wifisense.dto.AgentLiveAck;
import com.wifisense.dto.AgentLiveSample;
import com.wifisense.dto.AgentLiveTask;
import com.wifisense.service.LiveService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The agent's side of «En vivo» (agent 2.1.0). Like the rest of {@code /api/agent}, it has no user session: the
 * {@code X-Agent-Key} header decides the network.
 */
@RestController
@RequestMapping("/api/agent/live")
public class AgentLiveController {

    private final LiveService live;

    public AgentLiveController(LiveService live) {
        this.live = live;
    }

    /** Between reports the agent asks every 30 seconds whether someone opened «En vivo» (204: nobody). */
    @GetMapping("/next")
    public ResponseEntity<AgentLiveTask> next(@RequestHeader(value = AgentController.KEY_HEADER, required = false)
                                              String key) {
        return live.next(key).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** One sample every 2 seconds; the answer says whether someone is still watching. */
    @PostMapping("/samples")
    public AgentLiveAck sample(@RequestHeader(value = AgentController.KEY_HEADER, required = false) String key,
                               @Valid @RequestBody AgentLiveSample sample) {
        return live.receive(key, sample);
    }
}
