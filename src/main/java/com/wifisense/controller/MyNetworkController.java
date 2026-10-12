package com.wifisense.controller;

import com.wifisense.dto.HomeNetworkRequest;
import com.wifisense.dto.HomeNetworkResponse;
import com.wifisense.service.HomeNetworkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** End-user area: the owner always comes from the token, never from the request. */
@RestController
@RequestMapping("/api/my/networks")
public class MyNetworkController {

    private final HomeNetworkService service;

    public MyNetworkController(HomeNetworkService service) {
        this.service = service;
    }

    @GetMapping
    public List<HomeNetworkResponse> list(Authentication authentication) {
        return service.list(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HomeNetworkResponse create(@Valid @RequestBody HomeNetworkRequest request, Authentication authentication) {
        return service.create(authentication.getName(), request);
    }

    @PutMapping("/{id}")
    public HomeNetworkResponse update(@PathVariable Long id, @Valid @RequestBody HomeNetworkRequest request,
                                      Authentication authentication) {
        return service.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(authentication.getName(), id);
    }
}
