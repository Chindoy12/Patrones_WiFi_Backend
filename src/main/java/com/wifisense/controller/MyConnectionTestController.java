package com.wifisense.controller;

import com.wifisense.dto.ConnectionTestRequest;
import com.wifisense.dto.ConnectionTestResponse;
import com.wifisense.service.ConnectionTestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Connection tests of the signed-in end user; the owner always comes from the token. */
@RestController
@RequestMapping("/api/my/networks/{networkId}/tests")
public class MyConnectionTestController {

    private final ConnectionTestService service;

    public MyConnectionTestController(ConnectionTestService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConnectionTestResponse> history(@PathVariable Long networkId,
                                                @RequestParam(defaultValue = "10") int limit,
                                                Authentication authentication) {
        return service.history(authentication.getName(), networkId, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionTestResponse save(@PathVariable Long networkId, @Valid @RequestBody ConnectionTestRequest request,
                                       Authentication authentication) {
        return service.save(authentication.getName(), networkId, request);
    }
}
